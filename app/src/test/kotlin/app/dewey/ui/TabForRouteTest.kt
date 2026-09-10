package app.dewey.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabForRouteTest {

    @Test
    fun `a tab's own route selects that tab`() {
        assertThat(tabForRoute(Routes.HOME)).isEqualTo(Routes.HOME)
        assertThat(tabForRoute(Routes.NOTES)).isEqualTo(Routes.NOTES)
        assertThat(tabForRoute(Routes.ME)).isEqualTo(Routes.ME)
    }

    @Test
    fun `a tool screen keeps Home selected, since Home's grid is where it was opened`() {
        assertThat(tabForRoute("tools/merge")).isEqualTo(Routes.HOME)
        assertThat(tabForRoute("tools/scan")).isEqualTo(Routes.HOME)
    }

    @Test
    fun `documents with or without a category is the Documents tab`() {
        assertThat(tabForRoute(Routes.DOCUMENTS_PATTERN)).isEqualTo(Routes.DOCUMENTS)
        assertThat(tabForRoute("documents?category=Insurance")).isEqualTo(Routes.DOCUMENTS)
        assertThat(tabForRoute(Routes.DOCUMENTS)).isEqualTo(Routes.DOCUMENTS)
    }

    @Test
    fun `before anything is on the back stack, Home is selected`() {
        assertThat(tabForRoute(null)).isEqualTo(Routes.HOME)
    }
}
