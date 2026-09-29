package app.dewey.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dewey.auth.AccountState
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.tools.ToolTextField
import app.dewey.ui.tools.secure.PasswordField

/**
 * The optional account: sign in, create one, or sign out.
 *
 * Hidden entirely in a build with no Firebase project ([AccountState.Unavailable]).
 * The fields are held here rather than in [MeViewModel]: they mean nothing
 * until a button is pressed, and a password is better kept out of anything
 * longer-lived than the screen.
 */
@Composable
fun MeAccountSection(
    account: AccountState,
    isBusy: Boolean,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onSignOut: () -> Unit,
) {
    if (account == AccountState.Unavailable) return

    GlassCard(modifier = Modifier.fillMaxWidth(), padding = Dewey.spacing.gutter) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AccountCircle, Dewey.colors.hues.pages, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column {
                Text("Account", style = Dewey.type.Title, color = Dewey.colors.ink)
                Text(
                    text = when (account) {
                        is AccountState.SignedIn -> account.email.ifBlank { "Signed in" }
                        else -> "Optional. Keeps Librarian with you on a new phone. Your documents never leave this one."
                    },
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkMuted,
                )
            }
        }

        when (account) {
            is AccountState.SignedIn -> {
                Spacer(Modifier.height(Dewey.spacing.row))
                SecondaryAction(
                    label = if (isBusy) "Signing out…" else "Sign out",
                    onClick = { if (!isBusy) onSignOut() },
                    icon = Icons.AutoMirrored.Rounded.Logout,
                )
            }

            else -> SignInForm(isBusy, onSignIn, onCreateAccount, onResetPassword)
        }
    }
}

@Composable
private fun SignInForm(
    isBusy: Boolean,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Not rememberSaveable: a password should not be written into saved state.
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Spacer(Modifier.height(Dewey.spacing.row))
    ToolTextField(
        value = email,
        onValueChange = { email = it },
        placeholder = "Email",
        keyboardType = KeyboardType.Email,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Dewey.spacing.tight))
    PasswordField(
        value = password,
        onValueChange = { password = it },
        placeholder = "Password",
        visible = passwordVisible,
        onToggleVisible = { passwordVisible = !passwordVisible },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Dewey.spacing.row))
    PrimaryAction(
        label = if (isBusy) "One moment…" else "Sign in",
        onClick = { if (!isBusy) onSignIn(email, password) },
    )
    Spacer(Modifier.height(Dewey.spacing.tight))
    Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
        SecondaryAction(label = "Create account", onClick = { if (!isBusy) onCreateAccount(email, password) })
        SecondaryAction(label = "Forgot password", onClick = { if (!isBusy) onResetPassword(email) })
    }
}
