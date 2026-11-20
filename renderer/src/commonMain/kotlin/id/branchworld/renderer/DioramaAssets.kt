package id.branchworld.renderer

internal enum class DioramaAsset(val file: String) {
    Pine("tree_pineRoundA.glb"),
    Rock("rock_smallH.glb"),
    Bunny("animal-bunny.glb"),
    Deer("animal-deer.glb"),
    ;

    val path: String get() = "diorama/$file"
}

internal val CreatureClips = listOf("idle", "walk", "eat")
