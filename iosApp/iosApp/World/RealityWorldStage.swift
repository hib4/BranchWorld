import Combine
import Metal
import RealityKit
import Shared
import UIKit

/// The iOS 3D view: RealityKit, driven entirely by shared Kotlin through `WorldStage`. Kotlin builds every mesh,
/// chooses every material and moves every entity each frame; this class only turns that data into RealityKit entities.
/// Shading lives in WorldShaders.metal.
@MainActor
final class RealityWorldStage: NSObject, @preconcurrency WorldStage {
    private let arView = ARView(frame: .zero, cameraMode: .nonAR, automaticallyConfigureSession: false)
    private let anchor = AnchorEntity(world: .zero)
    private let camera = PerspectiveCamera()
    private let library: MTLLibrary? = MTLCreateSystemDefaultDevice()?.makeDefaultLibrary()

    private var meshes: [Int32: MeshResource] = [:]
    private var meshUsers: [Int32: [ModelEntity]] = [:]
    private var materials: [Int32: RealityKit.Material] = [:]
    private var materialUsers: [Int32: [ModelEntity]] = [:]
    private var textures: [Int32: TextureResource] = [:]
    private var entities: [Int32: Entity] = [:]
    /// Models whose mesh is currently empty: they stay hidden whatever their transform says.
    private var blank: Set<ObjectIdentifier> = []
    private var lighting: TextureResource?
    private var frames: StageFrames?
    private var updates: Cancellable?

    override init() {
        super.init()
        arView.renderOptions = [
            .disableMotionBlur, .disableDepthOfField, .disableCameraGrain, .disableHDR, .disableGroundingShadows,
            .disableAREnvironmentLighting, .disablePersonOcclusion, .disableFaceMesh,
        ]
        arView.environment.background = .color(UIColor(red: 0.18, green: 0.41, blue: 0.47, alpha: 1))
        arView.isMultipleTouchEnabled = false
        arView.isUserInteractionEnabled = false
        camera.camera.near = 0.1
        camera.camera.far = 90
        camera.camera.fieldOfViewInDegrees = 47
        if #available(iOS 27.0, *) {
            // The shaders already produce display-ready colours, as Kool's do; RealityKit's filmic curve would darken
            // the sea and shadows, so it is flattened.
            camera.components.set(ToneMappingComponent(exposure: 0, toeStrength: 0, toeLength: 0, shoulderStrength: 0,
                                                        shoulderLength: 0, shoulderAngle: 0))
        }
        anchor.addChild(camera)
        arView.scene.addAnchor(anchor)
        if let blank = Self.lightingImage(Data(count: 32)) {
            lighting = try? TextureResource(image: blank, options: .init(semantic: .raw))
        }
    }

    func makeView() -> UIView { arView }

    func clearScene() {
        for child in Array(anchor.children) where child !== camera { child.removeFromParent() }
        meshes.removeAll()
        meshUsers.removeAll()
        materialUsers.removeAll()
        blank.removeAll()
        materials.removeAll()
        textures.removeAll()
        entities.removeAll()
    }

    func defineMesh(mesh: Int32, positions: Data, normals: Data, colors: Data, texCoords: Data, indices: Data) {
        guard let resource = Self.makeMesh(positions: positions, normals: normals, colors: colors, texCoords: texCoords, indices: indices) else {
            // An empty mesh (no plants yet, no burrows): keep the entities but draw nothing.
            meshes[mesh] = nil
            for user in meshUsers[mesh] ?? [] {
                user.isEnabled = false
                blank.insert(ObjectIdentifier(user))
            }
            return
        }
        meshes[mesh] = resource
        for user in meshUsers[mesh] ?? [] {
            user.model?.mesh = resource
            blank.remove(ObjectIdentifier(user))
            user.isEnabled = true
        }
    }

    func defineTexture(texture: Int32, image: Data) {
        guard let picture = UIImage(data: image)?.cgImage else { return }
        textures[texture] = try? TextureResource(image: picture, options: .init(semantic: .color))
    }

    func defineMaterial(material: Int32, shading: Int32, texture: Int32, r: Float, g: Float, b: Float, a: Float, variant: Int32) {
        guard let library else { return }
        let (surfaceName, modifierName): (String, String?) = switch shading {
        case 0: ("landSurface", nil)
        case 1: ("seaSurface", nil)
        case 2: ("modelSurface", nil)
        case 3: ("foliageSurface", "foliageSway")
        case 5: ("shadeSurface", nil)
        case 6: ("meadowSurface", "meadowSway")
        case 7: ("riverSurface", "riverLevel")
        case 8: ("scarSurface", nil)
        case 9: ("flameSurface", "flameLick")
        case 10: ("smokeSurface", "smokeRise")
        default: ("plainSurface", nil)
        }
        let surface = CustomMaterial.SurfaceShader(named: surfaceName, in: library)
        let created: CustomMaterial?
        if let modifierName {
            created = try? CustomMaterial(surfaceShader: surface,
                                          geometryModifier: CustomMaterial.GeometryModifier(named: modifierName, in: library),
                                          lightingModel: .unlit)
        } else {
            created = try? CustomMaterial(surfaceShader: surface, lightingModel: .unlit)
        }
        guard var made = created else { return }
        made.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: CGFloat(a)),
                               texture: textures[texture].map { .init($0) })
        if let lighting { made.custom.texture = .init(lighting) }
        made.custom.value = SIMD4<Float>(Float(variant), 0, 0, 0)
        switch shading {
        case 4, 6: made.faceCulling = .none
        case 5:
            made.blending = .transparent(opacity: .init(floatLiteral: 1))
            made.faceCulling = .none
        case 7:
            // The river is cut away where it sinks below its bed or the sea.
            made.opacityThreshold = 0.5
        case 8, 9, 10:
            made.blending = .transparent(opacity: .init(floatLiteral: 1))
            made.faceCulling = .none
        default: break
        }
        materials[material] = made
        for user in materialUsers[material] ?? [] { user.model?.materials = [made] }
    }

    func addEntity(entity: Int32, parent: Int32, mesh: Int32, material: Int32) {
        let created: Entity
        if mesh >= 0 {
            let model = ModelEntity()
            model.model = ModelComponent(mesh: meshes[mesh] ?? MeshResource.generateBox(size: 0.001),
                                         materials: materials[material].map { [$0] } ?? [])
            model.isEnabled = meshes[mesh] != nil
            if meshes[mesh] == nil { blank.insert(ObjectIdentifier(model)) }
            meshUsers[mesh, default: []].append(model)
            materialUsers[material, default: []].append(model)
            created = model
        } else {
            created = Entity()
        }
        entities[entity] = created
        (entities[parent] ?? anchor).addChild(created)
    }

    func removeEntity(entity: Int32) {
        entities.removeValue(forKey: entity)?.removeFromParent()
    }

    func setTransforms(records: Data) {
        records.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            let count = raw.count / 48
            for index in 0..<count {
                let base = index * 48
                let id = raw.loadUnaligned(fromByteOffset: base, as: Int32.self)
                guard let entity = entities[id] else { continue }
                func float(_ slot: Int) -> Float { raw.loadUnaligned(fromByteOffset: base + 4 + slot * 4, as: Float.self) }
                let visible = float(10) > 0.5 && !blank.contains(ObjectIdentifier(entity))
                if entity.isEnabled != visible { entity.isEnabled = visible }
                guard visible else { continue }
                entity.transform = Transform(
                    scale: SIMD3(float(7), float(8), float(9)),
                    rotation: simd_quatf(ix: float(3), iy: float(4), iz: float(5), r: float(6)),
                    translation: SIMD3(float(0), float(1), float(2)))
            }
        }
    }

    func setCamera(eyeX: Float, eyeY: Float, eyeZ: Float, targetX: Float, targetY: Float, targetZ: Float, fovDegrees: Float) {
        camera.camera.fieldOfViewInDegrees = fovDegrees
        camera.look(at: SIMD3(targetX, targetY, targetZ), from: SIMD3(eyeX, eyeY, eyeZ), relativeTo: nil)
    }

    func setLighting(texels: Data, backgroundR: Float, backgroundG: Float, backgroundB: Float) {
        arView.environment.background = .color(UIColor(red: CGFloat(backgroundR), green: CGFloat(backgroundG), blue: CGFloat(backgroundB), alpha: 1))
        guard let image = Self.lightingImage(texels) else { return }
        try? lighting?.replace(withImage: image, options: .init(semantic: .raw))
    }

    func startFrames(frames: StageFrames) {
        self.frames = frames
        // RealityKit's per-frame update runs on the main thread just before each frame is drawn.
        updates = arView.scene.subscribe(to: SceneEvents.Update.self) { [weak self] _ in
            self?.frames?.frame(nanos: Int64(DispatchTime.now().uptimeNanoseconds))
        }
    }

    func snapshot(done: @escaping (Data?) -> Void) {
        arView.snapshot(saveToHDR: false) { image in done(image?.pngData()) }
    }

    /// The eight lighting texels as an 8×1 RGBA image.
    private static func lightingImage(_ texels: Data) -> CGImage? {
        var bytes = [UInt8](texels)
        if bytes.count < 32 { bytes += [UInt8](repeating: 0, count: 32 - bytes.count) }
        guard let provider = CGDataProvider(data: Data(bytes.prefix(32)) as CFData) else { return nil }
        return CGImage(width: 8, height: 1, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: 32,
                       space: CGColorSpaceCreateDeviceRGB(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }

    /// A mesh from Kotlin's arrays: positions, normals, optional rgba colours and uvs, and triangle indices.
    private static func makeMesh(positions: Data, normals: Data, colors: Data, texCoords: Data, indices: Data) -> MeshResource? {
        let vertexCount = positions.count / 12
        let indexCount = indices.count / 4
        guard vertexCount > 0, indexCount > 0 else { return nil }
        let descriptor = LowLevelMesh.Descriptor(
            vertexCapacity: vertexCount,
            vertexAttributes: [
                .init(semantic: .position, format: .float3, layoutIndex: 0, offset: 0),
                .init(semantic: .normal, format: .float3, layoutIndex: 1, offset: 0),
                .init(semantic: .color, format: .float4, layoutIndex: 2, offset: 0),
                .init(semantic: .uv0, format: .float2, layoutIndex: 3, offset: 0),
            ],
            vertexLayouts: [
                .init(bufferIndex: 0, bufferStride: 12),
                .init(bufferIndex: 1, bufferStride: 12),
                .init(bufferIndex: 2, bufferStride: 16),
                .init(bufferIndex: 3, bufferStride: 8),
            ],
            indexCapacity: indexCount,
            indexType: .uint32)
        guard let mesh = try? LowLevelMesh(descriptor: descriptor) else { return nil }
        mesh.withUnsafeMutableBytes(bufferIndex: 0) { target in positions.copyBytes(to: target.bindMemory(to: UInt8.self)) }
        mesh.withUnsafeMutableBytes(bufferIndex: 1) { target in
            if normals.count >= vertexCount * 12 { normals.copyBytes(to: target.bindMemory(to: UInt8.self)) }
        }
        mesh.withUnsafeMutableBytes(bufferIndex: 2) { target in
            if colors.count >= vertexCount * 16 {
                colors.copyBytes(to: target.bindMemory(to: UInt8.self))
            } else {
                let white = target.bindMemory(to: Float.self)
                for i in 0..<white.count { white[i] = 1 }
            }
        }
        mesh.withUnsafeMutableBytes(bufferIndex: 3) { target in
            if texCoords.count >= vertexCount * 8 {
                texCoords.copyBytes(to: target.bindMemory(to: UInt8.self))
            } else {
                let zero = target.bindMemory(to: UInt8.self)
                for i in 0..<zero.count { zero[i] = 0 }
            }
        }
        mesh.withUnsafeMutableIndices { target in indices.copyBytes(to: target.bindMemory(to: UInt8.self)) }
        var low = SIMD3<Float>(repeating: .greatestFiniteMagnitude)
        var high = SIMD3<Float>(repeating: -.greatestFiniteMagnitude)
        positions.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            for v in 0..<vertexCount {
                let point = SIMD3(raw.loadUnaligned(fromByteOffset: v * 12, as: Float.self),
                                  raw.loadUnaligned(fromByteOffset: v * 12 + 4, as: Float.self),
                                  raw.loadUnaligned(fromByteOffset: v * 12 + 8, as: Float.self))
                low = simd_min(low, point)
                high = simd_max(high, point)
            }
        }
        // Swaying crowns move a little beyond their rest bounds.
        let bounds = BoundingBox(min: low - 0.1, max: high + 0.1)
        mesh.parts.replaceAll([LowLevelMesh.Part(indexCount: indexCount, topology: .triangle, bounds: bounds)])
        return try? MeshResource(from: mesh)
    }
}
