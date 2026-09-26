package com.example.data

import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object ChannelRepository {

    const val DEFAULT_AZAM_CDN_HOST = "https://cdnedgch2.azamtvltd.co.tz"
    const val DEFAULT_AZAM_CDN_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwNDEyNTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZDE5NDU4MjNlZWUyZjEzNCIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.KVTVBBod8HqPeilMn-Pc-zKhn3ecdoJcuztd4u092LvlLxWoZQCqFiQqLPXMso7loCQjejhKD4xxOOysa4NFkQ=="
    const val DEFAULT_AZAM_CDN_EXP = 1790412582L
    const val DEFAULT_AZAM_CDN_SOURCE = "cache"

    @Volatile
    var AZAM_CDN_HOST: String = DEFAULT_AZAM_CDN_HOST
        private set

    @Volatile
    var AZAM_CDN_TOKEN: String = DEFAULT_AZAM_CDN_TOKEN
        private set

    @Volatile
    var AZAM_CDN_EXP: Long = DEFAULT_AZAM_CDN_EXP
        private set

    @Volatile
    var AZAM_CDN_SOURCE: String = DEFAULT_AZAM_CDN_SOURCE
        private set

    val categories = listOf(
        "All",
        "Azam TV",
        "Sports",
        "Entertainment",
        "Movies",
        "Kids",
        "News",
        "Music",
        "Africa",
        "Tanzania",
        "Documentary",
        "Other"
    )

    val channels: List<LiveChannel> = listOf(
        // 1. AZAM SPORT 1 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "R17JUvbCEzu2eTbjnE74",
            name = "Azam Sports 1 HD",
            description = "mpira live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85"),
            country = "Tanzania",
            featured = true
        ),
        // 2. AZAM SPORT 2 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f",
            name = "Azam Sports 2 HD",
            description = "mpira live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport2/DASH/AzamSport2.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Y7Cj3Wtj/azam-sport-2-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("739e7499125b31cc9948da8057b84cf9" to "1b7d44d798c351acc02f33ddfbb7682a"),
            country = "Tanzania",
            featured = true
        ),
        // 3. AZAM SPORT 3 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "1c976127-e8a4-4bd6-8e73-5da0edce369b",
            name = "Azam Sports 3 HD",
            description = "mpiraa live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport3/DASH/AzamSport3.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/2YfLQ545/1000221070.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("2f12d7b889de381a9fb5326ca3aa166d" to "51c2d733a54306fdf89acd4c9d4f6005"),
            country = "Tanzania",
            featured = true
        ),
        // 4. AZAM SPORT 4 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c",
            name = "Azam Sports 4 HD",
            description = "Mpira Live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport4/DASH/AzamSport4.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/SwtFQNsh/1000221063.jpg",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("fbcac74e355b3b5d9b6cbc56c7597dbf" to "5f980e872b378d88b46f656a806205c3"),
            country = "Tanzania",
            featured = true
        ),
        // 5. AZAM ONE (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "c405ae74-c4c5-4842-9f26-130ce380b307",
            name = "Azam One",
            description = "Burudani na Filamu za Afrika Mashariki",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("b5cbe1bb5acf3c7f9995be428245cfcd" to "89f1188a11e5e000d4443eb27ca378e1"),
            country = "Tanzania",
            featured = true
        ),
        // 6. AZAM TWO (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "008ffe6e-a30f-4ed1-9ddb-4033dde18576",
            name = "Azam Two",
            description = "Tamthilia za Kiswahili na Burudani Live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamTwo/DASH/AzamTwo.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("18f515fd536b3e728e9844e77d9f0fa8" to "c96dfd9b65560f87a1a95b8aeda0432d"),
            country = "Tanzania",
            featured = true
        ),
        // 7. SINEMA ZETU (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e",
            name = "Sinema Zetu",
            description = "Filamu za Kiswahili & Bongo Movies 24/7",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("d628ae37a8f0336b970f250d9699461e" to "1194c3d60bb494aabe9114ca46c2738e"),
            country = "Tanzania",
            featured = true
        ),
        // 8. AZAM XTRA HD (Added Azam TV Channel #1 - MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "azam_xtra_hd_14",
            name = "Azam Xtra HD",
            description = "Tamthilia, Reality & Vipindi Maalum vya Azam TV",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("b5cbe1bb5acf3c7f9995be428245cfcd" to "89f1188a11e5e000d4443eb27ca378e1"),
            country = "Tanzania",
            featured = true
        ),
        // 9. AZAM MOVIES HD (Added Azam TV Channel #2 - MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "azam_movies_hd_15",
            name = "Azam Movies HD",
            description = "Sinema Mpya & Action Cinema 24/7 • Azam TV",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("movies", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("d628ae37a8f0336b970f250d9699461e" to "1194c3d60bb494aabe9114ca46c2738e"),
            country = "Tanzania",
            featured = true
        ),
        // 10. CLOUDS TV HD (Added Azam TV Channel #3 - MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "azam_clouds_tv_16",
            name = "Clouds TV HD",
            description = "The People's Station • Burudani & Muziki Live (Azam TV)",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("8714fe102679348e9c76cfd315dacaa0" to "a8b86ceda831061c13c7c4c67bd77f8e"),
            country = "Tanzania",
            featured = true
        ),
        // 11. ITV TANZANIA HD (Added Azam TV Channel #4 - MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "azam_itv_tz_17",
            name = "ITV Tanzania HD",
            description = "Super Brand • Habari, Tamthilia & Vipindi Live (Azam TV)",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/UTV/DASH/UTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("news", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894"),
            country = "Tanzania",
            featured = true
        ),
        // 12. UTV (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "d7b415f7-d024-4c7f-a570-653367e8dc5c",
            name = "UTV",
            description = "Habari, Michezo na Burudani • Azam TV",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/UTV/DASH/UTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894"),
            country = "Tanzania",
            featured = true
        ),
        // 13. ZBC2 (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "b502217f-a9d0-4aef-99e6-8a784adedc65",
            name = "ZBC2",
            description = "Zanzibar Broadcasting Corporation 2",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/ZBC2/DASH/ZBC2.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.imgur.com/5HqOXH0.jpeg",
            categories = listOf("tanzania", "entertainment"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("f38b03549d023f3a883997328537bf4d" to "90f9d2d9cc26009c437905692ad70730"),
            country = "Tanzania",
            featured = true
        ),
        // 14. ZBC (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "101e9c3f-de90-47fb-a69c-342be8a0bb80",
            name = "ZBC",
            description = "Zanzibar Broadcasting Corporation",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/ZBC/DASH/ZBC.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/xtynWQsN/1000221078.png",
            categories = listOf("tanzania", "other"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("e91fec140bc5316f919b4dc9c16287d7" to "79884fad0dbfcdc43d3e33c82a1f1cfa"),
            country = "Tanzania"
        ),
        // 15. KIX (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "4f36f2d1-ff7f-467b-b4f2-6f303263f28b",
            name = "KIX",
            description = "Action Movies • Azam TV",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/KIXMovies/DASH/KIXMovies.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/ch6qGQT3/KIX-logo-svg.png",
            categories = listOf("movies"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("a7e155b282f33335ae8d553f169f443c" to "c3fdcfd5d509f1ed8550d76a525e34e5"),
            featured = true
        ),
        // 16. Crown Tv (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "bba104f4-f5ac-41c9-aa36-7af71aaa1993",
            name = "Crown Tv",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/CrownTv/DASH/CrownTv.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/GfWDtdQT/1000221075.png",
            categories = listOf("music", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("e3eb6e0656ec3c22aa308aaa3f82c565" to "a7634d6defd2c255135095c45bc442fb"),
            country = "Tanzania",
            featured = true
        ),
        // 17. Wasafi (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "80e54146-1d9b-4c91-8f71-de0ea4866833",
            name = "Wasafi",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("8714fe102679348e9c76cfd315dacaa0" to "a8b86ceda831061c13c7c4c67bd77f8e"),
            country = "Tanzania",
            featured = true
        ),
        // 18. POP Animation Network (Tanzania Live TV - HLS M3U8)
        LiveChannel(
            id = "tv_1788953482934_twaqe",
            name = "POP Animation Network",
            description = "Kids • Tanzania Live TV",
            streamUrl = "https://narrative-popkids-rakuten.amagi.tv/1080p/index.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSDr0lcgVyDkVpdt6fh7yCYby-shZUQ3K3e_pBwt6j8HA&s=10",
            categories = listOf("kids", "tanzania"),
            language = "en",
            encryptionType = "none",
            country = "Tanzania",
            featured = true,
            enabled = true,
            published = true
        ),
        // 19. Baby Tv (MPD + ClearKey)
        LiveChannel(
            id = "68354b4a-d06c-4c8b-b6be-85cc3e95a43b",
            name = "Baby Tv",
            description = "",
            streamUrl = "https://cache07.zapitv.com/live/eds_c2/BABYTV/dash_live_ez/BABYTV.mpd",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/B0D1MgZ/Baby-TV-logo.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("7556319f3ce54d51b955fa0887ee7388" to "d36a3893e796b5934d17fd866f133a82")
        ),
        // 20. TNT SPORTS 1 HD (MPD + ClearKey)
        LiveChannel(
            id = "ee2ba70e-c28b-40ce-8ba1-0c20f133d04c",
            name = "TNT SPORTS 1  HD",
            description = "",
            streamUrl = "https://live-pv-ta.amazon.fastly-edge.com/lhr-nitro/live/clients/dash/enc/fb6jy4pxts/out/v1/f8fa17f087564f51aa4d5c700be43ec4/cenc.mpd",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/R4NBCtMm/4ae6eef1-b7ab-4fcd-bc20-18ac49124d95.jpg",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("f288380ca4cef9ad3f27a92a08e9bb8b" to "9f18d26291d9230833501f7f822f6875")
        ),
        // 21. TNT SPORTS 2 HD (MPD + ClearKey)
        LiveChannel(
            id = "ba2b232c-a79d-4130-b5d8-4883fbce7a1d",
            name = "TNT SPORTS 2 HD",
            description = "",
            streamUrl = "https://live-pv-ta.amazon.fastly-edge.com/lhr-nitro/live/clients/dash/enc/fb6jy4pxts/out/v1/f8fa17f087564f51aa4d5c700be43ec4/cenc.mpd",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/R4NBCtMm/4ae6eef1-b7ab-4fcd-bc20-18ac49124d95.jpg",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("f288380ca4cef9ad3f27a92a08e9bb8b" to "9f18d26291d9230833501f7f822f6875")
        ),
        // 22. Cartoon network (M3U8 HLS)
        LiveChannel(
            id = "80212719-1b5c-45b3-9e52-b59e533705f1",
            name = "Cartoon network",
            description = "",
            streamUrl = "https://cdn4.skygo.mn/live/disk1/Cartoon_Network/HLSv3-FTA/Cartoon_Network.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/8gV4tq4g/1000221079.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 23. 1 KZN TV (M3U8 HLS)
        LiveChannel(
            id = "8Q6H024OmdN8zIZTG4Zz",
            name = "1 KZN TV",
            description = "ENJOY STREAM",
            streamUrl = "https://cdn.freevisiontv.co.za/sttv/smil:1kzn.stream.smil/chunklist_b480000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://starsat.co.za/wp-content/uploads/2023/08/1KZN-TV.png",
            categories = listOf("africa"),
            language = "en",
            encryptionType = "none"
        ),
        // 24. YAHOO FINANCE (M3U8 HLS)
        LiveChannel(
            id = "Vshw4JeUaRpzDcNdlzcn",
            name = "YAHOO FINANCE",
            description = "WATCH NOW",
            streamUrl = "https://d1ewctnvcwvvvu.cloudfront.net/240p-cc/index.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://images-cdn3.welcomesoftware.com/assets/yahoo+finance.jpg/Zz0yNWFjNTk0NjlkMmQxMWVmYjhlNjFlYTY2MTI5N2IyNg==?width=768&height=430",
            categories = listOf("other"),
            language = "en",
            encryptionType = "none"
        ),
        // 25. BBC NEWS (M3U8 HLS)
        LiveChannel(
            id = "9mFG4jZCtlXCEKfv5TF6",
            name = "BBC NEWS",
            description = "NEWS",
            streamUrl = "https://vs-hls-push-ww-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_news_channel_hd/t=3840/v=pv14/b=5070016/main.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://yt3.googleusercontent.com/v4JamQ9B-PUiJHjmZQs9UwTaoLQW8vijJMMpV5QvA2wHQ6iwWM8Q1s6O4jgTl0dtDigVWAi7SA=s900-c-k-c0x00ffffff-no-rj",
            categories = listOf("news"),
            language = "en",
            encryptionType = "none"
        ),
        // 26. AMC Absolute Reality (M3U8 HLS)
        LiveChannel(
            id = "bhlB91ftosCPbTd2OMmP",
            name = "AMC Absolute Reality",
            description = "",
            streamUrl = "https://amc-absolutereality-1-us.plex.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/fYM014TZ/download-2.png",
            categories = listOf("other"),
            language = "en",
            encryptionType = "none"
        ),
        // 27. CAPE TOWN TV (M3U8 HLS)
        LiveChannel(
            id = "IoDxhM3b7jd3c7lU5vkI",
            name = "CAPE TOWN TV",
            description = "CAPETOWN TV",
            streamUrl = "https://cdn.freevisiontv.co.za/sttv/smil:ctv.stream.smil/chunklist_b480000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://capetowntv.org/wp-content/uploads/2023/01/cropped-Web-icon.png",
            categories = listOf("africa"),
            language = "en",
            encryptionType = "none"
        ),
        // 28. Anime X Hidive (M3U8 HLS)
        LiveChannel(
            id = "O56rdn4uSDFd8rMFQpgT",
            name = "Anime X Hidive",
            description = "",
            streamUrl = "https://amc-anime-x-hidive-1-us.tablo.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/d0hG0N1/hidive-logo.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 29. CNN (M3U8 HLS)
        LiveChannel(
            id = "DBSsfqWBXJRSnbCtaImk",
            name = "CNN",
            description = "NEWS",
            streamUrl = "https://turnerlive.warnermediacdn.com/hls/live/586495/cnngo/cnn_slate/VIDEO_0_3564000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://cdn-1.webcatalog.io/catalog/cnn-international/cnn-international-icon-unplated.png?v=1716987984587",
            categories = listOf("news"),
            language = "en",
            encryptionType = "none"
        ),
        // 30. GOSPER CARTOONS (M3U8 HLS)
        LiveChannel(
            id = "0ZPw7ulYy8ILOyos8ywF",
            name = "GOSPER CARTOONS",
            description = "KIDS",
            streamUrl = "https://stmv1.srvif.com/gospelcartoon/gospelcartoon/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://www.cxtv.com.br/img/Tvs/Logo/webp-l/f0a3c3c7b9a651e847d86dd71bbb5551.webp",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 31. BEK TV Sports West (M3U8 HLS)
        LiveChannel(
            id = "JgV8VrShS7f1hXJvQiUn",
            name = "BEK TV Sports West",
            description = "",
            streamUrl = "https://cdn3.wowza.com/5/ZWQ1K2NYTmpFbGsr/BEK-WOWZA-1/smil:BEKPRIMEW.smil/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRouVhLTmPWd6crbON-IgQpS1fBerasj88W1Npd06GUfA&s=10",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "none"
        ),
        // 32. WWE (M3U8 HLS)
        LiveChannel(
            id = "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2",
            name = "WWE",
            description = "",
            streamUrl = "https://dpv6ceilvhyrw.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-7ufe851j0vqbg/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/zW4Jvp6z/WWE-official-logo-svg.png",
            categories = listOf("sport", "entertainment"),
            language = "en",
            encryptionType = "none",
            featured = true
        ),
        // 33. CBEEBIES (M3U8 HLS)
        LiveChannel(
            id = "fbc3ebe6-29c5-418a-89cd-3aa17a05e337",
            name = "CBEEBIES",
            description = "",
            streamUrl = "https://cdn4.skygo.mn/live/disk1/Cbeebies/HLSv3-FTA/Cbeebies.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/FLXj9vtv/cbeebies-cbbc-television-channel-itv3-apps.jpg",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 34. SABC NEWS (M3U8 HLS)
        LiveChannel(
            id = "sVQCJNWwkyHclXVuCPpL",
            name = "SABC NEWS",
            description = "INDEPENDENT. IMPARTIAL",
            streamUrl = "https://sabconetanw.cdn.mangomolo.com/news/smil:news.stream.smil/chunklist_b250000_t64MjQwcA==.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://yt3.googleusercontent.com/x5Bgc7UBYZvOHvp3UG91lZbV6ND0YCOi2a026vLPDGIU5GF2qp5JnHnCeLyPegWhEjM5njpN=s900-c-k-c0x00ffffff-no-rj",
            categories = listOf("news"),
            language = "en",
            encryptionType = "none"
        ),
        // 35. SOWETO TV (M3U8 HLS)
        LiveChannel(
            id = "W7mBoCT7HiRMU5rHVL4J",
            name = "SOWETO TV",
            description = "HI",
            streamUrl = "https://cdn.freevisiontv.co.za/sttv/smil:soweto.stream.smil/chunklist_b480000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTHIRfIk1qiekejwXNifTZCXGm0hS1cLmxiqg&s",
            categories = listOf("africa"),
            language = "en",
            encryptionType = "none"
        ),
        // 36. WILD (M3U8 HLS)
        LiveChannel(
            id = "nTlfO9ki3eZrGmTmS8am",
            name = "WILD",
            description = "TELEVISION NETWORK",
            streamUrl = "https://dfhsahpa45kk2.cloudfront.net/scheduler/scheduleMaster/476/variant/22100055.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://yt3.googleusercontent.com/ZBZVBI42wYc6U1UTNnIkdjrdtB1WKUeAXDhbTwvDk8zjbeGIvMmdxAHOtzOzC75wHtITnqvSGw=s900-c-k-c0x00ffffff-no-rj",
            categories = listOf("documentary"),
            language = "en",
            encryptionType = "none"
        )
    )

    private val _liveChannelsFlow = MutableStateFlow(channels)
    val liveChannelsFlow: StateFlow<List<LiveChannel>> = _liveChannelsFlow.asStateFlow()

    private val homepageChannelIds = listOf(
        "R17JUvbCEzu2eTbjnE74",                 // Azam Sports 1 HD
        "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f", // Azam Sports 2 HD
        "1c976127-e8a4-4bd6-8e73-5da0edce369b", // Azam Sports 3 HD
        "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c", // Azam Sports 4 HD
        "c405ae74-c4c5-4842-9f26-130ce380b307", // Azam One
        "008ffe6e-a30f-4ed1-9ddb-4033dde18576", // Azam Two
        "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e", // Sinema Zetu
        "azam_xtra_hd_14",                      // Azam Xtra HD
        "azam_movies_hd_15",                    // Azam Movies HD
        "azam_clouds_tv_16",                    // Clouds TV HD (Azam TV)
        "azam_itv_tz_17",                       // ITV Tanzania HD (Azam TV)
        "d7b415f7-d024-4c7f-a570-653367e8dc5c", // UTV
        "4f36f2d1-ff7f-467b-b4f2-6f303263f28b", // KIX
        "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2"  // WWE
    )

    /**
     * Curated Live TV channels for the Homepage, including the expanded Azam TV bouquet.
     */
    val homePageFeaturedChannels: List<LiveChannel>
        get() {
            val source = _liveChannelsFlow.value
            return homepageChannelIds.mapNotNull { targetId ->
                source.find { it.id == targetId } ?: channels.find { it.id == targetId }
            }
        }

    /**
     * All 17 Azam TV priority channels placed first for the Live TV screen and slider.
     */
    val azamPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value.filter { it.isAzamPriority }.ifEmpty { channels.take(17) }

    /**
     * All Tanzania Live TV channels (including Azam bouquet + Tanzania Firestore channels).
     */
    val tanzaniaPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value
            .filter { it.isTanzaniaChannel && it.enabled && it.published }
            .sortedByDescending { it.priorityTier }

    /**
     * Updates the active CDN authorization token and edge host from a JSON payload:
     * `{"token": "...", "exp": 1790412582, "cdnHost": "https://cdnedgch2.azamtvltd.co.tz", "source": "cache"}`
     */
    fun updateCdnAuthorizationToken(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            val newToken = obj.optString("token", "").trim()
            val newHost = obj.optString("cdnHost", "").trim().removeSuffix("/")
            val newExp = obj.optLong("exp", AZAM_CDN_EXP)
            val newSource = obj.optString("source", AZAM_CDN_SOURCE)
            if (newToken.isNotEmpty()) {
                AZAM_CDN_TOKEN = newToken
                if (newHost.startsWith("http", ignoreCase = true)) {
                    AZAM_CDN_HOST = newHost
                }
                AZAM_CDN_EXP = newExp
                AZAM_CDN_SOURCE = newSource
                _liveChannelsFlow.value = _liveChannelsFlow.value.map { ch ->
                    ch.copy(streamUrl = normalizeDashStreamUrl(ch.streamUrl))
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun mergeFirebaseChannelsWithAzamPriority(remoteChannels: List<LiveChannel>) {
        val activeRemote = remoteChannels.filter { it.enabled && it.published }.map { rem ->
            val matchingLocal = channels.find {
                it.id == rem.id || it.name.equals(rem.name, ignoreCase = true)
            }
            val mergedKeys = if (rem.clearKeys.isNotEmpty()) {
                rem.clearKeys
            } else {
                matchingLocal?.clearKeys.orEmpty()
            }
            val normalizedUrl = normalizeDashStreamUrl(rem.streamUrl)
            val isDash = normalizedUrl.contains(".mpd", ignoreCase = true)
            rem.copy(
                streamUrl = normalizedUrl,
                streamFormat = if (isDash) "dash" else rem.streamFormat,
                encryptionType = if (mergedKeys.isNotEmpty()) "clearkey" else rem.encryptionType,
                clearKeys = mergedKeys
            )
        }
        val azamFirst = channels.take(17).map { ch ->
            ch.copy(streamUrl = normalizeDashStreamUrl(ch.streamUrl))
        }
        val remainingLocal = channels.drop(17).map { ch ->
            ch.copy(streamUrl = normalizeDashStreamUrl(ch.streamUrl))
        }
        val newRemote = activeRemote.filter { rem ->
            azamFirst.none { it.id == rem.id || it.name.equals(rem.name, ignoreCase = true) }
        }
        val combinedRemaining = (newRemote + remainingLocal.filter { loc ->
            newRemote.none { it.id == loc.id || it.name.equals(loc.name, ignoreCase = true) }
        }).sortedByDescending { it.priorityTier }

        // Strictly order: 17 Azam priority channels -> Tanzania Live TV -> Featured -> Other Live TV
        _liveChannelsFlow.value = azamFirst + combinedRemaining
    }

    /**
     * Normalizes Azam TV & CDN stream URLs (`.mpd` manifests and `.mp4` / `.m4s` CDN streams/segments):
     * - Rewrites `https://cdnblncr.azamtvltd.co.tz` to [AZAM_CDN_HOST] (`https://cdnedgch2.azamtvltd.co.tz`)
     * - Attaches `?cdntoken=[AZAM_CDN_TOKEN]` to Azam TV `.mpd` and `.mp4` CDN links
     * - Leaves external `.m3u8` HLS links untouched unless they use `azamtvltd.co.tz` or `cdntoken=`.
     */
    fun normalizeDashStreamUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return trimmed

        val rewrittenHostUrl = trimmed
            .replace("http://cdnblncr.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)
            .replace("https://cdnblncr.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)
            .replace("http://cdnedgch2.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)
            .replace("https://cdnedgch2.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)

        val basePath = rewrittenHostUrl.substringBefore("?")
        val existingQuery = rewrittenHostUrl.substringAfter("?", "")

        val isAzamCdn = basePath.contains("azamtvltd.co.tz", ignoreCase = true)
        val hasCdnTokenParam = existingQuery.contains("cdntoken=", ignoreCase = true)
        val isCdnMp4OrMpd = isAzamCdn ||
                hasCdnTokenParam ||
                (basePath.endsWith(".mpd", ignoreCase = true) && isAzamCdn) ||
                (basePath.endsWith(".mp4", ignoreCase = true) && isAzamCdn)

        if (!isCdnMp4OrMpd) {
            return rewrittenHostUrl
        }

        val otherParams = existingQuery
            .split("&")
            .filter { it.isNotBlank() && !it.startsWith("cdntoken=", ignoreCase = true) }

        val finalQuery = (otherParams + "cdntoken=$AZAM_CDN_TOKEN").joinToString("&")
        return "$basePath?$finalQuery"
    }

    fun getChannelById(id: String): LiveChannel? =
        _liveChannelsFlow.value.find { it.id == id } ?: channels.find { it.id == id }

    fun filterChannels(query: String, category: String): List<LiveChannel> {
        val trimmed = query.trim()
        val sourceList = _liveChannelsFlow.value
        val filtered = sourceList.filter { channel ->
            val matchesCategory = when (category.lowercase()) {
                "all" -> true
                "azam tv", "azam" -> channel.isAzamPriority
                "sports" -> channel.categories.any { it.equals("sport", ignoreCase = true) || it.equals("sports", ignoreCase = true) }
                "entertainment" -> channel.categories.any { it.equals("entertainment", ignoreCase = true) }
                "movies" -> channel.categories.any { it.equals("movies", ignoreCase = true) || it.equals("movie", ignoreCase = true) }
                "kids" -> channel.categories.any { it.equals("kids", ignoreCase = true) || it.equals("kid", ignoreCase = true) }
                "news" -> channel.categories.any { it.equals("news", ignoreCase = true) }
                "music" -> channel.categories.any { it.equals("music", ignoreCase = true) }
                "africa" -> channel.categories.any { it.equals("africa", ignoreCase = true) }
                "tanzania" -> channel.isTanzaniaChannel
                "documentary" -> channel.categories.any { it.equals("documentary", ignoreCase = true) }
                "other" -> channel.categories.isEmpty() || channel.categories.any { it.equals("other", ignoreCase = true) }
                else -> channel.categories.any { it.equals(category, ignoreCase = true) }
            }

            val matchesName = trimmed.isEmpty() ||
                    channel.name.contains(trimmed, ignoreCase = true) ||
                    channel.country.contains(trimmed, ignoreCase = true)

            matchesCategory && matchesName && channel.enabled && channel.published
        }

        // Prioritize Azam TV channels first (3), then Tanzania channels (2), then featured (1), then other channels (0)
        return filtered.sortedByDescending { it.priorityTier }
    }
}
