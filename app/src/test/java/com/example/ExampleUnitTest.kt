package com.example

import com.example.data.remote.JioSaavnParser
import com.example.util.formatCompactCount
import com.example.util.formatRemainingMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseSongs_handlesWrappedResultsAndNormalizesFields() {
        val json = """
            {
              "success": true,
              "data": {
                "results": [
                  {
                    "id": "abc123",
                    "name": "Apna Bana Le &amp; More",
                    "album": { "name": "Bhediya &quot;OST&quot;" },
                    "duration": "261",
                    "url": "https://www.jiosaavn.com/song/apna-bana-le/123",
                    "artists": {
                      "primary": [
                        { "name": "Arijit Singh", "image": [{ "url": "http://c.saavncdn.com/artist_150x150.jpg" }] },
                        { "name": "Sachin-Jigar" }
                      ]
                    },
                    "image": [
                      { "quality": "50x50", "url": "http://c.saavncdn.com/cover_50x50.jpg" },
                      { "quality": "150x150", "url": "http://c.saavncdn.com/cover_150x150.jpg" }
                    ],
                    "downloadUrl": [
                      { "quality": "96kbps", "url": "http://aac.saavncdn.com/low.mp4" },
                      { "quality": "320kbps", "url": "http://aac.saavncdn.com/high.mp4" }
                    ]
                  },
                  {
                    "id": "bad_no_audio",
                    "name": "Broken Track",
                    "url": "https://www.jiosaavn.com/song/broken/999"
                  }
                ]
              }
            }
        """.trimIndent()

        val songs = JioSaavnParser.parseSongs(json)
        assertEquals(1, songs.size)
        val song = songs.first()
        assertEquals("abc123", song.id)
        assertEquals("Arijit Singh, Sachin-Jigar", song.artist)
        assertEquals("https://c.saavncdn.com/cover_500x500.jpg", song.image)
        assertEquals("https://aac.saavncdn.com/high.mp4", song.url)
        assertEquals(261, song.duration)
        assertEquals("https://c.saavncdn.com/artist_500x500.jpg", song.primaryArtistImage)
    }

    @Test
    fun parseSongs_handlesPlainArrayResponse() {
        val json = """
            [
              {
                "id": "s1",
                "title": "Kesariya",
                "primaryArtists": "Arijit Singh",
                "album": "Brahmastra",
                "duration": 268,
                "image": ["https://c.saavncdn.com/k_50x50.jpg"],
                "downloadUrl": ["https://aac.saavncdn.com/k_320.mp4"]
              }
            ]
        """.trimIndent()

        val songs = JioSaavnParser.parseSongs(json)
        assertEquals(1, songs.size)
        assertEquals("Kesariya", songs[0].title)
        assertEquals("https://c.saavncdn.com/k_500x500.jpg", songs[0].image)
        assertEquals("https://aac.saavncdn.com/k_320.mp4", songs[0].url)
    }

    @Test
    fun parseSongs_skipsEmptyDownloadUrlAndJioSaavnWebUrl() {
        val json = """
            {
              "results": [
                { "id": "x1", "name": "NoAudio", "url": "https://www.jiosaavn.com/song/x1", "downloadUrl": [] },
                { "id": "", "name": "NoId", "downloadUrl": [{ "url": "https://aac.saavncdn.com/a.mp4" }] }
              ]
            }
        """.trimIndent()
        assertTrue(JioSaavnParser.parseSongs(json).isEmpty())
    }

    @Test
    fun formatters_produceExpectedOutput() {
        assertEquals("206", formatCompactCount(206))
        assertEquals("1.2K", formatCompactCount(1240))
        assertEquals("3.4M", formatCompactCount(3_400_000))
        assertEquals("-1:30", formatRemainingMs(30_000, 120_000))
    }
}
