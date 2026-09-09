package app.dewey.classify

import app.dewey.index.VectorMath

/**
 * A category the user taught by filing documents, rather than one we shipped.
 *
 * [examples] are the opening-chunk embeddings of documents already sitting in
 * the folder. They are the prototype: a folder of six real car documents
 * describes "car document" better than any sentence anyone could write for it,
 * and it does so in the user's language, country and filing habits without
 * anybody having predicted them.
 */
data class LearnedCategory(
    val folderName: String,
    val examples: List<FloatArray>,
) {
    /**
     * How well [vector] fits, as the mean of its [NEIGHBOURS] closest examples.
     *
     * Not the single closest. A folder of unrelated documents can produce one
     * accidental near-match; producing two is far harder, and that — rather
     * than any measure of the folder's own tidiness — is what keeps a junk
     * drawer from winning. Measured leave-one-out on the corpus, one neighbour
     * ranks the right folder first 99% of the time and two does it 100%.
     */
    fun score(vector: FloatArray): Float {
        if (examples.isEmpty()) return 0f

        val similarities = examples
            .filter { it.size == vector.size }
            .map { VectorMath.dot(vector, it) }
            .sortedDescending()
        if (similarities.isEmpty()) return 0f

        val counted = similarities.take(NEIGHBOURS)
        return counted.sum() / counted.size
    }

    companion object {
        /** How many of a folder's documents a new one has to resemble. */
        const val NEIGHBOURS = 2
    }
}

/**
 * Turns the folders a person already keeps into categories.
 *
 * The app ships thirteen categories, which is thirteen guesses about somebody
 * else's life. A folder called "Voiture", "Immigration" or "Kids school" is not
 * a guess — it is a decision the user already made, sitting on their disk, and
 * the documents inside it are labelled training data nobody had to label.
 */
object CategoryLearner {

    /**
     * Fewest documents a folder needs before it is treated as a category.
     *
     * Three rather than two: [LearnedCategory.NEIGHBOURS] is 2, so two examples
     * would mean every new document matches the whole folder by definition. A
     * third is also the first real evidence that a folder is a habit rather
     * than somewhere two files happened to land.
     */
    const val MIN_EXAMPLES = 3

    /**
     * Most examples kept per folder.
     *
     * Scoring is linear in the examples held, and this runs once per document
     * in a sort. A folder of three hundred bills is no better described by all
     * three hundred than by a sample of them.
     */
    const val MAX_EXAMPLES = 24

    /**
     * The folders worth treating as categories, with their examples.
     *
     * @param examplesByFolder every top-level folder, mapped to the opening
     *   embeddings of the documents in it.
     */
    fun learn(examplesByFolder: Map<String, List<FloatArray>>): List<LearnedCategory> =
        examplesByFolder
            .filterValues { it.size >= MIN_EXAMPLES }
            .map { (folder, examples) -> LearnedCategory(folder, examples.take(MAX_EXAMPLES)) }
            .sortedBy { it.folderName }
}
