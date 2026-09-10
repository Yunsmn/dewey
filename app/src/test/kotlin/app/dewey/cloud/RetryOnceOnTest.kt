package app.dewey.cloud

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryOnceOnTest {

    private class Retryable : Exception()

    @Test
    fun `a first success is returned without a second attempt`() = runTest {
        var attempts = 0
        val result = retryOnceOn(isRetryable = { it is Retryable }) { attempts++; "answer" }

        assertThat(result).isEqualTo("answer")
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `a retryable failure gets exactly one more attempt`() = runTest {
        var attempts = 0
        val result = retryOnceOn(isRetryable = { it is Retryable }) {
            attempts++
            if (attempts == 1) throw Retryable()
            "answer"
        }

        assertThat(result).isEqualTo("answer")
        assertThat(attempts).isEqualTo(2)
    }

    @Test
    fun `a failure that is not retryable is thrown straight away`() {
        var attempts = 0
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                retryOnceOn(isRetryable = { it is Retryable }) { attempts++; throw IOException("offline") }
            }
        }
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `a second retryable failure is thrown rather than retried again`() {
        var attempts = 0
        assertThrows(Retryable::class.java) {
            kotlinx.coroutines.runBlocking {
                retryOnceOn(isRetryable = { it is Retryable }) { attempts++; throw Retryable() }
            }
        }
        assertThat(attempts).isEqualTo(2)
    }
}
