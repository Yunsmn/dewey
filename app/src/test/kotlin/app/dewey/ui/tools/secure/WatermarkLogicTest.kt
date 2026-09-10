package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.ui.tools.ToolRunState
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class WatermarkLogicTest {

    private val target = mockk<Uri>(relaxed = true)

    @Test
    fun `needs a source and non-blank text`() {
        assertThat(canRunWatermark(hasSource = false, text = "DRAFT")).isFalse()
        assertThat(canRunWatermark(hasSource = true, text = "")).isFalse()
        assertThat(canRunWatermark(hasSource = true, text = "   ")).isFalse()
        assertThat(canRunWatermark(hasSource = true, text = "DRAFT")).isTrue()
    }

    @Test
    fun `the summary quotes exactly what was stamped`() {
        assertThat(watermarkSummary("CONFIDENTIAL")).isEqualTo("Watermarked every page with \"CONFIDENTIAL\".")
    }

    @Test
    fun `a successful run reports the summary and leaves the text as typed`() {
        val state = WatermarkUiState(text = "DRAFT")

        val after = state.afterRun(Result.success(Unit), target)

        assertThat(after.run).isEqualTo(ToolRunState.Done(watermarkSummary("DRAFT"), target))
        assertThat(after.text).isEqualTo("DRAFT")
    }

    @Test
    fun `a failed run is reported without losing the chosen options`() {
        val state = WatermarkUiState(text = "DRAFT", opacity = WatermarkOpacity.BOLD)

        val after = state.afterRun(Result.failure(RuntimeException("boom")), target)

        assertThat(after.run).isEqualTo(ToolRunState.Failed("Something went wrong. Nothing was changed."))
        assertThat(after.opacity).isEqualTo(WatermarkOpacity.BOLD)
    }
}
