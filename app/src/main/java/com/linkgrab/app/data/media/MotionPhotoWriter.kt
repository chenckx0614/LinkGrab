package com.linkgrab.app.data.media

import java.io.InputStream
import java.io.OutputStream

/** Android Motion Photo 1.0: JPEG + XMP directory + trailing MP4.
 * Input JPEG must be freshly encoded (no pre-existing XMP, gain map or appended media).
 * Spec: https://developer.android.com/media/platform/motion-photo-format
 */
internal object MotionPhotoWriter {
    private val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    fun write(jpeg: InputStream, video: InputStream, videoLength: Long, output: OutputStream,
              checkpoint: () -> Unit = {}) {
        require(videoLength > 0) { "实况视频为空" }
        require(jpeg.read() == 0xff && jpeg.read() == 0xd8) { "实况封面必须为 JPEG" }
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about="" xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
                    xmlns:Container="http://ns.google.com/photos/1.0/container/"
                    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
                    Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1"
                    Camera:MotionPhotoPresentationTimestampUs="-1"
                    Camera:MicroVideo="1" Camera:MicroVideoVersion="1"
                    Camera:MicroVideoOffset="$videoLength" Camera:MicroVideoPresentationTimestampUs="-1">
                  <Container:Directory><rdf:Seq>
                    <rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li>
                    <rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength"/></rdf:li>
                  </rdf:Seq></Container:Directory>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val segmentLength = 2 + xmpHeader.size + xmp.size
        require(segmentLength <= 65535)
        output.write(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe1.toByte()))
        output.write(segmentLength shr 8)
        output.write(segmentLength and 0xff)
        output.write(xmpHeader)
        output.write(xmp)
        copy(jpeg, output, checkpoint)
        val copied = copy(video, output, checkpoint)
        require(copied == videoLength) { "实况视频长度不符，请重试" }
    }

    private fun copy(input: InputStream, output: OutputStream, checkpoint: () -> Unit): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkpoint()
            val count = input.read(buffer)
            if (count < 0) return total
            output.write(buffer, 0, count)
            total += count
        }
    }
}
