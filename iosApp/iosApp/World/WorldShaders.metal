// BranchWorld's island shading for RealityKit on iOS, ported from the Kool renderer's shaders (renderer/koolMain) so
// the world looks the same on every platform. Every material is unlit: these functions do the lighting themselves,
// reading sun, fill, haze and wind from an 8×1 lighting texture that Kotlin updates when the weather changes
// (renderer/iosMain stage/StageData.kt lightingTexels).

#include <metal_stdlib>
#include <RealityKit/RealityKit.h>

using namespace metal;

namespace {

struct Light {
    float3 sunDirection;
    float strength;
    float3 sunColor;
    float fill;
    float3 fillColor;
    float recharge;    // the watershed's fill, 0..1: rivers rise after rain
    float4 mist;
    float3 fillDirection;
    float meadowWind;
    float3 water;      // sun, twilight, rain
    float canopyWind;
    float3 haze;
    float motion;      // 0 while the player asks for reduced motion
    float3 meadowTint;
};

float4 texel(texture2d<half> lighting, uint index) {
    return float4(lighting.read(uint2(index, 0)));
}

Light readLight(texture2d<half> lighting) {
    Light light;
    float4 a = texel(lighting, 0);
    light.sunDirection = normalize(a.xyz * 2.0 - 1.0);
    light.strength = a.w * 2.0;
    float4 b = texel(lighting, 1);
    light.sunColor = b.xyz;
    light.fill = b.w;
    float4 c = texel(lighting, 2);
    light.fillColor = c.xyz;
    light.recharge = c.w;
    light.mist = texel(lighting, 3);
    float4 e = texel(lighting, 4);
    light.fillDirection = normalize(e.xyz * 2.0 - 1.0);
    light.meadowWind = e.w / 10.0;
    float4 f = texel(lighting, 5);
    light.water = f.xyz;
    light.canopyWind = f.w / 10.0;
    float4 g = texel(lighting, 6);
    light.haze = g.xyz;
    light.motion = g.w;
    light.meadowTint = texel(lighting, 7).xyz * 1.1;
    return light;
}

// Kool's lit shaders: a dim uniform ambient (0.2 in sRGB), the sun and the fill as diffuse lights, and a Blinn-Phong
// highlight from the sun. The result is linear; RealityKit converts it for the display as Kool does. [diffuse] matches
// each Kool shader's brightness, measured against the web build on the same island and camera: Blinn-Phong surfaces
// (land, markers, plants) BlinnDiffuse, physically based models (pines, animals) PbrDiffuse.
constant float BlinnDiffuse = 0.82;
constant float PbrDiffuse = 0.47;

float3 shade(float3 base, float3 normal, float3 toEye, Light light, float specular, float diffuse) {
    float3 color = base * 0.0331;
    float3 toSun = -light.sunDirection;
    color += base * light.sunColor * light.strength * diffuse * max(dot(normal, toSun), 0.0);
    color += base * light.fillColor * light.fill * diffuse * max(dot(normal, -light.fillDirection), 0.0);
    if (specular > 0.0) {
        float3 half_vector = normalize(toSun + toEye);
        color += light.sunColor * light.strength * specular * pow(max(dot(normal, half_vector), 0.0), 16.0);
    }
    return color;
}

float viewDistance(realitykit::surface_parameters params) {
    float3 world = params.geometry().world_position();
    return length((params.uniforms().world_to_view() * float4(world, 1.0)).xyz);
}

// The land haze: distant ground and trees fade toward the sky's colour (Kool coastalHaze).
float3 hazed(float3 base, float distance, Light light) {
    return mix(base, light.mist.rgb, smoothstep(5.2, 14.0, distance) * light.mist.a);
}

// RealityKit works in Display P3; Kool's colours, like the web's, use sRGB primaries. Converting keeps a cyan sea cyan
// rather than letting it read as a more saturated P3 colour.
constant float3x3 SrgbToDisplayP3 = float3x3(
    float3(0.8225, 0.0332, 0.0171),
    float3(0.1774, 0.9669, 0.0724),
    float3(0.0000, 0.0000, 0.9108));

void emit(realitykit::surface_parameters params, float3 color, float opacity) {
    params.surface().set_base_color(half3(0.0));
    params.surface().set_emissive_color(half3(max(SrgbToDisplayP3 * max(color, 0.0), 0.0)));
    params.surface().set_opacity(half(opacity));
}

float3 linearFromDisplay(float3 c) {
    return pow(max(c, 0.0), 2.2);
}

// The Cube Pets coats (Kool wildlifeCoat), painted over the shared colour map.
float3 coat(float3 source, int variant) {
    if (variant == 1) {
        float light = dot(source, float3(0.35, 0.50, 0.15));
        return mix(source, float3(light * 1.31, light * 1.24, light * 1.14), 0.63);
    }
    if (variant == 2) {
        float light = dot(source, float3(0.30, 0.55, 0.15));
        return mix(source, float3(light * 0.84, light * 0.88, light * 0.95), 0.80);
    }
    if (variant == 3) return source * float3(1.16, 0.91, 0.83);
    return source;
}

} // namespace

/// Terrain: vertex colours, sun and fill, a faint highlight and the land haze.
[[visible]]
void landSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    float3 base = params.geometry().color().rgb * float3(params.material_constants().base_color_tint());
    base = hazed(base, viewDistance(params), light);
    float3 normal = normalize(params.geometry().normal());
    emit(params, shade(base, normal, normalize(params.geometry().view_direction()), light, 0.035, BlinnDiffuse), 1.0);
}

/// Small shapes (markers, plants, burrows): lit vertex colours without haze.
[[visible]]
void plainSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    float4 paint = params.geometry().color();
    float3 normal = normalize(params.geometry().normal());
    emit(params, shade(paint.rgb, normal, normalize(params.geometry().view_direction()), light, 0.08, BlinnDiffuse), paint.a);
}

/// glTF models: the base colour texture (or white) times the material colour, the animal's coat, light and haze.
[[visible]]
void modelSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    constexpr sampler textureSampler(coord::normalized, address::repeat, filter::linear, mip_filter::linear);
    float2 uv = params.geometry().uv0();
    float3 base = float3(params.textures().base_color().sample(textureSampler, uv).rgb);
    base *= float3(params.material_constants().base_color_tint());
    base = coat(base, int(params.uniforms().custom_parameter().x + 0.5));
    base = hazed(base, viewDistance(params), light);
    float3 normal = normalize(params.geometry().normal());
    emit(params, shade(base, normal, normalize(params.geometry().view_direction()), light, 0.0, PbrDiffuse), 1.0);
}

/// Baked pines: vertex colours, light and haze; the geometry modifier sways their crowns.
[[visible]]
void foliageSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    float3 base = hazed(params.geometry().color().rgb, viewDistance(params), light);
    float3 normal = normalize(params.geometry().normal());
    emit(params, shade(base, normal, normalize(params.geometry().view_direction()), light, 0.0, PbrDiffuse), 1.0);
}

/// The pines' crowns sway in the wind; vertex alpha is how far up the crown a vertex sits (Kool pineSway).
[[visible]]
void foliageSway(realitykit::geometry_parameters params) {
    Light light = readLight(params.textures().custom());
    float3 world = params.geometry().world_position();
    float crown = params.geometry().color().a;
    float time = params.uniforms().time() * light.motion;
    float shift = sin(world.x * 0.92 + world.z * 0.75 + time * 1.53) * crown * light.canopyWind;
    params.geometry().set_world_position_offset(float3(shift, 0.0, shift * 0.38));
}

/// The sea (Kool CoastalWater): shallows to deep water, swell, ripples, foam at the shore, the dusk sun path, rain rings
/// and the haze toward the horizon. Kool writes these colours straight to the display, so they are linearised here.
[[visible]]
void seaSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    float3 p = params.geometry().world_position();
    float4 ground = params.geometry().color();
    float shore = ground.a;
    float sun = light.water.x;
    float dusk = light.water.y;
    float overcast = light.water.z;
    float clock = params.uniforms().time() * light.motion;
    float distance = viewDistance(params);
    float line = sin(p.z * 18.0 + p.x * 8.3 + clock * 1.55 + sin(p.x * 5.7 - p.z * 2.1 + clock * 0.41) * 1.29);
    float crossWave = sin(p.x * 12.1 - p.z * 8.7 - clock * 0.89);
    float detail = 1.0 - smoothstep(7.0, 20.0, distance);
    float swell = sin(p.x * 2.6 + p.z * 3.4 + clock * 0.89);
    float brush = swell * 0.011 + (line + crossWave) * 0.008 * detail;
    float3 lowLight = ground.rgb * float3(0.52, 0.60, 0.78);
    float3 water = mix(lowLight, ground.rgb, sun);
    float3 color = mix(water, water * float3(0.78, 0.87, 0.96), overcast);
    float ripple = smoothstep(0.53, 0.96, line) * smoothstep(-0.16, 0.88, crossWave);
    float foam = smoothstep(0.66, 0.84, shore) * (1.0 - smoothstep(0.95, 1.0, shore));
    float wash = sin(shore * 38.0 - clock * 1.72 + swell * 0.89);
    float reflection = (ripple * 0.031 + smoothstep(0.44, 0.98, wash) * foam * (0.065 + crossWave * 0.026)) * detail;
    color += float3(0.64, 0.89, 0.85) * (brush + reflection);
    float horizon = smoothstep(4.8, 16.0, distance);
    float3 eye = -normalize(params.geometry().view_direction());
    float sunPath = smoothstep(0.51, 0.91, dot(eye, float3(-0.64, -0.24, -0.71)));
    float duskReflection = (0.030 + ripple * 0.086) * dusk * sunPath * detail * (1.0 - overcast);
    color += float3(0.97, 0.50, 0.28) * duskReflection;
    float2 rainGrid = p.xz * 4.7;
    float2 tile = floor(rainGrid);
    float phase = fract(sin(dot(tile, float2(43.12, 75.54))) * 4376.19 + clock * 1.62);
    float drift = sin(dot(tile, float2(27.89, 18.44))) * 0.13;
    float ring = abs(length(fract(rainGrid) - float2(0.5 + drift, 0.5 - drift)) - phase * 0.39);
    float splash = (1.0 - smoothstep(0.009, 0.032, ring)) * (1.0 - phase);
    color += float3(0.55, 0.74, 0.78) * (splash * overcast * detail * 0.13);
    color = mix(color, light.haze, horizon * (0.42 + 0.24 * overcast));
    emit(params, linearFromDisplay(color), 1.0);
}

/// Soft shade under pines and stones: unlit vertex colour with alpha (Kool translucentSurface).
[[visible]]
void shadeSurface(realitykit::surface_parameters params) {
    float4 paint = params.geometry().color();
    emit(params, paint.rgb, paint.a);
}

/// Grass blades and reeds lean with the wind; vertex alpha is how far a point bends (Kool MeadowWind).
[[visible]]
void meadowSway(realitykit::geometry_parameters params) {
    Light light = readLight(params.textures().custom());
    float3 p = params.geometry().world_position();
    float time = params.uniforms().time() * light.motion;
    float bend = params.geometry().color().a * light.meadowWind * sin(p.x * 2.96 + p.z * 3.81 + time * 1.5);
    params.geometry().set_world_position_offset(float3(bend, 0.0, bend * 0.54));
}

/// Blade colours under the meadow's light tint. Kool writes these straight to the display, so they are linearised.
[[visible]]
void meadowSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    emit(params, linearFromDisplay(params.geometry().color().rgb * light.meadowTint), 1.0);
}

/// Rivers and lakes rise with the watershed's recharge (Kool InlandWater vertex stage).
[[visible]]
void riverLevel(realitykit::geometry_parameters params) {
    Light light = readLight(params.textures().custom());
    float y = params.geometry().model_position().y;
    float rise = (light.recharge - 0.40) * 0.12 * smoothstep(0.025, 0.19, y);
    params.geometry().set_model_position_offset(float3(0.0, rise, 0.0));
}

/// Rivers, lakes and marshes (Kool InlandWater): shallows to deep water by kind, current and glints along the flow, wet
/// edges, the dusk sheen and the estuary blending into the sea's colours. Where the surface sinks below its bed or the
/// sea, it is cut away. Vertex colour packs flow (rg), kind (b) and bed height (a).
[[visible]]
void riverSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    float3 p = params.geometry().world_position();
    float4 info = params.geometry().color();
    float bed = info.a * 2.5 - 1.0;
    float depth = p.y - bed;
    if (depth < 0.004 || p.y < 0.016) {
        emit(params, float3(0.0), 0.0);
        return;
    }
    float clock = params.uniforms().time() * light.motion;
    float3 air = light.water;
    float kind = info.b;
    float2 flow = info.rg * 0.55 + float2(0.13, 0.71);
    float flowRate = 1.35 - smoothstep(0.10, 0.72, kind);
    float longWave = sin(dot(p.xz, flow) * 15.0 - clock * flowRate * 2.6 + sin(p.x * 7.9 + p.z * 5.8 - clock * 0.77) * 1.58);
    float crossWave = sin(p.x * 26.0 - p.z * 19.0 + clock * 1.25);
    float3 deep = mix(float3(0.12, 0.43, 0.45), float3(0.24, 0.42, 0.30), kind);
    float lake = 1.0 - smoothstep(0.04, 0.38, abs(kind - 0.45));
    deep = mix(deep, float3(0.12, 0.35, 0.47), lake * 0.72);
    float3 shallow = mix(float3(0.45, 0.64, 0.48), float3(0.48, 0.55, 0.34), kind);
    float3 base = mix(shallow, deep, smoothstep(0.006, 0.10, depth));
    float3 tint = mix(float3(0.53, 0.64, 0.83), float3(1.04, 1.01, 0.92), air.x);
    float sheen = smoothstep(0.37, 0.92, longWave) * smoothstep(-0.56, 0.96, crossWave);
    float wetEdge = smoothstep(0.006, 0.032, depth) * (1.0 - smoothstep(0.030, 0.070, depth));
    float reflection = sheen * 0.075 + wetEdge * 0.055 + longWave * 0.012;
    float3 water = base * tint * (1.0 - air.z * 0.15);
    water += float3(0.70, 0.89, 0.82) * reflection;
    water += float3(0.99, 0.55, 0.32) * air.y * (sheen * 0.075 + 0.018);
    float coastDepth = clamp((bed * 32.0 + 18.0) / 18.0, 0.0, 1.0);
    float3 coastBase = float3(0.081, 0.31, 0.39) + float3(0.26, 0.35, 0.19) * coastDepth;
    float3 coastLight = mix(float3(0.52, 0.60, 0.78), float3(1.0), air.x);
    float3 overcast = mix(float3(1.0), float3(0.78, 0.87, 0.96), air.z);
    float3 tidal = coastBase * coastLight * overcast;
    float shoreWave = sin(p.z * 18.0 + p.x * 8.3 + clock * 1.55 + sin(p.x * 5.7 - p.z * 2.1 + clock * 0.41) * 1.29);
    float seaCross = sin(p.x * 12.1 - p.z * 8.7 - clock * 0.89);
    float coastRipple = smoothstep(0.53, 0.96, shoreWave) * smoothstep(-0.16, 0.88, seaCross);
    tidal += float3(0.64, 0.89, 0.85) * ((shoreWave + seaCross) * 0.008 + coastRipple * 0.031);
    float inland = smoothstep(0.018, 0.17, p.y);
    float horizon = smoothstep(4.8, 16.0, viewDistance(params));
    float3 color = mix(tidal, water, inland);
    color = mix(color, light.haze, horizon * (0.42 + 0.24 * air.z));
    emit(params, linearFromDisplay(color), 1.0);
}

// The wind's direction in eighths from east toward +z, as fire records it.
constant float2 WindSteps[8] = {
    float2(1, 0), float2(1, 1), float2(0, 1), float2(-1, 1), float2(-1, 0), float2(-1, -1), float2(0, -1), float2(1, -1)
};

float2 windDrift(float4 custom) {
    return WindSteps[int(custom.x + 0.5) & 7] * 0.30;
}

/// Fire time: still at a fixed moment while the player asks for reduced motion (Kool uses 0.4 s for flames).
float fireClock(float time, Light light, float still) {
    return light.motion > 0.5 ? time : still;
}

/// Burned and cleared ground with soft, daubed edges where the scar meets different ground (Kool ScarShader).
/// Texture coordinates carry the tile's edge mask: x west 1 + east 2, y north 1 + south 2.
[[visible]]
void scarSurface(realitykit::surface_parameters params) {
    Light light = readLight(params.textures().custom());
    float2 p = params.geometry().world_position().xz;
    float4 tint = params.geometry().color();
    float drift = sin(p.x * 19.0 + p.y * 17.0) * 2.3;
    float daub = sin(p.x * 66.0 + p.y * 19.0 + drift) * cos(p.y * 51.0 - p.x * 17.0);
    float2 local = fract(p / 0.32) - 0.5;
    float2 edgeMask = round(params.geometry().uv0());
    float2 lowMask = edgeMask - floor(edgeMask * 0.5) * 2.0;
    float2 low = max(-local, float2(0.0)) * lowMask;
    float2 high = max(local, float2(0.0)) * step(float2(1.5), edgeMask);
    float edge = max(max(low.x, low.y), max(high.x, high.y));
    float opacity = 1.0 - smoothstep(0.33, 0.54, edge + daub * 0.038);
    float tone = 0.70 + light.water.x * 0.25 + daub * 0.14;
    emit(params, linearFromDisplay(tint.rgb * tone), tint.a * opacity);
}

/// Flames lick and flicker; embers rise downwind and fade (Kool FlameShader vertex stage).
[[visible]]
void flameLick(realitykit::geometry_parameters params) {
    Light light = readLight(params.textures().custom());
    float time = fireClock(params.uniforms().time(), light, 0.4);
    float4 data = params.geometry().color();
    float2 wind = windDrift(params.uniforms().custom_parameter());
    float phase = data.r * 6.283;
    float tip = data.g;
    float lick = sin(time * 9.5 + phase) * 0.035 * tip;
    float sway = cos(time * 7.1 + phase * 1.7) * 0.028 * tip;
    float flare = sin(time * 12.3 + phase * 2.3) * 0.06;
    float age = fract(time * 0.81 + data.r);
    float spark = 1.0 - data.a;
    float travel = age * spark;
    float fade = data.a + spark * (1.0 - age) * smoothstep(0.0, 0.14, age);
    params.geometry().set_model_position_offset(float3(lick + travel * wind.x, tip * flare + travel * 0.57, sway + travel * wind.y));
    params.geometry().set_custom_attribute(float4(tip, data.b, fade, 0.0));
}

/// Ember red at the base through amber to pale at the tips (Kool FlameShader fragment stage).
[[visible]]
void flameSurface(realitykit::surface_parameters params) {
    float4 heat = params.geometry().custom_attribute();
    float tip = heat.x;
    float3 ember = float3(0.78, 0.16, 0.03);
    float3 amber = float3(1.0, 0.55, 0.10);
    float3 pale = float3(1.0, 0.90, 0.55);
    float3 color = mix(mix(ember, amber, smoothstep(0.0, 0.45, tip)), pale, smoothstep(0.55, 1.0, tip));
    emit(params, linearFromDisplay(min(color * (1.05 + heat.y * 0.45), 1.0)), (1.0 - tip * tip) * heat.z);
}

/// Smoke puffs climb and lean downwind in a loop (Kool SmokeShader).
[[visible]]
void smokeRise(realitykit::geometry_parameters params) {
    Light light = readLight(params.textures().custom());
    float time = fireClock(params.uniforms().time(), light, 0.6);
    float4 data = params.geometry().color();
    float2 drift = windDrift(params.uniforms().custom_parameter());
    float age = fract(time * 0.22 + data.r);
    params.geometry().set_model_position_offset(float3(drift.x * age, age * 1.9, drift.y * age));
    params.geometry().set_custom_attribute(float4((1.0 - age) * smoothstep(0.0, 0.12, age) * data.g, 0.0, 0.0, 0.0));
}

[[visible]]
void smokeSurface(realitykit::surface_parameters params) {
    emit(params, linearFromDisplay(float3(0.24, 0.22, 0.21)), params.geometry().custom_attribute().x * 0.55);
}
