package id.branchworld.game

import java.net.URI
import java.util.concurrent.Executors
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

internal class DesktopWorldSounds {
    private val audio = Executors.newSingleThreadExecutor { task -> Thread(task, "branchworld-audio").apply { isDaemon = true } }
    private val clips = mutableMapOf<WorldSound, Clip>()

    fun play(sound: WorldSound) {
        if (audio.isShutdown) return
        audio.execute {
            try {
                val clip = clips.getOrPut(sound) {
                    AudioSystem.getAudioInputStream(URI(sound.assetUri()).toURL()).use { stream ->
                        AudioSystem.getClip().let { clip ->
                            try {
                                clip.open(stream)
                                clip
                            } catch (error: Exception) {
                                clip.close()
                                throw error
                            }
                        }
                    }
                }
                clip.stop()
                clip.framePosition = 0
                clip.start()
                println("BranchWorldSound Played ${sound.name}")
            } catch (error: Exception) {
                println("BranchWorldSound Could not play ${sound.name}: ${error.message}")
            }
        }
    }

    fun close() {
        audio.execute { clips.values.forEach(Clip::close) }
        audio.shutdown()
    }
}
