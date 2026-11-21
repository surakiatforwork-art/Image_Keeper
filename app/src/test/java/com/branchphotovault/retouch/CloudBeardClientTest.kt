package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test

class CloudBeardClientTest {
    @Test fun validAsyncSubmissionAndCompletion() {
        assertEquals("job_0123",
            CloudBeardClient.parseTaskId("""{"task_id":"job_0123","error_detail":{"code":""}}"""))
        assertNull(CloudBeardClient.parseResult("""{"task_status":0,"error_detail":{"code":""}}"""))
        assertNull(CloudBeardClient.parseResult("""{"task_status":1,"error_detail":{"code":""}}"""))
        assertEquals("https://assets.example.org/finished.jpg",
            CloudBeardClient.parseResult(
                """{"task_status":2,"data":{"result_urls":["https://assets.example.org/finished.jpg"]},"error_detail":{"code":""}}"""
            ))
    }

    @Test fun rejectsMissingJobIdAndServerErrors() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudBeardClient.parseTaskId("""{"task_id":""}""")
        }
        assertThrows(java.io.IOException::class.java) {
            CloudBeardClient.parseTaskId("""{"task_id":"x","error_detail":{"code":"102","message":"secret"}}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudBeardClient.parseResult("""{"task_status":2,"data":{"result_urls":[]}}""")
        }
    }

    @Test fun rejectsUntrustedResultLocations() {
        listOf(
            "http://assets.example.org/image.png",
            "https://localhost/image.png",
            "https://127.0.0.1/image.png",
            "https://192.168.1.5/image.png",
            "https://10.0.0.1/private",
            "https://images.internal/image.png",
            "file:///sdcard/test.png"
        ).forEach { url ->
            assertThrows("Accepted unsafe URL: $url",Exception::class.java) {
                CloudBeardClient.validateResultUrl(url)
            }
        }
    }

    @Test fun querySuccessCannotSilentlyAcceptUnknownStatus() {
        assertThrows(IllegalStateException::class.java) {
            CloudBeardClient.parseResult("""{"task_status":7}""")
        }
    }
}
