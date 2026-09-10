package app.dewey.ui.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.dewey.ui.theme.Dewey

/**
 * A single written line — a page range like "1-3, 7", a starting number, the
 * words of a watermark — styled like the search field: a line on the paper,
 * not a boxed input, so a tool screen doesn't suddenly look like a form.
 *
 * Named for what it is rather than its first use. It began as the page tools'
 * range field, and carrying that name into the watermark screen described a
 * box for arbitrary text as a box for page numbers. [keyboardType] is what
 * changes between uses; the look does not.
 */
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        cursorBrush = SolidColor(Dewey.colors.accent),
        modifier = modifier.fillMaxWidth(),
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
