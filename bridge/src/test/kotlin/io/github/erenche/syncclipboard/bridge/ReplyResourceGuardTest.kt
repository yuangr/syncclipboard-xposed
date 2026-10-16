package io.github.erenche.syncclipboard.bridge

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class ReplyResourceGuardTest {
    private val released = mutableListOf<Any>()
    private val guard = ReplyResourceGuard<Any> { released.add(it) }

    @Test fun successfulClaimTransfersOwnership() {
        val value = Any()
        guard.offer(value) { true }
        assertSame(value, guard.claim(value))
        guard.close()
        assertTrue(released.isEmpty())
    }
    @Test fun cancellationAfterReplyBeforeClaimReleasesValue() {
        val value = Any()
        guard.offer(value) { true }
        guard.close()
        assertEquals(listOf(value), released)
    }
    @Test fun lateReplyIsReleasedWithoutCompletion() {
        val value = Any()
        guard.close()
        guard.offer(value) { fail("Must not complete"); true }
        assertEquals(listOf(value), released)
    }
    @Test fun rejectedCompletionReleasesValue() {
        val value = Any()
        guard.offer(value) { false }
        guard.close()
        assertEquals(listOf(value), released)
    }
    @Test fun duplicateReplyDoesNotOverwritePendingResource() {
        val value = Any()
        val duplicate = Any()
        guard.offer(value) { true }
        guard.offer(duplicate) { fail("Must not complete twice"); true }
        assertSame(value, guard.claim(value))
        guard.close()
        assertEquals(listOf(duplicate), released)
    }
    @Test fun closeIsIdempotent() {
        val value = Any()
        guard.offer(value) { true }
        guard.close()
        guard.close()
        assertEquals(listOf(value), released)
    }
    @Test fun inlineResumeCanClaimBeforeCompleteReturns() {
        val value = Any()
        guard.offer(value) { assertSame(value, guard.claim(value)); true }
        guard.close()
        assertTrue(released.isEmpty())
    }
    @Test fun racingCancellationAndReplyReleaseExactlyOnce() {
        repeat(100) {
            val disposed = mutableListOf<Any>()
            val owner = ReplyResourceGuard<Any> { disposed.add(it) }
            val value = Any()
            val gate = CountDownLatch(1)
            val sender = thread { gate.await(); owner.offer(value) { true } }
            val cancel = thread { gate.await(); owner.close() }
            gate.countDown()
            sender.join()
            cancel.join()
            assertEquals(listOf(value), disposed)
        }
    }
}
