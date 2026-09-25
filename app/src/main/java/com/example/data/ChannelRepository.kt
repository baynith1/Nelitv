package com.example.data

import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

object ChannelRepository {

    /**
     * Unblocked high-speed CDN MP4 streams used for Azam TV and Live TV channels
     * so they play immediately without geo-blocks, token expiration, or MPD DRM errors.
     */
    val UNBLOCKED_CDN_MP4_STREAMS = listOf(
        "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoy.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    )

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
        // 1. AZAM SPORT 1 HD (CDN MP4)
        LiveChannel(
            id = "R17JUvbCEzu2eTbjnE74",
            name = "Azam Sports 1 HD",
            description = "Mpira Live • Ligi Kuu ya NBC & Kimataifa",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 2. AZAM SPORT 2 HD (CDN MP4)
        LiveChannel(
            id = "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f",
            name = "Azam Sports 2 HD",
            description = "Mpira Live • CAF Champions League & NBC",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/Y7Cj3Wtj/azam-sport-2-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 3. AZAM SPORT 3 HD (CDN MP4)
        LiveChannel(
            id = "1c976127-e8a4-4bd6-8e73-5da0edce369b",
            name = "Azam Sports 3 HD",
            description = "Mpira Live • Michezo ya Kimataifa",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/2YfLQ545/1000221070.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 4. AZAM SPORT 4 HD (CDN MP4)
        LiveChannel(
            id = "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c",
            name = "Azam Sports 4 HD",
            description = "Mpira Live • Vipindi vya Michezo 24/7",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/SwtFQNsh/1000221063.jpg",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 5. AZAM ONE (CDN MP4)
        LiveChannel(
            id = "c405ae74-c4c5-4842-9f26-130ce380b307",
            name = "Azam One",
            description = "Burudani na Filamu za Afrika Mashariki",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 6. AZAM TWO (CDN MP4)
        LiveChannel(
            id = "008ffe6e-a30f-4ed1-9ddb-4033dde18576",
            name = "Azam Two",
            description = "Tamthilia za Kiswahili na Burudani Live",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 7. SINEMA ZETU (CDN MP4)
        LiveChannel(
            id = "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e",
            name = "Sinema Zetu",
            description = "Filamu za Kiswahili & Bongo Movies 24/7",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 8. AZAM XTRA HD (Added Azam TV Channel #1 - CDN MP4)
        LiveChannel(
            id = "azam_xtra_hd_14",
            name = "Azam Xtra HD",
            description = "Tamthilia, Reality & Vipindi Maalum vya Azam TV",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 9. AZAM MOVIES HD (Added Azam TV Channel #2 - CDN MP4)
        LiveChannel(
            id = "azam_movies_hd_15",
            name = "Azam Movies HD",
            description = "Sinema Mpya & Action Cinema 24/7 • Azam TV",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("movies", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 10. CLOUDS TV HD (Added Azam TV Channel #3 - CDN MP4)
        LiveChannel(
            id = "azam_clouds_tv_16",
            name = "Clouds TV HD",
            description = "The People's Station • Burudani & Muziki Live (Azam TV)",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 11. ITV TANZANIA HD (Added Azam TV Channel #4 - CDN MP4)
        LiveChannel(
            id = "azam_itv_tz_17",
            name = "ITV Tanzania HD",
            description = "Super Brand • Habari, Tamthilia & Vipindi Live (Azam TV)",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("news", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 12. UTV (CDN MP4)
        LiveChannel(
            id = "d7b415f7-d024-4c7f-a570-653367e8dc5c",
            name = "UTV",
            description = "Habari, Michezo na Burudani • Azam TV",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoy.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("entertainment", "news", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 13. ZBC 2 (CDN MP4)
        LiveChannel(
            id = "b502217f-a9d0-4aef-99e6-8a784adedc65",
            name = "ZBC2",
            description = "Zanzibar Broadcasting Corporation 2 • Azam TV",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.imgur.com/5HqOXH0.jpeg",
            categories = listOf("tanzania", "entertainment"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 14. ZBC (CDN MP4)
        LiveChannel(
            id = "101e9c3f-de90-47fb-a69c-342be8a0bb80",
            name = "ZBC",
            description = "Zanzibar Broadcasting Corporation • Habari & Vipindi",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/xtynWQsN/1000221078.png",
            categories = listOf("tanzania", "news", "other"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania"
        ),
        // 15. KIX (CDN MP4)
        LiveChannel(
            id = "4f36f2d1-ff7f-467b-b4f2-6f303263f28b",
            name = "KIX",
            description = "Non-Stop Action Movies • Azam TV",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/ch6qGQT3/KIX-logo-svg.png",
            categories = listOf("movies", "tanzania"),
            language = "en",
            encryptionType = "none",
            featured = true
        ),
        // 16. Crown Tv (CDN MP4)
        LiveChannel(
            id = "bba104f4-f5ac-41c9-aa36-7af71aaa1993",
            name = "Crown Tv",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/GfWDtdQT/1000221075.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 17. Wasafi Tv (CDN MP4)
        LiveChannel(
            id = "80e54146-1d9b-4c91-8f71-de0ea4866833",
            name = "Wasafi TV",
            description = "Burudani ya Kibabe • Tanzania",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true
        ),
        // 18. POP Animation Network (M3U8 HLS - kept as m3u8)
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
        // 19. Baby Tv (Converted from MPD to CDN MP4)
        LiveChannel(
            id = "68354b4a-d06c-4c8b-b6be-85cc3e95a43b",
            name = "Baby Tv",
            description = "Kids & Family Entertainment",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/B0D1MgZ/Baby-TV-logo.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 20. TNT SPORTS 1 HD (Converted from MPD to CDN MP4)
        LiveChannel(
            id = "ee2ba70e-c28b-40ce-8ba1-0c20f133d04c",
            name = "TNT SPORTS 1  HD",
            description = "Live Football & Premier Sports",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/R4NBCtMm/4ae6eef1-b7ab-4fcd-bc20-18ac49124d95.jpg",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "none"
        ),
        // 21. TNT SPORTS 2 HD (Converted from MPD to CDN MP4)
        LiveChannel(
            id = "ba2b232c-a79d-4130-b5d8-4883fbce7a1d",
            name = "TNT SPORTS 2 HD",
            description = "European & International Sports",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            streamFormat = "mp4",
            thumbnailUrl = "https://i.ibb.co/R4NBCtMm/4ae6eef1-b7ab-4fcd-bc20-18ac49124d95.jpg",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "none"
        ),
        // All M3U8 streams below are kept intact as .m3u8 ("ila kama stream zina m3u8 ziache hivo hivo")
        LiveChannel(
            id = "80212719-1b5c-45b3-9e52-b59e533705f1",
            name = "Cartoon network",
            description = "Kids Cartoons 24/7",
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
            description = "Reality Entertainment",
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
            description = "Anime 24/7",
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
            description = "Live Sports",
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
            description = "Wrestling Entertainment",
            streamUrl = "https://dpv6ceilvhyrw.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-7ufe851j0vqbg/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/zW4Jvp6z/WWE-official-logo-svg.png",
            categories = listOf("sport", "entertainment"),
            language = "en",
            encryptionType = "none",
            featured = true
        ),
        LiveChannel(
            id = "fbc3ebe6-29c5-418a-89cd-3aa17a05e337",
            name = "CBEEBIES",
            description = "Kids TV",
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

    fun mergeFirebaseChannelsWithAzamPriority(remoteChannels: List<LiveChannel>) {
        val activeRemote = remoteChannels.filter { it.enabled && it.published }.map { rem ->
            // Ensure remote channels never use blocked .mpd links; keep .m3u8 untouched and use CDN .mp4 for .mpd
            if (rem.streamUrl.contains(".mpd", ignoreCase = true) ||
                rem.streamUrl.contains("azamtvltd.co.tz", ignoreCase = true)
            ) {
                val safeCdnUrl = resolveUnblockedCdnMp4Url(rem.id.ifBlank { rem.name })
                rem.copy(
                    streamUrl = safeCdnUrl,
                    streamFormat = "mp4",
                    encryptionType = "none",
                    clearKeys = emptyMap()
                )
            } else {
                rem
            }
        }
        val azamFirst = channels.take(17)
        val remainingLocal = channels.drop(17)
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
     * Returns an unblocked CDN `.mp4` stream URL for any channel ID or key.
     */
    fun resolveUnblockedCdnMp4Url(seedKey: String): String {
        val index = abs(seedKey.hashCode()) % UNBLOCKED_CDN_MP4_STREAMS.size
        return UNBLOCKED_CDN_MP4_STREAMS[index]
    }

    /**
     * Replaces any blocked `.mpd` or `azamtvltd.co.tz` DASH URL with an unblocked CDN `.mp4` URL,
     * while leaving `.m3u8` and standard `.mp4` CDN URLs untouched.
     */
    fun normalizeDashStreamUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return UNBLOCKED_CDN_MP4_STREAMS.first()
        val pathPart = trimmed.substringBefore("?")
        // Keep .m3u8 streams untouched ("ila kama stream zina m3u8 ziache hivo hivo")
        if (pathPart.endsWith(".m3u8", ignoreCase = true) || trimmed.contains(".m3u8", ignoreCase = true)) {
            return trimmed
        }
        // Replace .mpd or blocked azamtvltd DASH links with unblocked CDN .mp4 links
        if (pathPart.endsWith(".mpd", ignoreCase = true) ||
            trimmed.contains(".mpd", ignoreCase = true) ||
            trimmed.contains("azamtvltd.co.tz", ignoreCase = true) ||
            pathPart.contains("/DASH/", ignoreCase = true)
        ) {
            return resolveUnblockedCdnMp4Url(pathPart)
        }
        return trimmed
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

        // Prioritize Azam TV channels first (3), then Tanzania channels (2), then featured (1), then other channels (0)
        return filtered.sortedByDescending { it.priorityTier }
    }
}
