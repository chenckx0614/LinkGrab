package com.linkgrab.app.data.media

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class MotionPhotoWriterTest {
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(),
        0, 4, 1, 2, 0xff.toByte(), 0xd9.toByte())
    private val video = byteArrayOf(0, 0, 0, 12, 102, 116, 121, 112, 105, 115, 111, 109)

    @Test fun directoryPointsToExactTrailingVideoAndImageBytesArePreserved() {
        val out = ByteArrayOutputStream()
        MotionPhotoWriter.write(jpeg.inputStream(), video.inputStream(), video.size.toLong(), out)
        val bytes = out.toByteArray()
        assertEquals(0xe1, bytes[3].toInt() and 0xff)
        val segmentLength = ((bytes[4].toInt() and 0xff) shl 8) or (bytes[5].toInt() and 0xff)
        val jpegTail = 4 + segmentLength
        assertArrayEquals(jpeg.copyOfRange(2, jpeg.size), bytes.copyOfRange(jpegTail, bytes.size - video.size))
        assertArrayEquals(video, bytes.copyOfRange(bytes.size - video.size, bytes.size))
        val header = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
        assertArrayEquals(header, bytes.copyOfRange(6, 6 + header.size))
        val xml = bytes.copyOfRange(6 + header.size, jpegTail)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val camera = "http://ns.google.com/photos/1.0/camera/"
        val description = doc.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "Description").item(0) as org.w3c.dom.Element
        assertEquals("1", description.getAttributeNS(camera, "MotionPhoto"))
        assertEquals(video.size.toString(), description.getAttributeNS(camera, "MicroVideoOffset"))
        val items = doc.getElementsByTagNameNS("http://ns.google.com/photos/1.0/container/", "Item")
        assertEquals(2, items.length)
        val motion = items.item(1) as org.w3c.dom.Element
        val itemNs = "http://ns.google.com/photos/1.0/container/item/"
        assertEquals("video/mp4", motion.getAttributeNS(itemNs, "Mime"))
        assertEquals("MotionPhoto", motion.getAttributeNS(itemNs, "Semantic"))
        assertEquals(video.size.toString(), motion.getAttributeNS(itemNs, "Length"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonJpegInput() {
        MotionPhotoWriter.write(byteArrayOf(1, 2).inputStream(), video.inputStream(), video.size.toLong(), ByteArrayOutputStream())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTruncatedVideo() {
        MotionPhotoWriter.write(jpeg.inputStream(), video.inputStream(), video.size + 1L, ByteArrayOutputStream())
    }

    @Test(expected = java.util.concurrent.CancellationException::class)
    fun copyingRespondsToCancellation() {
        MotionPhotoWriter.write(jpeg.inputStream(), video.inputStream(), video.size.toLong(), ByteArrayOutputStream()) {
            throw java.util.concurrent.CancellationException()
        }
    }
}
