package app.dewey.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dewey.domain.model.Document
import app.dewey.ui.home.CategoryCount
import app.dewey.ui.library.LibrarySection
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue

/**
 * One chip per category [LibraryViewModel][app.dewey.ui.library.LibraryViewModel]
 * already grouped the library into, in the order it ranked them — a category's
 * rank must read the same way here as it does on Home and in the library
 * itself. "All" is not one of [sections]; it is the count of everything in
 * them, added back in by the caller.
 */
internal fun categoryChips(sections: List<LibrarySection>): List<CategoryCount> =
    sections.map { CategoryCount(it.label, it.documents.size) }

/**
 * The documents a chip should reveal: everything, for the synthetic "All"
 * entry (`category == null`), or just the one section whose label matches.
 * A label that no longer exists — the last document in it was just moved —
 * resolves to an empty list rather than falling back to "All", so a stale
 * selection reads as "nothing here" instead of silently showing everything.
 */
internal fun documentsForCategory(sections: List<LibrarySection>, category: String?): List<Document> =
    if (category == null) {
        sections.flatMap { it.documents }
    } else {
        sections.firstOrNull { it.label == category }?.documents.orEmpty()
    }

@Composable
fun DocumentsCategoryChips(
    sections: List<LibrarySection>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = categoryChips(sections)
    val totalCount = chips.sumOf { it.count }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
    ) {
        CategoryChip(
            label = "All",
            count = totalCount,
            selected = selected == null,
            strong = Dewey.colors.accent,
            soft = Dewey.colors.accentSoft,
            onClick = { onSelect(null) },
        )
        for (chip in chips) {
            val hue: Hue = Dewey.colors.hues.forCategory(chip.label)
            CategoryChip(
                label = chip.label,
                count = chip.count,
                selected = selected == chip.label,
                strong = hue.strong,
                soft = hue.soft,
                onClick = { onSelect(chip.label) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    selected: Boolean,
    strong: Color,
    soft: Color,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) strong else soft
    val foregroundColor = if (selected) Dewey.colors.onAccent else strong
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dewey.spacing.gutter, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, style = Dewey.type.Micro, color = foregroundColor)
        Text(text = count.toString(), style = Dewey.type.Micro, color = foregroundColor.copy(alpha = 0.75f))
    }
}
