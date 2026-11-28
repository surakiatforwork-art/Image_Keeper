package com.branchphotovault

import com.branchphotovault.data.update.GithubReleaseUpdater
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialUpdateVersionTest {
    @Test fun detectsNewerAndOlderStableReleases() {
        assertEquals(1, GithubReleaseUpdater.compareVersions("v1.5.0", "1.4.3"))
        assertEquals(-1, GithubReleaseUpdater.compareVersions("v1.4.1", "1.4.3"))
        assertEquals(0, GithubReleaseUpdater.compareVersions("v1.4.3", "1.4.3"))
    }

    @Test fun comparesNumericPartsNotStrings() {
        assertEquals(1, GithubReleaseUpdater.compareVersions("v1.4.10", "1.4.9"))
        assertEquals(1, GithubReleaseUpdater.compareVersions("v2.0.0", "1.99.99"))
        assertEquals(-1, GithubReleaseUpdater.compareVersions("v1.3.99", "1.4.0"))
    }

    @Test fun stableSupersedesSameBaseBeta() {
        assertEquals(1, GithubReleaseUpdater.compareVersions("v1.4.9", "1.4.9-beta.1"))
        assertEquals(-1, GithubReleaseUpdater.compareVersions("v1.4.9-beta.1", "1.4.9"))
        assertEquals(1, GithubReleaseUpdater.compareVersions("v1.4.10", "1.4.9-beta.1"))
        assertEquals(0, GithubReleaseUpdater.compareVersions("v1.4.9", "1.4.9+build.66"))
    }

    @Test fun rejectsUnknownVersionSyntaxWithoutInventingUpdates() {
        assertEquals(0, GithubReleaseUpdater.compareVersions("bad-tag", "1.4.3"))
        assertEquals(0, GithubReleaseUpdater.compareVersions("v1.4.3", "invalid"))
    }
}
