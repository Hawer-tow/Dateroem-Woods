package com.dateroem.woodsales.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The icons are built from path text the first time a screen draws one, so
// a typo in a shape would only show up as a crash on that screen. This
// builds every one of them up front.
class AppIconsTest {

    @Test
    fun everyIconBuildsWithDrawableShapes() {
        val getters = AppIcons::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.returnType == ImageVector::class.java && it.parameterCount == 0 }
        // 57 since 24 October 2026; update it when an icon is added.
        assertEquals("number of icons", 57, getters.size)
        for (getter in getters) {
            val icon = getter.invoke(AppIcons) as ImageVector
            val paths = icon.root.filterIsInstance<VectorPath>()
            assertTrue("${icon.name} has no shapes", paths.isNotEmpty())
            for (path in paths) {
                assertTrue("${icon.name} has an empty shape", path.pathData.isNotEmpty())
            }
            assertEquals("${icon.name} viewport", 24f, icon.viewportWidth)
        }
    }
}
