package id.branchworld.game.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameSettingsTest {
    @Test
    fun aProfileSavedBeforeMusicExistedKeepsItsSettingsAndHearsMusic() {
        val older = """{"version":1,"settings":{"worldSounds":false,"interfaceSounds":true,"reducedMotion":true,"textScale":115}}"""
        val settings = checkNotNull(PlayerProfileCodec.decode(older.encodeToByteArray())).settings
        assertTrue(settings.music)
        assertFalse(settings.worldSounds)
        assertTrue(settings.reducedMotion)
        assertEquals(115, settings.textScale)
    }

    @Test
    fun turningMusicOffIsKept() {
        val profile = PlayerProfile(settings = GameSettings(music = false))
        val decoded = checkNotNull(PlayerProfileCodec.decode(PlayerProfileCodec.encode(profile)))
        assertFalse(decoded.settings.music)
    }
}
