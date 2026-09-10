package app.dewey.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue

/**
 * What Librarian gets you, for the tab it lives behind.
 *
 * A richer offer than a single locked panel — see
 * [app.dewey.ui.billing.LibrarianGate] for that simpler shape — because this
 * is one whole tab rather than one gated action inside a free screen, and a
 * feature list is a more honest pitch than one padlock icon for four
 * genuinely different things: the folder link itself, automatic sorting,
 * asking questions, and bill tracking.
 *
 * It only asks for the paywall through [onUnlock]; [DocumentsScreen] owns the
 * paywall itself, outside this showcase, so buying — which replaces this
 * showcase with the documents — does not take the paywall's confirmation
 * down with it.
 */
@Composable
fun DocumentsLockedShowcase(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    DocumentsLockedContent(onUnlock = onUnlock, modifier = modifier)
}

private data class LockedFeature(val icon: ImageVector, val hue: Hue, val title: String, val blurb: String)

@Composable
private fun DocumentsLockedContent(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val features = listOf(
        LockedFeature(
            icon = Icons.Rounded.Folder,
            hue = Dewey.colors.hues.pages,
            title = "Link a folder",
            blurb = "Point Librarian at Downloads, or wherever your documents land, and see them here.",
        ),
        LockedFeature(
            icon = Icons.Rounded.SwapVert,
            hue = Dewey.colors.hues.convert,
            title = "Automatic sorting",
            blurb = "Bills, statements, contracts — filed into folders that match your own filing, not a generic list.",
        ),
        LockedFeature(
            icon = Icons.Rounded.AutoAwesome,
            hue = Dewey.colors.hues.assistant,
            title = "Ask your documents",
            blurb = "“When does my insurance renew?” — answered from what's actually in your folder.",
        ),
        LockedFeature(
            icon = Icons.Rounded.Receipt,
            hue = Dewey.colors.hues.bills,
            title = "Bill tracking",
            blurb = "What's due, what's overdue, and what it costs, kept in one place.",
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.block),
    ) {
        Text("Documents", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "Librarian keeps your linked folder here, sorted and searchable, with an assistant that " +
                "can answer questions about it.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
        )
        Spacer(Modifier.height(Dewey.spacing.block))

        for (feature in features) {
            LockedFeatureRow(feature)
            Spacer(Modifier.height(Dewey.spacing.gutter))
        }

        Spacer(Modifier.height(Dewey.spacing.tight))
        PrimaryAction(label = "Unlock Librarian", onClick = onUnlock)
        Spacer(Modifier.height(NavBarClearance))
    }
}

@Composable
private fun LockedFeatureRow(feature: LockedFeature) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
        IconTile(icon = feature.icon, hue = feature.hue, size = 44.dp)
        Column {
            Text(feature.title, style = Dewey.type.Title, color = Dewey.colors.ink)
            Text(feature.blurb, style = Dewey.type.Body, color = Dewey.colors.inkMuted)
        }
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun DocumentsLockedLightPreview() {
    DeweyTheme(dark = false) {
        DocumentsLockedContent(onUnlock = {})
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun DocumentsLockedDarkPreview() {
    DeweyTheme(dark = true) {
        DocumentsLockedContent(onUnlock = {})
    }
}
