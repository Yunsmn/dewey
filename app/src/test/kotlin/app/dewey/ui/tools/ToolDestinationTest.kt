package app.dewey.ui.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolDestinationTest {

    @Test
    fun `every tool has its own route`() {
        // Two tools sharing a route would open the same screen from two rows,
        // silently, with nothing at compile time to catch it.
        val routes = ToolDestination.entries.map { it.route }

        assertThat(routes).containsNoDuplicates()
    }

    @Test
    fun `every tool route sits under the tools prefix`() {
        // The navigation bar marks the Tools tab active for any route that
        // starts with this, so a tool outside it would highlight the wrong tab.
        assertThat(ToolDestination.entries.map { it.route }).containsNoneIn(listOf("", "tools"))
        ToolDestination.entries.forEach { assertThat(it.route).startsWith("tools/") }
    }

    @Test
    fun `every group has at least one tool`() {
        // An empty group would render as a heading with nothing under it.
        ToolGroup.entries.forEach { group ->
            assertThat(ToolDestination.entries.filter { it.group == group }).isNotEmpty()
        }
    }
}
