package id.branchworld.renderer

internal enum class DioramaAsset(val file: String) {
    Pine("tree_pineRoundA.glb"),
    RoundedPine("tree_pineRoundB.glb"),
    TallPine("tree_pineTallA_detailed.glb"),
    Rock("rock_smallH.glb"),
    Bunny("animal-bunny.glb"),
    Deer("animal-deer.glb"),
    Fox("animal-fox.glb"),
    ;

    val path: String get() = "diorama/$file"
}

internal val CreatureClips = listOf("idle", "walk", "run", "eat")
