package app.dewey.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/**
 * Pinned above the keyboard and the gesture bar — see [Modifier.imePadding] and
 * [Modifier.navigationBarsPadding] — the same rounded-field-plus-pill shape
 * [app.dewey.ui.documents.AskBar] uses, so asking looks like the same feature
 * whether it is reached from Documents or from here.
 *
 * @param canSend whether asking anything at all would do something right now
 *   — false once today's cap is spent, in which case the field itself is
 *   disabled rather than just the button, so there is nothing ambiguous to
 *   tap while waiting for tomorrow.
 */
@Composable
fun AssistantInputBar(
    input: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val send = {
        keyboard?.hide()
        onSend()
    }
    val fieldShape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(fieldShape)
                .background(Dewey.colors.paperSunken, fieldShape)
                .padding(horizontal = Dewey.spacing.gutter, vertical = 12.dp),
        ) {
            if (input.isEmpty()) {
                Text(
                    text = "Ask about your documents",
                    style = Dewey.type.Body,
                    color = Dewey.colors.inkFaint,
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChanged,
                enabled = canSend,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
                // BasicTextField's default cursor vanishes on the dark palette's sunken field.
                cursorBrush = SolidColor(Dewey.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend && input.isNotBlank()) send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SendButton(enabled = canSend && input.isNotBlank(), onClick = send)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val hue = Dewey.colors.hues.assistant
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) hue.strong else hue.soft)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Send,
            contentDescription = "Send",
            tint = if (enabled) Dewey.colors.onAccent else hue.strong,
            modifier = Modifier.size(20.dp),
        )
    }
}
