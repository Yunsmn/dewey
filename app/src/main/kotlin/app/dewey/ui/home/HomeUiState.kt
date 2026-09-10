package app.dewey.ui.home

import app.dewey.data.recent.RecentFile

/**
 * What the Home tab shows.
 *
 * [now] is captured once per emission rather than read fresh inside a row -
 * the same reasoning as `BillsUiState.today`: a recent file's relative time
 * and the glance figures built from the same instant should never disagree
 * with one another mid-scroll.
 */
data class HomeUiState(
    val isEntitled: Boolean = true,
    val recent: List<RecentFile> = emptyList(),
    val billsGlance: BillsGlance? = null,
    val topCategories: List<CategoryCount> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    /** The AI button or the Upgrade pill was tapped without the entitlement to use it. */
    val showPaywall: Boolean = false,
) {
    /**
     * Whether "At a glance" has anything to show at all.
     *
     * Gated on [isEntitled] here rather than left to the screen, so a screen
     * that only checks "is there anything in these lists" can't accidentally
     * render the section for someone who hasn't unlocked it just because the
     * figures happened to still be sitting in state from before a purchase.
     */
    val hasGlance: Boolean get() = isEntitled && (billsGlance != null || topCategories.isNotEmpty())
}
