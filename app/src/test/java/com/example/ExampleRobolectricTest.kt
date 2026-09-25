package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Neli TV", appName)
    }

    @Test
    fun `firestore movie series episode and tvChannel JSON documents parse properly`() {
        val movieDocJson = """
            {
              "documents": [
                {
                  "name": "projects/neli-tv/databases/(default)/documents/movies/mov_1788911132603_wdgav",
                  "fields": {
                    "id": {"stringValue": "mov_1788911132603_wdgav"},
                    "title": {"stringValue": "Never a Thief"},
                    "originalTitle": {"stringValue": "缉盗"},
                    "originalLanguage": {"stringValue": "zh"},
                    "narrated": {"booleanValue": true},
                    "narrationLanguage": {"stringValue": "Swahili"},
                    "downloadEnabled": {"booleanValue": true},
                    "featured": {"booleanValue": true},
                    "published": {"booleanValue": true},
                    "rating": {"doubleValue": 5.3},
                    "runtime": {"integerValue": "120"},
                    "year": {"integerValue": "2025"},
                    "posterPath": {"stringValue": "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg"},
                    "backdropPath": {"stringValue": "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg"},
                    "streamUrl": {"stringValue": "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4"},
                    "genres": {"arrayValue": {"values": [{"stringValue": "Crime"}]}}
                  }
                }
              ]
            }
        """.trimIndent()

        val count = MediaContentRepository.parseFirestoreCollections(
            moviesJson = movieDocJson
        )
        assertEquals(1, count)
        val movie = MediaContentRepository.getMediaById("mov_1788911132603_wdgav")
        assertNotNull(movie)
        assertEquals("Never a Thief", movie!!.title)
        assertTrue(movie.narrated)
        assertEquals("Swahili", movie.narrationLanguage)
    }

    @Test
    fun `user signup with real name email and password automatically logs in and persists across login`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.NeliDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = db.mediaDao()

        val signUpRes = com.example.data.AuthRepository.signUpWithEmailAndPassword(
            context = context,
            dao = dao,
            realName = "Juma Bakari",
            email = "juma@nelitv.tz",
            password = "password123"
        )
        assertTrue(signUpRes.isSuccess)
        val registeredUser = signUpRes.getOrNull()
        assertNotNull(registeredUser)
        assertEquals("Juma Bakari", registeredUser!!.realName)
        assertTrue(registeredUser.isLoggedIn)

        // Log out and sign back in: real name and account data must not be lost
        dao.logoutAllUsers()
        val signInRes = com.example.data.AuthRepository.signInWithEmailAndPassword(
            context = context,
            dao = dao,
            email = "juma@nelitv.tz",
            password = "password123"
        )
        assertTrue(signInRes.isSuccess)
        assertEquals("Juma Bakari", signInRes.getOrNull()?.realName)
        db.close()
    }
}
