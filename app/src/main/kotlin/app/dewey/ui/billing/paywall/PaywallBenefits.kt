package app.dewey.ui.billing.paywall

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
import app.dewey.ui.theme.DeweyHues
import app.dewey.ui.theme.Hue

/**
 * One thing Librarian unlocks, on the paywall's benefits list.
 *
 * A plain list rather than something cleverer, so the next one — home-screen
 * widgets, say — is one more entry here rather than a change to how the
 * list is drawn.
 *
 * @param hue reads from [DeweyHues] rather than holding a [Hue] directly,
 *   because a [Hue] only exists inside composition — see
 *   [app.dewey.ui.theme.Dewey.colors].
 */
data class PaywallBenefit(
    val icon: ImageVector,
    val hue: (DeweyHues) -> Hue,
    val title: String,
    val description: String,
)

/** Only what this app actually does today — see the class doc on [PaywallBenefit]. */
val LibrarianBenefits: List<PaywallBenefit> = listOf(
    PaywallBenefit(
        icon = Icons.Rounded.Folder,
        hue = { it.pages },
        title = "See every document",
        description = "Link your folder and browse everything, sorted by category.",
    ),
    PaywallBenefit(
        icon = Icons.Rounded.Bolt,
        hue = { it.scan },
        title = "Sort in one tap",
        description = "File everything into folders at once — with a review queue and undo.",
    ),
    PaywallBenefit(
        icon = Icons.Rounded.Category,
        hue = { it.convert },
        title = "Learns your categories",
        description = "Picks them up from the folders you already keep.",
    ),
    PaywallBenefit(
        icon = Icons.Rounded.Chat,
        hue = { it.assistant },
        title = "Ask your documents",
        description = "Get answers pulled straight from what's in your files.",
    ),
    PaywallBenefit(
        icon = Icons.Rounded.ManageSearch,
        hue = { it.mark },
        title = "Search by meaning",
        description = "Find a document by what it's about, not just its name.",
    ),
    PaywallBenefit(
        icon = Icons.Rounded.Receipt,
        hue = { it.bills },
        title = "Track your bills",
        description = "Amounts and due dates, kept in one place.",
    ),
    PaywallBenefit(
        icon = Icons.Rounded.NoteAdd,
        hue = { it.protect },
        title = "Notes on anything",
        description = "On their own, or attached to a bill.",
    ),
)
