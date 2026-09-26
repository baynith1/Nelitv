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
    fun `channel repository contains all 36 channels with 17 Azam DASH MPD streams and Tanzania priority`() {
        assertEquals(36, ChannelRepository.channels.size)
        val first17 = ChannelRepository.channels.take(17)
        assertTrue(first17.all {
            it.isDash &&
                    it.streamUrl.startsWith(ChannelRepository.DEFAULT_AZAM_CDN_HOST) &&
                    it.streamUrl.contains(".mpd?cdntoken=") &&
                    it.isClearKey
        })

        val popTz = ChannelRepository.getChannelById("tv_1788953482934_twaqe")
        assertNotNull(popTz)
        assertEquals("POP Animation Network", popTz!!.name)
        assertEquals("Tanzania", popTz.country)
        assertEquals(2, popTz.priorityTier)
        assertTrue(popTz.streamUrl.endsWith(".m3u8", ignoreCase = true))
    }

    @Test
    fun `normalizeDashStreamUrl rewrites cdnblncr to cdnedgch2 and attaches cdntoken for mpd and mp4 cdn urls`() {
        val rawAzamMpd = "https://cdnblncr.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1.mpd"
        val normalizedMpd = ChannelRepository.normalizeDashStreamUrl(rawAzamMpd)
        assertTrue(normalizedMpd.startsWith("https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken="))
        assertTrue(normalizedMpd.endsWith(ChannelRepository.AZAM_CDN_TOKEN))

        val rawAzamMp4Seg = "https://cdnblncr.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1-init.mp4"
        val normalizedMp4 = ChannelRepository.normalizeDashStreamUrl(rawAzamMp4Seg)
        assertTrue(normalizedMp4.startsWith("https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1-init.mp4?cdntoken="))
        assertTrue(normalizedMp4.endsWith(ChannelRepository.AZAM_CDN_TOKEN))

        val m3u8Stream = "https://cdn4.skygo.mn/live/disk1/Cartoon_Network/HLSv3-FTA/Cartoon_Network.m3u8"
        assertEquals(m3u8Stream, ChannelRepository.normalizeDashStreamUrl(m3u8Stream))
    }

    @Test
    fun `all category returns all channels`() {
        val result = ChannelRepository.filterChannels(query = "", category = "All")
        assertEquals(36, result.size)
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
    fun `azam channels have valid clearkey pairs and cdn token`() {
        val azam1 = ChannelRepository.getChannelById("R17JUvbCEzu2eTbjnE74")
        assertNotNull(azam1)
        assertTrue(azam1!!.isDash)
        assertTrue(azam1.isClearKey)
        assertEquals("c31df1600afc33799ecac543331803f2", azam1.clearKeyId)
        assertEquals("dd2101530e222f545997d4c553787f85", azam1.clearKey)
        assertTrue(azam1.streamUrl.contains("cdntoken=${ChannelRepository.DEFAULT_AZAM_CDN_TOKEN}"))
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
    fun `homepage featured live tv channels contain expanded Azam TV bouquet KIX and WWE`() {
        val homeChannels = ChannelRepository.homePageFeaturedChannels
        assertEquals(14, homeChannels.size)
        val names = homeChannels.map { it.name }
        assertTrue(names.any { it.contains("Azam Sports 1", ignoreCase = true) })
        assertTrue(names.any { it.contains("Azam Sports 2", ignoreCase = true) })
        assertTrue(names.any { it.equals("Azam One", ignoreCase = true) })
        assertTrue(names.any { it.equals("Azam Two", ignoreCase = true) })
        assertTrue(names.any { it.equals("Sinema Zetu", ignoreCase = true) })
        assertTrue(names.any { it.equals("Azam Xtra HD", ignoreCase = true) })
        assertTrue(names.any { it.equals("Azam Movies HD", ignoreCase = true) })
        assertTrue(names.any { it.equals("Clouds TV HD", ignoreCase = true) })
        assertTrue(names.any { it.equals("ITV Tanzania HD", ignoreCase = true) })
        assertTrue(names.any { it.equals("KIX", ignoreCase = true) })
        assertTrue(names.any { it.equals("WWE", ignoreCase = true) })
    }
}
