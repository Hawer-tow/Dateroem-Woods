package com.dateroem.woodsales.ui.marketing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Editing a campaign re-sends its media key. Every address the app can be
// given for the same file has to come back as the same key, or a save
// wipes the campaign's video ("the video is gone").
class MediaKeyOfTest {

    private val key = "campaigns/value-addition-a60d7199.mp4"

    @Test
    fun mobileServerForm() = assertEquals(key, mediaKeyOf("media/$key"))

    @Test
    fun fullAddresses() {
        assertEquals(key, mediaKeyOf("http://127.0.0.1:8000/media/$key"))
        assertEquals(key, mediaKeyOf("https://example.ngrok-free.dev/mobile-api/media/$key"))
        assertEquals(key, mediaKeyOf("/media/$key"))
    }

    @Test
    fun bareKeyAndQuery() {
        assertEquals(key, mediaKeyOf(key))
        assertEquals(key, mediaKeyOf("media/$key?v=2"))
    }

    @Test
    fun nothing() {
        assertNull(mediaKeyOf(null))
        assertNull(mediaKeyOf(""))
        assertNull(mediaKeyOf("media/"))
    }
}
