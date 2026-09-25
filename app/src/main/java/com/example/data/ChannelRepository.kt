package com.example.data

import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChannelRepository {

    const val AZAM_CDN_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwMzc2NTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiOTdmYmY3NWU2ZWU2MmJkYSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.zW07ft5kqXHOkbPguYYGgrBwihIVJIjoGvhVqTN3cjjWZEp4NWJfpQUQyDYUkyuhNVKmLYSHxO5EwEegjYQ84A%3D%3D"

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
        // 1. AZAM SPORT 1
        LiveChannel(
            id = "R17JUvbCEzu2eTbjnE74",
            name = "Azam Sports 1 HD",
            description = "Mpira Live • Ligi Kuu & Kimataifa",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85")
        ),
        // 2. AZAM SPORT 2
        LiveChannel(
            id = "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f",
            name = "Azam Sports 2 HD",
            description = "Mpira Live • CAF & NBC Premier League",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport2/DASH/AzamSport2.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Y7Cj3Wtj/azam-sport-2-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("739e7499125b31cc9948da8057b84cf9" to "1b7d44d798c351acc02f33ddfbb7682a")
        ),
        // 3. AZAM SPORT 3
        LiveChannel(
            id = "1c976127-e8a4-4bd6-8e73-5da0edce369b",
            name = "Azam Sports 3 HD",
            description = "Mpira Live • Michezo ya Kimataifa",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport3/DASH/AzamSport3.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/2YfLQ545/1000221070.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("2f12d7b889de381a9fb5326ca3aa166d" to "51c2d733a54306fdf89acd4c9d4f6005")
        ),
        // 4. AZAM SPORT 4
        LiveChannel(
            id = "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c",
            name = "Azam Sports 4 HD",
            description = "Mpira Live • Vipindi vya Michezo",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport4/DASH/AzamSport4.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/SwtFQNsh/1000221063.jpg",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("fbcac74e355b3b5d9b6cbc56c7597dbf" to "5f980e872b378d88b46f656a806205c3")
        ),
        // 5. AZAM ONE
        LiveChannel(
            id = "c405ae74-c4c5-4842-9f26-130ce380b307",
            name = "Azam One",
            description = "Burudani na Filamu za Afrika Mashariki",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("b5cbe1bb5acf3c7f9995be428245cfcd" to "89f1188a11e5e000d4443eb27ca378e1")
        ),
        // 6. AZAM 2
        LiveChannel(
            id = "008ffe6e-a30f-4ed1-9ddb-4033dde18576",
            name = "Azam Two",
            description = "Tamthilia na Burudani Live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamTwo/DASH/AzamTwo.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("18f515fd536b3e728e9844e77d9f0fa8" to "c96dfd9b65560f87a1a95b8aeda0432d")
        ),
        // 7. ZBC 2
        LiveChannel(
            id = "b502217f-a9d0-4aef-99e6-8a784adedc65",
            name = "ZBC2",
            description = "Zanzibar Broadcasting Corporation 2",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC2/DASH/ZBC2.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.imgur.com/5HqOXH0.jpeg",
            categories = listOf("tanzania", "entertainment"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("f38b03549d023f3a883997328537bf4d" to "90f9d2d9cc26009c437905692ad70730")
        ),
        // 8. SINEMA ZETU
        LiveChannel(
            id = "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e",
            name = "Sinema Zetu",
            description = "Filamu za Kiswahili 24/7",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("d628ae37a8f0336b970f250d9699461e" to "1194c3d60bb494aabe9114ca46c2738e")
        ),
        // 9. ZBC
        LiveChannel(
            id = "101e9c3f-de90-47fb-a69c-342be8a0bb80",
            name = "ZBC",
            description = "Zanzibar Broadcasting Corporation",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC/DASH/ZBC.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/xtynWQsN/1000221078.png",
            categories = listOf("tanzania", "news", "other"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("e91fec140bc5316f919b4dc9c16287d7" to "79884fad0dbfcdc43d3e33c82a1f1cfa")
        ),
        // 10. KIX
        LiveChannel(
            id = "4f36f2d1-ff7f-467b-b4f2-6f303263f28b",
            name = "KIX",
            description = "Non-Stop Action Movies",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/KIXMovies/DASH/KIXMovies.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/ch6qGQT3/KIX-logo-svg.png",
            categories = listOf("movies"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("a7e155b282f33335ae8d553f169f443c" to "c3fdcfd5d509f1ed8550d76a525e34e5")
        ),
        // 11. UTV
        LiveChannel(
            id = "d7b415f7-d024-4c7f-a570-653367e8dc5c",
            name = "UTV",
            description = "Habari na Burudani",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/UTV/DASH/UTV.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("entertainment", "news", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894")
        ),
        // 12. Crown Tv
        LiveChannel(
            id = "bba104f4-f5ac-41c9-aa36-7af71aaa1993",
            name = "Crown Tv",
            description = "Muziki na Burudani Live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/CrownTv/DASH/CrownTv.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/GfWDtdQT/1000221075.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("e3eb6e0656ec3c22aa308aaa3f82c565" to "a7634d6defd2c255135095c45bc442fb")
        ),
        // 13. Wasafi Tv
        LiveChannel(
            id = "80e54146-1d9b-4c91-8f71-de0ea4866833",
            name = "Wasafi TV",
            description = "Burudani ya Kibabe",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=$AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("8714fe102679348e9c76cfd315dacaa0" to "a8b86ceda831061c13c7c4c67bd77f8e"),
            country = "Tanzania",
            featured = true
        ),
        // Firestore Tanzania Channel: POP Animation Network
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
        LiveChannel(
            id = "Vshw4JeUaRpzDcNdlzcn",
            name = "YAHOO FINANCE",
            description = "WATCH NOW",
            streamUrl = "https://d1ewctnvcwvvvu.cloudfront.net/240p-cc/index.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://images-cdn3.welcomesoftware.com/assets/yahoo+finance.jpg/Zz0yNWFjNTk0NjlkMmQxMWVmYjhlNjFlYTY2MTI5N2IyNg==?width=768&height=430",
            categories = listOf("news", "other"),
            language = "en",
            encryptionType = "none"
        ),
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
        LiveChannel(
            id = "bhlB91ftosCPbTd2OMmP",
            name = "AMC Absolute Reality",
            description = "",
            streamUrl = "https://amc-absolutereality-1-us.plex.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/fYM014TZ/download-2.png",
            categories = listOf("entertainment", "other"),
            language = "en",
            encryptionType = "none"
        ),
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
        LiveChannel(
            id = "O56rdn4uSDFd8rMFQpgT",
            name = "Anime X Hidive",
            description = "",
            streamUrl = "https://amc-anime-x-hidive-1-us.tablo.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/d0hG0N1/hidive-logo.png",
            categories = listOf("kids", "entertainment"),
            language = "en",
            encryptionType = "none"
        ),
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
        LiveChannel(
            id = "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2",
            name = "WWE",
            description = "",
            streamUrl = "https://dpv6ceilvhyrw.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-7ufe851j0vqbg/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/zW4Jvp6z/WWE-official-logo-svg.png",
            categories = listOf("sport", "entertainment"),
            language = "en",
            encryptionType = "none"
        ),
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
        "c405ae74-c4c5-4842-9f26-130ce380b307", // Azam One
        "008ffe6e-a30f-4ed1-9ddb-4033dde18576", // Azam Two
        "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e", // Sinema Zetu
        "4f36f2d1-ff7f-467b-b4f2-6f303263f28b", // KIX
        "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2"  // WWE
    )

    /**
     * Curated Live TV channels for the Homepage only:
     * Azam Sports 1, Azam Sports 2, Azam One, Azam Two, Sinema Zetu, KIX, and WWE.
     */
    val homePageFeaturedChannels: List<LiveChannel>
        get() {
            val source = _liveChannelsFlow.value
            return homepageChannelIds.mapNotNull { targetId ->
                source.find { it.id == targetId } ?: channels.find { it.id == targetId }
            }
        }

    /**
     * All 13 Azam TV priority channels placed first for the Live TV screen.
     */
    val azamPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value.filter { it.isAzamPriority }.ifEmpty { channels.take(13) }

    /**
     * All Tanzania Live TV channels (including Azam bouquet + Tanzania Firestore channels).
     */
    val tanzaniaPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value
            .filter { it.isTanzaniaChannel && it.enabled && it.published }
            .sortedByDescending { it.priorityTier }

    fun mergeFirebaseChannelsWithAzamPriority(remoteChannels: List<LiveChannel>) {
        val activeRemote = remoteChannels.filter { it.enabled && it.published }
        val azamFirst = channels.take(13)
        val remainingLocal = channels.drop(13)
        val newRemote = activeRemote.filter { rem ->
            azamFirst.none { it.id == rem.id || it.name.equals(rem.name, ignoreCase = true) }
        }
        val combinedRemaining = (newRemote + remainingLocal.filter { loc ->
            newRemote.none { it.id == loc.id || it.name.equals(loc.name, ignoreCase = true) }
        }).sortedByDescending { it.priorityTier }

        // Strictly order: 13 initial priority channels -> Tanzania Live TV -> Featured -> Other Live TV
        _liveChannelsFlow.value = azamFirst + combinedRemaining
    }

    /**
     * Normalizes any DASH segment URL (e.g. `.../live/eds/AzamSport1/DASH/AzamSport1-mp4a_...mp4?cdntoken=...`)
     * to its master `.mpd` manifest URL while preserving the query string.
     */
    fun normalizeDashStreamUrl(rawUrl: String): String {
        val pathPart = rawUrl.substringBefore("?")
        val queryPart = rawUrl.substringAfter("?", "")
        return if (pathPart.contains("/DASH/") && pathPart.endsWith(".mp4", ignoreCase = true)) {
            val dashDir = pathPart.substringBeforeLast("/")
            val fileName = pathPart.substringAfterLast("/")
            val streamBaseName = fileName.substringBefore("-").ifEmpty {
                dashDir.substringBeforeLast("/").substringAfterLast("/")
            }
            "$dashDir/$streamBaseName.mpd${if (queryPart.isNotEmpty()) "?$queryPart" else ""}"
        } else {
            rawUrl
        }
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
                    channel.description.contains(trimmed, ignoreCase = true) ||
                    channel.country.contains(trimmed, ignoreCase = true)

            matchesCategory && matchesName && channel.enabled && channel.published
        }

        // Prioritize initial Azam TV channels first (3), then Tanzania channels (2), then featured (1), then other channels (0)
        return filtered.sortedByDescending { it.priorityTier }
    }
}
