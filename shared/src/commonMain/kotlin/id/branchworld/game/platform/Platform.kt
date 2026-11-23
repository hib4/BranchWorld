package id.branchworld.game.platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform