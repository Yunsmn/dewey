package app.dewey.ui.documents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [folderDisplayName] turns a SAF tree document id into the name a person
 * actually gave the folder — see the function's own KDoc for why the id's
 * shape differs by provider.
 */
class FolderDisplayNameTest {

    @Test
    fun `a primary storage id keeps only what follows the colon`() {
        assertThat(folderDisplayName("primary:Download")).isEqualTo("Download")
    }

    @Test
    fun `a nested folder keeps only its own last segment`() {
        assertThat(folderDisplayName("primary:Download/Dewey")).isEqualTo("Dewey")
    }

    @Test
    fun `a raw path id is split on slash rather than colon`() {
        assertThat(folderDisplayName("raw:/storage/emulated/0/Download")).isEqualTo("Download")
    }

    @Test
    fun `an id with neither separator is returned whole`() {
        assertThat(folderDisplayName("Download")).isEqualTo("Download")
    }
}
