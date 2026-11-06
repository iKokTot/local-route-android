package org.localroute.android.updates

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val certificate = "a".repeat(64)
    private val update = AppUpdate(41, "0.24.17", "org.localroute.community.preview",26,certificate,
        "${UpdatePolicy.REPOSITORY}/releases/tag/android-v0.24.17", "Исправления", "b".repeat(64))
    private fun accepts(value: AppUpdate = update) = UpdatePolicy.compatible(value,40,"org.localroute.community.preview",36,setOf(certificate))
    @Test fun newerCompatibleReleaseIsAccepted() { assertTrue(accepts()) }
    @Test fun oldAndEqualVersionsNeverPrompt() { assertFalse(accepts(update.copy(versionCode=40)));assertFalse(accepts(update.copy(versionCode=39))) }
    @Test fun wrongPackageAndCertificateNeverPrompt() {
        assertFalse(accepts(update.copy(packageName="org.localroute.community")))
        assertFalse(accepts(update.copy(certificate="c".repeat(64))))
        assertFalse(UpdatePolicy.compatible(update,40,update.packageName,36,setOf(certificate,"c".repeat(64))))
    }
    @Test fun incompatibleAndroidIsRejected() { assertFalse(accepts(update.copy(minSdk=37))) }
    @Test fun malformedVersionAndHashAreRejected() {
        assertFalse(accepts(update.copy(versionName="<script>"))); assertFalse(accepts(update.copy(sha256="invalid")))
    }
    @Test fun externalOrAmbiguousUrlsAreRejected() {
        listOf("http://github.com/iKokTot/local-route-android/releases/tag/v1", "https://github.com.evil.org/iKokTot/local-route-android/releases/tag/v1",
            "https://evil.org/", "${update.releaseUrl}?url=evil", "${update.releaseUrl}/../evil", "https://user@github.com/iKokTot/local-route-android/releases/tag/v1")
            .forEach { assertFalse(it,accepts(update.copy(releaseUrl=it))) }
    }
    @Test fun intervalHandlesOfflineRetryAndClockChanges() {
        assertTrue(UpdatePolicy.due(100,0));assertFalse(UpdatePolicy.due(101,100))
        assertFalse(UpdatePolicy.due(UpdatePolicy.INTERVAL,100));assertTrue(UpdatePolicy.due(UpdatePolicy.INTERVAL+100,100))
        assertTrue(UpdatePolicy.due(99,100))
    }
}
