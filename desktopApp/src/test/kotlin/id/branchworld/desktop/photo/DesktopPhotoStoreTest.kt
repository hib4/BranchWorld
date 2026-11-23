package id.branchworld.desktop.photo

import id.branchworld.game.platform.photo.PhotoMeta
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopPhotoStoreTest {
    @Test
    fun keepsSeparateDecodablePhotosInTheFallbackWhenPicturesIsUnavailable() {
        val directory = createTempDirectory("branchworld-photos-")
        try {
            val unavailable = Files.createFile(directory.resolve("Pictures"))
            val fallback = directory.resolve("Photos")
            val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB).apply { setRGB(1, 0, Color.ORANGE.rgb) }
            val png = ByteArrayOutputStream().also { check(ImageIO.write(image, "png", it)) }.toByteArray()

            val first = saveDesktopPhoto(png, PhotoMeta(104729, 360), unavailable, fallback)
            val second = saveDesktopPhoto(png, PhotoMeta(104729, 360), unavailable, fallback)

            assertEquals(fallback, first.parent)
            assertNotEquals(first, second)
            assertEquals(2L, Files.list(fallback).use { it.count() })
            for (file in listOf(first, second)) {
                assertTrue(file.fileName.toString().endsWith(".png"))
                val saved = ImageIO.read(file.toFile())
                assertEquals(3, saved.width)
                assertEquals(2, saved.height)
                assertEquals(Color.ORANGE.rgb, saved.getRGB(1, 0))
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
