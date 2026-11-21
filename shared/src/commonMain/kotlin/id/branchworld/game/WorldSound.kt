package id.branchworld.game

import branchworld.shared.generated.resources.Res

enum class WorldSound(private val fileName: String) {
    Fork("fork.wav"),
    Save("save.wav");

    fun assetUri(): String = Res.getUri("files/sounds/$fileName")
}
