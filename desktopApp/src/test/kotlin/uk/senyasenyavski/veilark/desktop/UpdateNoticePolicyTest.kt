package uk.senyasenyavski.veilark.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateNoticePolicyTest {
  @Test fun noticeIsDeduplicatedAcrossRestarts() {
    var saved = 320
    val first = UpdateNoticePolicy({ saved }, { saved = it })
    assertFalse(first.shouldNotify(320))
    assertTrue(first.shouldNotify(321))
    first.markAttempted(321)
    assertFalse(first.shouldNotify(321))
    val restarted = UpdateNoticePolicy({ saved }, { saved = it })
    assertFalse(restarted.shouldNotify(321))
    assertTrue(restarted.shouldNotify(322))
  }
}
