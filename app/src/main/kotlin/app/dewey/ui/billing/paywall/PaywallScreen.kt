package app.dewey.ui.billing.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/** All the way round, matching [app.dewey.ui.components.PrimaryAction]'s own pill. */
private val PILL_CORNER = 999.dp
private val CTA_VERTICAL_PADDING = 14.dp

/**
 * The paywall's whole visible surface, stateless over [PaywallUiState].
 *
 * Two parts. What Librarian is — the hero and the benefits — scrolls. What it
 * costs and the button to buy it sit in a footer pinned to the bottom. The
 * first version put the plans after all seven benefits, so on a phone the
 * button was never on screen without scrolling, which is the one place a
 * paywall's primary action must not be. It also made a scripted tap on the
 * emulator land on a list still coasting from the scroll that revealed it.
 *
 * Kept apart from [app.dewey.ui.billing.LibrarianPaywall] so this can be
 * previewed and reasoned about without a `Dialog`, a `ViewModel` or an
 * `Activity` — see the [Preview]s below, drawn from fake plans.
 */
@Composable
fun PaywallScreen(
    state: PaywallUiState,
    onSelectPlan: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(Dewey.colors.paper)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, top = 56.dp, bottom = Dewey.spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PaywallHero()
                Spacer(Modifier.height(Dewey.spacing.block))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
                ) {
                    LibrarianBenefits.forEach { benefit -> BenefitRow(benefit) }
                }
                Spacer(Modifier.height(Dewey.spacing.gutter))
                Text(
                    "The scanner and every PDF tool stay free.",
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkFaint,
                    textAlign = TextAlign.Center,
                )
            }

            PaywallCloseButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(Dewey.spacing.row),
            )
        }

        PaywallFooter(state = state, onSelectPlan = onSelectPlan, onPurchase = onPurchase, onRestore = onRestore, onRetry = onRetry)
    }
}

/** The pinned bottom panel: whatever the offering's state calls for, always in reach of a thumb. */
@Composable
private fun PaywallFooter(
    state: PaywallUiState,
    onSelectPlan: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = Dewey.radii.large, topEnd = Dewey.radii.large)
    val shadowColor = Dewey.colors.ink.copy(alpha = 0.14f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(Dewey.colors.paperRaised)
            .navigationBarsPadding()
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, top = Dewey.spacing.gutter, bottom = Dewey.spacing.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.phase) {
            PaywallPhase.Loading -> PaywallLoadingPlaceholder()
            PaywallPhase.OfferingsFailed -> PaywallOfferingsError(message = state.errorMessage, onRetry = onRetry)
            PaywallPhase.Success -> PaywallSuccessConfirmation()
            else -> PaywallPlansAndCta(state = state, onSelectPlan = onSelectPlan, onPurchase = onPurchase, onRestore = onRestore)
        }
    }
}

@Composable
private fun PaywallHero() {
    val hue = Dewey.colors.hues.assistant
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(hue.soft),
        )
        IconTile(icon = Icons.Rounded.AutoAwesome, hue = hue, size = 60.dp)
    }
    Spacer(Modifier.height(Dewey.spacing.row))
    Text("Dewey Librarian", style = Dewey.type.Headline, color = Dewey.colors.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Dewey.spacing.tight))
    Text(
        "Your documents, sorted, tracked and answered.",
        style = Dewey.type.Body,
        color = Dewey.colors.inkMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BenefitRow(benefit: PaywallBenefit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = benefit.icon, hue = benefit.hue(Dewey.colors.hues), size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(benefit.title, style = Dewey.type.Title, color = Dewey.colors.ink)
            Text(benefit.description, style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
        }
    }
}

@Composable
private fun PaywallLoadingPlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = Dewey.spacing.gutter)) {
        CircularProgressIndicator(color = Dewey.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Dewey.spacing.row))
        Text("Loading plans…", style = Dewey.type.Body, color = Dewey.colors.inkMuted)
    }
}

@Composable
private fun PaywallOfferingsError(message: String?, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = Dewey.spacing.row)) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Dewey.colors.danger, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            message ?: "Couldn't load plans.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dewey.spacing.row))
        SecondaryAction(label = "Retry", onClick = onRetry)
    }
}

@Composable
private fun PaywallSuccessConfirmation() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = Dewey.spacing.gutter)) {
        Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = Dewey.colors.accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(Dewey.spacing.row))
        Text("Welcome to Librarian", style = Dewey.type.Title, color = Dewey.colors.ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PaywallPlansAndCta(
    state: PaywallUiState,
    onSelectPlan: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    val purchasing = state.phase == PaywallPhase.Purchasing
    val restoring = state.phase == PaywallPhase.Restoring
    val interactive = state.phase == PaywallPhase.Ready

    // Two headline plans side by side, the usual shape and half the height;
    // anything else the dashboard might offer is listed one per row.
    if (state.plans.size == 2) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
        ) {
            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.id == state.selectedPlanId,
                    enabled = interactive,
                    onSelect = { onSelectPlan(plan.id) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.id == state.selectedPlanId,
                    enabled = interactive,
                    onSelect = { onSelectPlan(plan.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Spacer(Modifier.height(Dewey.spacing.row))

    val selected = state.selectedPlan
    if (selected != null) {
        PaywallPrimaryCta(
            label = ctaLabel(selected.hasFreeTrial),
            loading = purchasing,
            enabled = !purchasing && !restoring,
            onClick = onPurchase,
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            renewalWording(selected.priceFormatted, selected.periodLabel),
            style = Dewey.type.Meta,
            color = Dewey.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }

    if (state.phase == PaywallPhase.PurchaseFailed || state.phase == PaywallPhase.RestoreFailed) {
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            state.errorMessage ?: "Something went wrong.",
            style = Dewey.type.Meta,
            color = Dewey.colors.danger,
            textAlign = TextAlign.Center,
        )
    }

    Spacer(Modifier.height(Dewey.spacing.tight))
    if (restoring) {
        CircularProgressIndicator(color = Dewey.colors.inkFaint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
    } else {
        TextLinkButton(label = "Restore purchases", enabled = !purchasing, onClick = onRestore)
    }
}

/**
 * One plan. Selection is shown by an accent outline and a filled radio, not by
 * colour alone, and the annual plan carries its badge above the name.
 */
@Composable
private fun PlanCard(
    plan: PaywallPlan,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dewey.radii.medium)
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .background(if (selected) Dewey.colors.accentSoft else Dewey.colors.paper)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Dewey.colors.accent else Dewey.colors.rule,
                shape = shape,
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(Dewey.spacing.row),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                if (plan.kind == PaywallPlanKind.ANNUAL) {
                    Text(annualBadgeLabel(plan.savingsPercent), style = Dewey.type.Micro, color = Dewey.colors.accent)
                    Spacer(Modifier.height(Dewey.spacing.hairline))
                }
                Text(planTitle(plan.kind, plan.displayName), style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
            Icon(
                imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Dewey.colors.accent else Dewey.colors.inkFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text("${plan.priceFormatted}/${plan.periodLabel}", style = Dewey.type.Title, color = Dewey.colors.ink)
        val perMonth = plan.pricePerMonthFormatted
        if (plan.kind == PaywallPlanKind.ANNUAL && perMonth != null) {
            Text("$perMonth/month", style = Dewey.type.Meta, color = Dewey.colors.accent)
        }
    }
}

/**
 * The paywall's own primary pill: [app.dewey.ui.components.PrimaryAction]'s
 * look, plus a loading slot that one has no reason to carry — nothing else
 * in this app buys anything.
 */
@Composable
private fun PaywallPrimaryCta(label: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PILL_CORNER)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dewey.colors.accent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = CTA_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Dewey.colors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text = label, style = Dewey.type.Button, color = Dewey.colors.onAccent)
        }
    }
}

@Composable
private fun TextLinkButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dewey.radii.small))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dewey.spacing.row, vertical = Dewey.spacing.tight),
    ) {
        Text(label, style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
    }
}

@Composable
private fun PaywallCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Dewey.colors.paperRaised)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close",
            tint = Dewey.colors.inkMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun fakePlans(): List<PaywallPlan> = listOf(
    PaywallPlan(
        id = "annual",
        kind = PaywallPlanKind.ANNUAL,
        priceFormatted = "$29.99",
        periodLabel = "year",
        pricePerMonthFormatted = "$2.50",
        savingsPercent = 49,
        hasFreeTrial = false,
    ),
    PaywallPlan(
        id = "monthly",
        kind = PaywallPlanKind.MONTHLY,
        priceFormatted = "$4.99",
        periodLabel = "month",
        hasFreeTrial = false,
    ),
)

@Preview(name = "Light", showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun PaywallScreenLightPreview() {
    DeweyTheme(dark = false) {
        PaywallScreen(
            state = PaywallUiState(phase = PaywallPhase.Ready, plans = fakePlans(), selectedPlanId = "annual"),
            onSelectPlan = {},
            onPurchase = {},
            onRestore = {},
            onRetry = {},
            onClose = {},
        )
    }
}

@Preview(name = "Dark", showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun PaywallScreenDarkPreview() {
    DeweyTheme(dark = true) {
        PaywallScreen(
            state = PaywallUiState(phase = PaywallPhase.Purchasing, plans = fakePlans(), selectedPlanId = "annual"),
            onSelectPlan = {},
            onPurchase = {},
            onRestore = {},
            onRetry = {},
            onClose = {},
        )
    }
}
