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
