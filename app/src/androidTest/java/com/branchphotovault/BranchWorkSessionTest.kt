package com.branchphotovault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.branchphotovault.navigation.BranchWorkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchWorkSessionTest {
    @Test
    fun remembersBranchAcrossInstancesAndClearsOnExplicitExit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = BranchWorkSession(context)
        val prior = session.lastBranch()
        val account = "SESSION_TEST"
        val branch = "TEST_" + System.nanoTime()
        try {
            session.rememberBranch(account, branch)
            assertEquals(account to branch, BranchWorkSession(context).lastBranch())
            session.clearBranch()
            assertNull(BranchWorkSession(context).lastBranch())
        } finally {
            if (prior == null) session.clearBranch()
            else session.rememberBranch(prior.first, prior.second)
        }
    }
}
