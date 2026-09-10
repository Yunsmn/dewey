package app.dewey.ui.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue

/**
 * A single written line — a page range like "1-3, 7", a starting number, the
 * words of a watermark — styled like the search field: a line on the paper,
 * not a boxed input, so a tool screen doesn't suddenly look like a form.
 *
 * Named for what it is rather than its first use. It began as the page tools'
 * range field, and carrying that name into the watermark screen described a
 * box for arbitrary text as a box for page numbers. [keyboardType] is what
 * changes between uses; the look does not.
 *
 * @param hue tints the cursor and the rule beneath the field once it's
 *   focused, so typing into the rotate tool feels like typing into something
 *   blue rather than into a house-wide accent. Falls back to
 *   [Dewey.colors.accent] when null.
 */
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    hue: Hue? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val tint = hue?.strong ?: Dewey.colors.accent
    val ruleColor by animateColorAsState(if (focused) tint else Dewey.colors.rule, label = "fieldRule")

    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(tint),
            interactionSource = interactions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = Dewey.type.Body, color = Dewey.colors.inkFaint)
                    }
                    field()
                }
            },
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(top = Dewey.spacing.tight),
            thickness = if (focused) 2.dp else 1.dp,
            color = ruleColor,
        )
    }
}
