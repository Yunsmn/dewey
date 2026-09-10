package app.dewey.ui.tools.secure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * A single masked password line, styled like [app.dewey.ui.tools.ToolTextField] —
 * a line on the paper, not a boxed input.
 *
 * The reveal control is a word, not a small eye icon: this app has no other
 * icon-only controls, and a password field is exactly the wrong place to make
 * someone guess what a glyph means.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                cursorBrush = SolidColor(Dewey.colors.accent),
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
            style = Dewey.type.Meta,
            color = Dewey.colors.accent,
            modifier = Modifier
                .clickable(onClick = onToggleVisible)
                .padding(start = Dewey.spacing.row, top = Dewey.spacing.tight, bottom = Dewey.spacing.tight),
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
