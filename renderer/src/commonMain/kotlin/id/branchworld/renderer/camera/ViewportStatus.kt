package id.branchworld.renderer.camera

/** What the 3D view is doing. Everything but [Ready] is shown over the view so the player knows why it is still. */
internal sealed interface ViewportStatus {
    /** The words shown over the view, or null once the island is drawn. */
    val message: String?

    data object Starting : ViewportStatus {
        override val message = "Starting renderer…"
    }

    data object Loading : ViewportStatus {
        override val message = "Loading island…"
    }

    data object Ready : ViewportStatus {
        override val message: String? = null
    }

    data class Failed(override val message: String) : ViewportStatus
}
