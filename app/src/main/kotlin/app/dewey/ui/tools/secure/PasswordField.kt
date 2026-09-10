package app.dewey.ui.tools.secure

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue

/**
 * A single masked password line, styled like [app.dewey.ui.tools.ToolTextField] —
 * a line on the paper with a rule that colours in on focus, not a boxed input.
 *
 * The reveal control is a word, not a small eye icon: this app has no other
 * icon-only controls, and a password field is exactly the wrong place to make
 * someone guess what a glyph means.
 *
 * @param hue tints the cursor, the focus rule and the Show/Hide word, so the
 *   protect screen's own violet follows the password all the way down. Falls
 *   back to [Dewey.colors.accent] when null.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    hue: Hue? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val tint = hue?.strong ?: Dewey.colors.accent
    val ruleColor by animateColorAsState(if (focused) tint else Dewey.colors.rule, label = "passwordRule")

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    cursorBrush = SolidColor(tint),
                    interactionSource = interactions,
                    decorationBox = { field ->
                        Box {
                            if (value.isEmpty()) {
                                Text(placeholder, style = Dewey.type.Body, color = Dewey.colors.inkFaint)
                            }
                            field()
                        }
                    },
                )
            }
            Text(
                text = if (visible) "Hide" else "Show",
                style = Dewey.type.Meta.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                modifier = Modifier
                    .clickable(onClick = onToggleVisible)
                    .padding(start = Dewey.spacing.row, top = Dewey.spacing.tight, bottom = Dewey.spacing.tight),
            )
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(top = Dewey.spacing.tight),
            thickness = if (focused) 2.dp else 1.dp,
            color = ruleColor,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun PasswordFieldPreview() {
    DeweyTheme {
        PasswordField(
            value = "hunter2",
            onValueChange = {},
            placeholder = "At least 8 characters",
            visible = false,
            onToggleVisible = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
