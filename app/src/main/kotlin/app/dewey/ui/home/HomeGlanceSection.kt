package app.dewey.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.ui.bills.dueDateWords
import app.dewey.ui.bills.formatBillAmount
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.theme.Dewey
import java.time.LocalDate

/**
 * Bills and categories, side by side under one heading - hidden entirely
 * when [billsGlance] is null and [categories] is empty (see
 * HomeUiState.hasGlance, which is what decides whether this composable is
 * even reached), and independently within that: a card only renders when it
 * has something of its own to show.
 */
@Composable
internal fun HomeGlanceSection(
    billsGlance: BillsGlance?,
    categories: List<CategoryCount>,
    today: LocalDate,
    onOpenBills: () -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeading(label = "At a glance")
        Spacer(Modifier.height(Dewey.spacing.tight))
        if (billsGlance != null) {
            BillsGlanceCard(glance = billsGlance, today = today, onClick = onOpenBills)
            Spacer(Modifier.height(Dewey.spacing.row))
        }
        if (categories.isNotEmpty()) {
            CategoriesGlanceCard(categories = categories, onClick = onOpenCategory)
        }
    }
}

@Composable
private fun BillsGlanceCard(glance: BillsGlance, today: LocalDate, onClick: () -> Unit) {
    val hue = Dewey.colors.hues.bills
    val soonest = glance.soonest
    val dueDate = requireNotNull(soonest.dueDate) { "billsGlance only ever returns bills with a due date" }
    val vendor = soonest.vendor?.trim()?.takeIf { it.isNotEmpty() } ?: soonest.displayName

    GlassCard(modifier = Modifier.fillMaxWidth(), accent = hue.strong, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.Rounded.Receipt, hue = hue, size = 44.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${countLabel(glance.dueSoonCount, "bill")} due soon",
                    style = Dewey.type.Title,
                    color = Dewey.colors.ink,
                )
                Text(
                    text = "$vendor · ${dueDateWords(dueDate, today)}",
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            soonest.amount?.let { amount ->
                Text(
                    text = formatBillAmount(amount, soonest.currency),
                    style = Dewey.type.Amount,
                    color = Dewey.colors.ink,
                    modifier = Modifier.padding(start = Dewey.spacing.row),
                )
            }
        }
    }
}

@Composable
private fun CategoriesGlanceCard(categories: List<CategoryCount>, onClick: (String) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            categories.chunked(2).forEachIndexed { index, row ->
                if (index != 0) Spacer(Modifier.height(Dewey.spacing.tight))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
                    row.forEach { category ->
                        CategoryChip(category = category, onClick = { onClick(category.label) }, modifier = Modifier.weight(1f))
                    }
                    // An odd count in the last row: a spacer holds the empty
                    // half's width so the one chip present doesn't stretch to
                    // fill it and read as twice as busy as it is.
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: CategoryCount, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hue = Dewey.colors.hues.forCategory(category.label)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(hue.soft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dewey.spacing.row, vertical = Dewey.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.label,
            style = Dewey.type.Meta,
            color = hue.strong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(Dewey.spacing.tight))
        Text(text = category.count.toString(), style = Dewey.type.Label, color = hue.strong)
    }
}

private fun countLabel(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
