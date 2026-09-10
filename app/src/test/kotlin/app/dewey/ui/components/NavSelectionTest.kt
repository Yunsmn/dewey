package app.dewey.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavSelectionTest {

    @Test
    fun `a tab is active on its own route`() {
        assertThat(isRouteSelected(current = "tools", tab = "tools")).isTrue()
    }

    @Test
    fun `a tab stays active on a screen opened from it`() {
        assertThat(isRouteSelected(current = "tools/merge", tab = "tools")).isTrue()
    }

    @Test
    fun `a route that merely starts with the same letters does not count`() {
        assertThat(isRouteSelected(current = "toolsettings", tab = "tools")).isFalse()
    }

    @Test
    fun `another tab is not active`() {
        assertThat(isRouteSelected(current = "tools/merge", tab = "library")).isFalse()
    }

    @Test
    fun `nothing is active before navigation has a route`() {
        assertThat(isRouteSelected(current = null, tab = "library")).isFalse()
    }
}
