package app.dewey.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnswerWordingTest {

    @Test
    fun `recognises the English ways of saying nothing was found`() {
        listOf(
            "I could not find information about when your car insurance renews in your documents.",
            "I couldn't find that in your documents.",
            "There is no information about a car insurance renewal.",
            "Your documents do not mention a renewal date.",
            "That is not in your documents.",
        ).forEach { answer -> assertThat(saysNothingWasFound(answer)).isTrue() }
    }

    @Test
    fun `recognises the French ways of saying nothing was found`() {
        listOf(
            "Je n'ai pas trouvé cette information dans vos documents.",
            "Aucune information sur l'assurance automobile.",
            "Cette date ne figure pas dans vos documents.",
        ).forEach { answer -> assertThat(saysNothingWasFound(answer)).isTrue() }
    }

    @Test
    fun `real answers taken from the emulator are not mistaken for not found`() {
        listOf(
            "According to your documents, Omar Tazi visited the Clinique Al Madina Anfa on 2023-04-14.",
            "The contract holder on your March 2023 Lydec bill is Amina Fassi.",
            "Your documents show that Mehdi Chraibi saw a doctor on September 14, 2022, and again on March 14, 2025.",
            "Your documents show no upcoming bills that are not already overdue, as the Lydec bills are all past due.",
            "La facture Lydec de janvier 2023 s'élève à 281,26 MAD.",
        ).forEach { answer -> assertThat(saysNothingWasFound(answer)).isFalse() }
    }
}
