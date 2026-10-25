package com.bittv.home.ui

import com.bittv.home.ui.components.Motion
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    // LocalMotion is a static composition local: a new-but-equal value must not invalidate the whole tree
    @Test fun motionWithTheSameSettingIsEqual() = assertEquals(Motion(true), Motion(true))
}
