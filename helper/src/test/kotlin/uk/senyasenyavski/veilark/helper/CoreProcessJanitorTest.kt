package uk.senyasenyavski.veilark.helper

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreProcessJanitorTest {
  @Test
  fun `current user's process is eligible for orphan recovery`() {
    assertTrue(CoreProcessJanitor.isOwnedByCurrentUser("DOMAIN\\alice", "DOMAIN\\alice"))
    assertTrue(CoreProcessJanitor.isOwnedByCurrentUser("DOMAIN\\Alice", "domain\\ALICE"))
  }

  @Test
  fun `another user's process is never eligible for orphan recovery`() {
    assertFalse(CoreProcessJanitor.isOwnedByCurrentUser("DOMAIN\\alice", "DOMAIN\\bob"))
    assertFalse(CoreProcessJanitor.isOwnedByCurrentUser("DOMAIN\\alice", "OTHER\\alice"))
  }

  @Test
  fun `missing process owner fails closed`() {
    assertFalse(CoreProcessJanitor.isOwnedByCurrentUser(null, "DOMAIN\\alice"))
    assertFalse(CoreProcessJanitor.isOwnedByCurrentUser("DOMAIN\\alice", null))
    assertFalse(CoreProcessJanitor.isOwnedByCurrentUser("", ""))
  }
}
