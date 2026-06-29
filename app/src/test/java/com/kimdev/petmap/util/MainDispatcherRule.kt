package com.kimdev.petmap.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Dispatchers.Main 을 테스트 디스패처로 교체하는 JUnit 룰.
 * viewModelScope(=Dispatchers.Main) 의 코루틴을 가상 시계로 제어하기 위해 사용.
 *
 * 사용: runTest(mainDispatcherRule.testDispatcher) { ... advanceUntilIdle() ... }
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
