package id.branchworld.game

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform