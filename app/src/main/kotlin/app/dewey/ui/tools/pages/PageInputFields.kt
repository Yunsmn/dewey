package app.dewey.ui.tools.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.dewey.ui.theme.Dewey

/**
 * A single written line for page input — a range like "1-3, 7" or a lone page
 * number — styled like the search field: a line on the paper, not a boxed
 * input, so a tool screen doesn't suddenly look like a form.
 */
@Composable
fun PageRangeField(
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
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Dewey.colors.accent),
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

/**
 * What a page-count read is doing right now: still working, or the number a
 * range field's placeholder and validation are checked against.
 */
@Composable
fun PageCountHint(pageCount: Int?, modifier: Modifier = Modifier) {
    Text(
        text = if (pageCount != null) "${pageWord(pageCount)} in this document" else "Reading page count…",
        style = Dewey.type.Meta,
        color = Dewey.colors.inkMuted,
        modifier = modifier,
    )
}
