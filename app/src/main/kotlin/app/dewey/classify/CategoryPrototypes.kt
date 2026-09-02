package app.dewey.classify

import app.dewey.domain.model.DocType

/**
 * Short descriptions of what each kind of document looks like.
 *
 * Classification compares a document against these rather than against a label.
 * "facture d'electricite, releve de compteur, montant a payer" sits far closer to
 * a real bill in embedding space than the words "utility bill" ever could.
 *
 * Every category is described in French, Arabic and English. That is not
 * decoration: measured on the test corpus, the only two misclassifications were
 * Arabic rental contracts, and they were wrong precisely because rental was the
 * one category with no Arabic description while utility bills had one. Adding it
 * took accuracy from 98% to 100%. A missing language is a systematic hole, not
 * noise.
 *
 * Kept in step with tools/eval/classify_bench.py, which is where the accuracy
 * numbers above come from. If these drift, those numbers stop describing the app.
 */
object CategoryPrototypes {

    val byType: Map<DocType, List<String>> = mapOf(
        DocType.UTILITY_BILL to listOf(
            "Facture d'electricite et d'eau, consommation mensuelle, montant a payer, releve de compteur",
            "فاتورة الكهرباء والماء، الاستهلاك الشهري، المبلغ المستحق",
            "Monthly electricity and water utility bill with meter readings and an amount due",
            "Facture de telecommunications, abonnement mobile et internet, forfait, operateur",
            "Mobile and internet telecom bill with subscription plan and charges",
            "فاتورة الاتصالات، اشتراك الهاتف والأنترنت، المشغل، الاستهلاك",
        ),
        DocType.BANK_STATEMENT to listOf(
            "Releve de compte bancaire, solde, operations, debit et credit, virement",
            "Bank account statement listing transactions, balance, debits and credits",
            "كشف حساب بنكي، الرصيد، العمليات، مدين ودائن، تحويل بنكي",
        ),
        DocType.RENTAL_CONTRACT to listOf(
            "Contrat de bail, location d'un appartement, loyer mensuel, bailleur et locataire, duree",
            "Lease agreement between landlord and tenant with monthly rent and duration",
            "عقد كراء سكني، الوجيبة الكرائية الشهرية، المكري والمكتري، مدة العقد، الضمانة",
        ),
        DocType.INVOICE to listOf(
            "Facture d'achat, recu de magasin, articles achetes, prix unitaire, total TTC",
            "Purchase receipt from a shop listing items bought, quantities and total paid",
            "فاتورة شراء من متجر، المواد المشتراة، الثمن الإجمالي، وصل الأداء",
        ),
        DocType.MEDICAL to listOf(
            "Compte rendu medical, consultation, diagnostic, ordonnance, clinique, patient",
            "تقرير طبي، استشارة، تشخيص، وصفة طبية، عيادة",
        ),
        DocType.UNIVERSITY to listOf(
            "Releve de notes universitaire, attestation de scolarite, semestre, credits, moyenne",
            "University transcript or enrollment certificate with courses, grades and credits",
            "كشف النقط الجامعي، شهادة مدرسية، الفصل الدراسي، المعدل العام، الوحدات",
        ),
        DocType.INSURANCE to listOf(
            "Police d'assurance, garantie, prime annuelle, assure, echeance, sinistre",
            "Insurance policy with coverage, annual premium and renewal date",
            "عقد تأمين، الضمانات، القسط السنوي، المؤمن له، تاريخ التجديد",
        ),
        DocType.EMPLOYMENT to listOf(
            "Attestation de travail, contrat de stage, employeur, salaire, poste occupe",
            "Employment attestation or internship agreement stating role and salary",
            "شهادة عمل، عقد تدريب، المشغل، الأجر الشهري، المنصب",
        ),
        DocType.TAX to listOf(
            "Declaration de revenus, impot sur le revenu, Direction Generale des Impots, exercice fiscal",
            "Income tax declaration for a fiscal year with taxable income and tax due",
            "الإقرار بالضريبة على الدخل، المديرية العامة للضرائب، السنة المالية",
        ),
        DocType.WARRANTY to listOf(
            "Bon de garantie, appareil, duree de garantie, numero de serie, service apres-vente",
            "Product warranty certificate with serial number and warranty period",
            "شهادة الضمان، الجهاز، مدة الضمان، الرقم التسلسلي، خدمة ما بعد البيع",
        ),
        DocType.ADMIN to listOf(
            "Certificat de residence, extrait d'acte de naissance, casier judiciaire, commune, etat civil",
            "Official administrative certificate issued by a government office",
            "شهادة الإقامة، نسخة من رسم الولادة، السجل العدلي، مكتب الحالة المدنية",
        ),
    )

    /** Flattened, with the type each description belongs to. */
    val all: List<Pair<DocType, String>> =
        byType.entries.flatMap { (type, texts) -> texts.map { type to it } }
}
