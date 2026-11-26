package com.stube.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

val targetAppId: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId instrumentation argument is missing")

private fun MacrobenchmarkScope.bench(op: String) {
    device.executeShellCommand("am start -W -n $targetAppId/com.nova.browser.BenchActivity --es op $op")
}

/** Same data and permission state on every iteration; the app is left stopped. */
fun MacrobenchmarkScope.resetData() {
    killProcess()
    device.executeShellCommand("pm grant $targetAppId android.permission.POST_NOTIFICATIONS")
    bench("seed")
    killProcess()
}

/** Opens a panel and fails if its seeded rows ([marker]) never appear. */
fun MacrobenchmarkScope.openPanel(kind: String, marker: String?) {
    bench(kind)
    if (marker != null) check(device.wait(Until.hasObject(By.pkg(targetAppId).textStartsWith(marker)), 10_000)) {
        "Panel $kind did not show seeded data"
    }
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollList() {
    for (direction in listOf(Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.UP, Direction.UP)) {
        // Re-query each time (nodes go stale); the tallest scrollable is the list, not the chip row
        val list = device.findObjects(By.pkg(targetAppId).scrollable(true)).maxByOrNull { it.visibleBounds.height() }
            ?: error("No scrollable list")
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(direction)
        device.waitForIdle()
    }
}
