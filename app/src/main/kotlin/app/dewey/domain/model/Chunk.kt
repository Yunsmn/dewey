package app.dewey.domain.model

/**
 * A slice of a document's text, with the vector that represents it.
 *
 * Retrieval works over chunks rather than whole documents because a bank
 * statement's relevant paragraph is a small part of a long file, and embedding
 * the whole thing averages that paragraph into noise.
 */
data class Chunk(
    val id: Long = 0,
    val documentId: Long,
    val ordinal: Int,
    val text: String,
    val embedding: FloatArray,
) {
    // FloatArray uses identity equality, so data-class equals would be wrong in a
    // way that silently breaks set membership and test assertions.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chunk) return false
        return id == other.id &&
            documentId == other.documentId &&
            ordinal == other.ordinal &&
            text == other.text &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + ordinal
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
