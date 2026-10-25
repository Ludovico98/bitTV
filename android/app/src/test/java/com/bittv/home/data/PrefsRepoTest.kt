package com.bittv.home.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrefsRepoTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(file: File, scope: CoroutineScope) =
        PrefsRepo(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun defaultsMatchSpec() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val p = repo(File(tmp.root, "a.preferences_pb"), scope).prefs.first()
        assertEquals(Svc.entries.toSet() - Svc.PARAMOUNT, p.subs)
        assertEquals(listOf("longtide", "loworbit", "grandeur", "commissario", "static"), p.watchLater)
        assertTrue(p.animations)
        assertTrue(p.ambient)
        assertNull(p.voice)
        assertFalse(p.overscanGuides)
        assertEquals(Prefs(), p)
        scope.cancel()
    }

    @Test
    fun toggleWatchLaterAddsAtFrontThenRemoves() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val r = repo(File(tmp.root, "b.preferences_pb"), scope)
        assertTrue(r.toggleWatchLater("epidemic"))
        assertEquals("epidemic", r.prefs.first().watchLater.first())
        assertFalse(r.toggleWatchLater("epidemic"))
        assertFalse("epidemic" in r.prefs.first().watchLater)
        scope.cancel()
    }

    @Test
    fun settingsSurviveNewRepoInstance() = runTest {
        val file = File(tmp.root, "c.preferences_pb")
        val s1 = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val r1 = repo(file, s1)
        r1.setAnimations(false)
        r1.toggleSub(Svc.PARAMOUNT)
        r1.setVoice("UK English Female")
        r1.clearWatchLater()
        s1.cancel()
        val s2 = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val p = repo(file, s2).prefs.first()
        assertFalse(p.animations)
        assertTrue(Svc.PARAMOUNT in p.subs)
        assertEquals("UK English Female", p.voice)
        assertEquals(emptyList<String>(), p.watchLater)
        s2.cancel()
    }
}
