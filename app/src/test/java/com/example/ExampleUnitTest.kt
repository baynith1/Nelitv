package com.example

import com.example.data.ChannelRepository
import com.example.player.ClearKeyUtil
import com.example.player.Mp4CencDecryptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ExampleUnitTest {

    @Test
    fun `channel repository contains all 32 channels with 13 Azam token streams and Tanzania priority`() {
        assertEquals(32, ChannelRepository.channels.size)
        val first13 = ChannelRepository.channels.take(13)
        assertTrue(first13.all { it.streamUrl.contains("cdnedgch2.azamtvltd.co.tz") })
        assertTrue(first13.all { it.streamUrl.contains("cdntoken=") })
        assertTrue(first13.all { it.isDash && it.isClearKey })

        val popTz = ChannelRepository.getChannelById("tv_1788953482934_twaqe")
        assertNotNull(popTz)
        assertEquals("POP Animation Network", popTz!!.name)
        assertEquals("Tanzania", popTz.country)
        assertEquals(2, popTz.priorityTier)
    }

    @Test
    fun `normalizeDashStreamUrl converts segment mp4 urls to master mpd urls with token`() {
        val rawAzam1 = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1-mp4a_160000_swa=20000-p=363288493000000-3702860930173333.mp4?cdntoken=${ChannelRepository.AZAM_CDN_TOKEN}"
        val normalized1 = ChannelRepository.normalizeDashStreamUrl(rawAzam1)
        assertEquals(
            "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken=${ChannelRepository.AZAM_CDN_TOKEN}",
            normalized1
        )

        val rawZbc2 = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC2/DASH/ZBC2-mp4a_160000=20000-p=363288647000000-init.mp4?cdntoken=${ChannelRepository.AZAM_CDN_TOKEN}"
        val normalizedZbc2 = ChannelRepository.normalizeDashStreamUrl(rawZbc2)
        assertEquals(
            "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC2/DASH/ZBC2.mpd?cdntoken=${ChannelRepository.AZAM_CDN_TOKEN}",
            normalizedZbc2
        )

        val rawWasafi = "https://cdnedgch2.azamtvltd.co.tz/live/eds/WasafiTV/DASH/WasafiTV-mp4a_160000=20000-p=363288641000000-init.mp4?cdntoken=${ChannelRepository.AZAM_CDN_TOKEN}"
        val normalizedWasafi = ChannelRepository.normalizeDashStreamUrl(rawWasafi)
        assertEquals(
            "https://cdnedgch2.azamtvltd.co.tz/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=${ChannelRepository.AZAM_CDN_TOKEN}",
            normalizedWasafi
        )
    }

    @Test
    fun `all category returns all channels`() {
        val result = ChannelRepository.filterChannels(query = "", category = "All")
        assertEquals(32, result.size)
        // Verify priority order: Azam (3) -> Tanzania (2) -> Others (0)
        assertEquals(3, result.first().priorityTier)
        assertEquals(0, result.last().priorityTier)
    }

    @Test
    fun `category filtering returns only matching category channels`() {
        val sports = ChannelRepository.filterChannels(query = "", category = "Sports")
        assertTrue(sports.isNotEmpty())
        assertTrue(sports.all { it.categories.any { cat -> cat.contains("sport", ignoreCase = true) } })

        val news = ChannelRepository.filterChannels(query = "", category = "News")
        assertTrue(news.isNotEmpty())
        assertTrue(news.all { it.categories.any { cat -> cat.contains("news", ignoreCase = true) } })

        val kids = ChannelRepository.filterChannels(query = "", category = "Kids")
        assertTrue(kids.isNotEmpty())
        assertTrue(kids.all { it.categories.any { cat -> cat.contains("kid", ignoreCase = true) } })

        val tanzania = ChannelRepository.filterChannels(query = "", category = "Tanzania")
        assertTrue(tanzania.isNotEmpty())

        val music = ChannelRepository.filterChannels(query = "", category = "Music")
        assertTrue(music.isNotEmpty())
        assertTrue(music.all { it.categories.any { cat -> cat.contains("music", ignoreCase = true) } })
    }

    @Test
    fun `search filters channel names case-insensitively`() {
        val azamLower = ChannelRepository.filterChannels(query = "azam", category = "All")
        assertTrue(azamLower.isNotEmpty())
        assertTrue(azamLower.all { it.name.contains("azam", ignoreCase = true) })

        val azamUpper = ChannelRepository.filterChannels(query = "AZAM", category = "All")
        assertEquals(azamLower.size, azamUpper.size)

        val bbc = ChannelRepository.filterChannels(query = "bbc", category = "All")
        assertTrue(bbc.any { it.name.contains("BBC", ignoreCase = true) })

        val none = ChannelRepository.filterChannels(query = "xyz123nonexistent", category = "All")
        assertTrue(none.isEmpty())
    }

    @Test
    fun `search combines with category filter properly`() {
        val sportsAzam = ChannelRepository.filterChannels(query = "sport", category = "Sports")
        assertTrue(sportsAzam.isNotEmpty())
        assertTrue(sportsAzam.all {
            it.categories.any { cat -> cat.contains("sport", ignoreCase = true) } &&
                    it.name.contains("sport", ignoreCase = true)
        })
    }

    @Test
    fun `clearkey jwk json generation is valid format`() {
        val kidHex = "c31df1600afc33799ecac543331803f2"
        val keyHex = "dd2101530e222f545997d4c553787f85"

        val jwkJson = ClearKeyUtil.createClearKeyJwkJson(kidHex, keyHex)
        assertTrue(jwkJson.contains("\"keys\":["))
        assertTrue(jwkJson.contains("\"kty\":\"oct\""))
        assertTrue(jwkJson.contains("\"k\":"))
        assertTrue(jwkJson.contains("\"kid\":"))
        assertTrue(jwkJson.contains("\"type\":\"temporary\""))

        val jwkBytes = ClearKeyUtil.createClearKeyJwkBytes(kidHex, keyHex)
        assertTrue(jwkBytes.isNotEmpty())
    }

    @Test
    fun `mpd content protection stripping removes all drm tags`() {
        val sampleMpd = """
            <MPD>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc" cenc:default_KID="c31df160-0afc-3379-9eca-c543331803f2"/>
                  <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed" value="Widevine Content Protection">
                    <cenc:pssh>AAAAZHBzc2g=</cenc:pssh>
                  </ContentProtection>
                  <Representation id="avc1_600000=10003" bandwidth="600000" />
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val decryptor = Mp4CencDecryptor(
            mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85")
        )
        val stripped = decryptor.stripMpdContentProtection(sampleMpd)
        assertFalse(stripped.contains("ContentProtection"))
        assertFalse(stripped.contains("cenc:pssh"))
        assertTrue(stripped.contains("Representation id=\"avc1_600000=10003\""))
    }

    @Test
    fun `live Azam Sports 1 DASH init and media segments decrypt cleanly`() {
        val azam1 = ChannelRepository.getChannelById("R17JUvbCEzu2eTbjnE74")!!
        val decryptor = Mp4CencDecryptor(azam1.clearKeys)

        val mpdBytes = fetchBytesIfOnline(azam1.streamUrl) ?: return
        val mpdXml = String(mpdBytes, Charsets.UTF_8)
        val strippedMpd = decryptor.stripMpdContentProtection(mpdXml)
        assertFalse("Stripped MPD must not contain ContentProtection", strippedMpd.contains("<ContentProtection"))

        val baseUrl = azam1.streamUrl.substringBeforeLast("/")
        val query = azam1.streamUrl.substringAfter("?", "")

        // Fetch video init segment
        val videoInitUrl = "$baseUrl/AzamSport1-avc1_600000=10003-p=363288493000000-init.mp4?$query"
        val videoInitBytes = fetchBytesIfOnline(videoInitUrl) ?: return
        val initStrBefore = String(videoInitBytes, Charsets.ISO_8859_1)
        assertTrue("Encrypted video init should contain encv", initStrBefore.contains("encv"))

        val processedVideoInit = decryptor.processMp4Segment(videoInitBytes.copyOf())
        val initStrAfter = String(processedVideoInit, Charsets.ISO_8859_1)
        assertEquals(videoInitBytes.size, processedVideoInit.size)
        assertFalse("Processed video init must not contain encv", initStrAfter.contains("encv"))
        assertFalse("Processed video init must not contain sinf", initStrAfter.contains("sinf"))
        assertFalse("Processed video init must not contain pssh", initStrAfter.contains("pssh"))
        assertTrue("Processed video init must contain avc1", initStrAfter.contains("avc1"))

        // Fetch audio init segment
        val audioInitUrl = "$baseUrl/AzamSport1-mp4a_160000_swa=20000-p=363288493000000-init.mp4?$query"
        val audioInitBytes = fetchBytesIfOnline(audioInitUrl) ?: return
        val processedAudioInit = decryptor.processMp4Segment(audioInitBytes.copyOf())
        val audioInitAfter = String(processedAudioInit, Charsets.ISO_8859_1)
        assertFalse("Processed audio init must not contain enca", audioInitAfter.contains("enca"))
        assertTrue("Processed audio init must contain mp4a", audioInitAfter.contains("mp4a"))

        // Extract latest video & audio segment timestamps from MPD and verify media segment decryption
        val timeRegex = Regex("""<S t="(\d+)"""")
        val allTimes = timeRegex.findAll(mpdXml).map { it.groupValues[1] }.toList()
        assertTrue("MPD should contain segment timestamps", allTimes.isNotEmpty())

        val audioTime = allTimes.first()
        val videoTime = allTimes.last()

        val videoSegUrl = "$baseUrl/AzamSport1-avc1_600000=10003-p=363288493000000-$videoTime.mp4?$query"
        val videoSegBytes = fetchBytesIfOnline(videoSegUrl) ?: return
        val segBefore = String(videoSegBytes, Charsets.ISO_8859_1)
        assertTrue("Encrypted video segment should contain senc", segBefore.contains("senc"))
        val decryptedVideoSeg = decryptor.processMp4Segment(videoSegBytes.copyOf())
        val segAfter = String(decryptedVideoSeg, Charsets.ISO_8859_1)
        assertEquals(videoSegBytes.size, decryptedVideoSeg.size)
        assertFalse("Decrypted video segment must not contain senc", segAfter.contains("senc"))
        assertTrue("Decrypted video segment must contain mdat box", segAfter.contains("mdat"))

        // Fetch and decrypt audio media segment
        val audioSegUrl = "$baseUrl/AzamSport1-mp4a_160000_swa=20000-p=363288493000000-$audioTime.mp4?$query"
        val audioSegBytes = fetchBytesIfOnline(audioSegUrl) ?: return
        val decryptedAudioSeg = decryptor.processMp4Segment(audioSegBytes.copyOf())
        val audioSegAfter = String(decryptedAudioSeg, Charsets.ISO_8859_1)
        assertEquals(audioSegBytes.size, decryptedAudioSeg.size)
        assertFalse("Decrypted audio segment must not contain senc", audioSegAfter.contains("senc"))
        assertTrue("Decrypted audio segment must contain mdat box", audioSegAfter.contains("mdat"))
    }

    private fun fetchBytesIfOnline(urlStr: String): ByteArray? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    @Test
    fun `dash channels have clearkey configured`() {
        val azam1 = ChannelRepository.getChannelById("R17JUvbCEzu2eTbjnE74")
        assertNotNull(azam1)
        assertTrue(azam1!!.isDash)
        assertTrue(azam1.isClearKey)
        assertEquals("dd2101530e222f545997d4c553787f85", azam1.clearKeys["c31df1600afc33799ecac543331803f2"])
    }

    @Test
    fun `hls channels have none encryption`() {
        val bbc = ChannelRepository.getChannelById("9mFG4jZCtlXCEKfv5TF6")
        assertNotNull(bbc)
        assertTrue(bbc!!.isHls)
        assertFalse(bbc.isClearKey)
        assertEquals("none", bbc.encryptionType)
    }

    @Test
    fun `firestore movie series episode and tvChannel seed models prioritize Tanzania`() {
        val movie = com.example.data.MediaContentRepository.getMediaById("mov_1788911132603_wdgav")
        assertNotNull(movie)
        assertEquals("Never a Thief", movie!!.title)
        assertTrue(movie.narrated)
        assertEquals("Swahili", movie.narrationLanguage)
        assertTrue(movie.toPlayableChannel().isMp4)

        val series = com.example.data.MediaContentRepository.getMediaById("ser_1788988691994_5tt72")
        assertNotNull(series)
        assertEquals("Squid Game", series!!.title)
        assertEquals(3, series.seasons.size)

        val episodes = com.example.data.MediaContentRepository.getEpisodesForSeries("ser_1788988691994_5tt72", 3)
        assertTrue(episodes.isNotEmpty())
        assertEquals("Keys and Knives", episodes.first().name)
    }

    @Test
    fun `homepage featured live tv channels contain only Azam Sports 1 and 2 Azam One and Two Sinema Zetu KIX and WWE`() {
        val homeChannels = ChannelRepository.homePageFeaturedChannels
        assertEquals(7, homeChannels.size)
        val names = homeChannels.map { it.name }
        assertTrue(names.any { it.contains("Azam Sports 1", ignoreCase = true) })
        assertTrue(names.any { it.contains("Azam Sports 2", ignoreCase = true) })
        assertTrue(names.any { it.equals("Azam One", ignoreCase = true) })
        assertTrue(names.any { it.equals("Azam Two", ignoreCase = true) })
        assertTrue(names.any { it.equals("Sinema Zetu", ignoreCase = true) })
        assertTrue(names.any { it.equals("KIX", ignoreCase = true) })
        assertTrue(names.any { it.equals("WWE", ignoreCase = true) })
    }
}
