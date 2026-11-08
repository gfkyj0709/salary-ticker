package com.yj.salaryticker.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stitching tests with a synthetic "screen" over a long document.
 * 긴 문서 위의 가짜 화면으로 이어 붙이기 검증.
 */
class StitcherTest {

    // Long document: each row unique, with some blank gaps / 긴 문서 (빈 줄 섞임)
    private val doc = LongArray(5000) { if (it % 37 in 10..14) Stitcher.BLANK else it * 7919L + 13 }

    /**
     * Build a frame: fixed header + doc window + fixed footer.
     * 화면 만들기: 고정 상단 + 문서 일부 + 고정 하단.
     *
     * @return 행 해시 배열
     */
    private fun frame(scroll: Int, header: Int = 120, content: Int = 1800, footer: Int = 150): LongArray =
        LongArray(header) { -1000L - it } + LongArray(content) { doc[scroll + it] } + LongArray(footer) { -5000L - it }

    /** 고정 상단·하단 감지. */
    @Test fun detectsStaticBars() {
        val a = frame(0); val b = frame(700)
        assertEquals(120, Stitcher.staticTop(a, b))
        assertEquals(150, Stitcher.staticBottom(a, b))
    }

    /** 스크롤 거리 정확히 찾기. */
    @Test fun findsShift() {
        val a = frame(100).copyOfRange(120, 1920); val b = frame(830).copyOfRange(120, 1920)
        val m = Stitcher.findShift(a, b, expected = 800)
        assertEquals(730, m.shift)
        assertTrue(m.score > 0.99)
    }

    /** 끝에 도달하면 이동 0. */
    @Test fun detectsEnd() {
        val a = frame(3000).copyOfRange(120, 1920)
        assertEquals(0, Stitcher.findShift(a, a.copyOf(), expected = 800).shift)
    }

    /** 관계없는 화면이면 낮은 점수. */
    @Test fun rejectsUnrelated() {
        val a = frame(0).copyOfRange(120, 1920)
        val b = LongArray(1800) { 999_999_999L + it }
        assertTrue(Stitcher.findShift(a, b, expected = 800).score < 0.1)
    }
}
