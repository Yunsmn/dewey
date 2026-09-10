"""
Asks whether classification needs a cloud model at all.

The brief assumes sorting is a cloud job. But every document is already embedded
on device for retrieval, and a category is just another point in that space — so
a document can be labelled by comparing it to a short description of each
category, with no network call, no per-file cost and nothing leaving the phone.

This measures whether that is accurate enough to ship. Ground truth is the
corpus's own category labels. If it holds up, sorting becomes free and offline,
and the cloud is reserved for the documents that genuinely need it.
"""

import argparse
import json
from pathlib import Path

import numpy as np
from pypdf import PdfReader

from retrieval_bench import Encoder, chunk

# Mirrors DocumentClassifier.MIN_SIMILARITY / MIN_MARGIN. If those move, move
# these: a bench measuring a different threshold than the app ships is not
# measuring the app.
MIN_SIMILARITY = 0.80
MIN_MARGIN = 0.008

CORPUS = Path('tools/corpus/corpus')
GROUND_TRUTH = Path('tools/corpus/ground_truth.json')

# One or two sentences per category, in the language mix the corpus uses.
# Deliberately describing the *document*, not the category name: "facture
# d'electricite" is much closer to a real bill than the word "utility_bill".
PROTOTYPES = {
    'utility_bill': [
        "Facture d'electricite et d'eau, consommation mensuelle, montant a payer, releve de compteur",
        "فاتورة الكهرباء والماء، الاستهلاك الشهري، المبلغ المستحق",
        "Monthly electricity and water utility bill with meter readings and an amount due",
    ],
    'bank_statement': [
        "Releve de compte bancaire, solde, operations, debit et credit, virement",
        "Bank account statement listing transactions, balance, debits and credits",
        "كشف حساب بنكي، الرصيد، العمليات، مدين ودائن، تحويل بنكي",
    ],
    'rental_contract': [
        "Contrat de bail, location d'un appartement, loyer mensuel, bailleur et locataire, duree",
        "Lease agreement between landlord and tenant with monthly rent and duration",
        "عقد كراء سكني، الوجيبة الكرائية الشهرية، المكري والمكتري، مدة العقد، الضمانة",
    ],
    'invoice': [
        "Facture d'achat, recu de magasin, articles achetes, prix unitaire, total TTC",
        "Purchase receipt from a shop listing items bought, quantities and total paid",
        "فاتورة شراء من متجر، المواد المشتراة، الثمن الإجمالي، وصل الأداء",
    ],
    'medical': [
        "Compte rendu medical, consultation, diagnostic, ordonnance, clinique, patient",
        "تقرير طبي، استشارة، تشخيص، وصفة طبية، عيادة",
        "Medical report from a clinic with a consultation, diagnosis and prescription",
    ],
    # No travel documents exist in the generated corpus, so this category cannot
    # be scored for recall here. It is present so the bench measures the thing
    # that actually matters about adding it: whether a travel prototype steals
    # documents that belong to another category.
    'travel': [
        "Carte d'embarquement, vol, numero de siege, porte, heure de depart, aeroport",
        "Boarding pass or flight booking with passenger name, seat, gate and departure time",
        "بطاقة صعود الطائرة، رقم الرحلة، المقعد، بوابة المغادرة، المطار",
        "Reservation d'hotel, nuitees, date d'arrivee et de depart, confirmation de reservation",
        "Hotel or train booking confirmation with dates, reference number and traveller name",
    ],
    # Kept in step with CategoryPrototypes.kt — see the note there on why this
    # is separate from 'university' rather than folded into it.
    'paper': [
        "Research paper with an abstract, introduction, method, experiments, results and a list of references",
        "Article de recherche scientifique avec resume, introduction, methode, resultats et bibliographie",
        "ورقة بحثية أكاديمية، ملخص، مقدمة، منهجية البحث، النتائج، لائحة المراجع",
        "Preprint describing a model architecture, its training procedure and benchmark evaluation",
        "Technical specification or standards document defining a protocol, in numbered sections with normative requirements",
    ],
    'university': [
        "Releve de notes universitaire, attestation de scolarite, semestre, credits, moyenne",
        "University transcript or enrollment certificate with courses, grades and credits",
        "كشف النقط الجامعي، شهادة مدرسية، الفصل الدراسي، المعدل العام، الوحدات",
    ],
    'insurance': [
        "Police d'assurance, garantie, prime annuelle, assure, echeance, sinistre",
        "Insurance policy with coverage, annual premium and renewal date",
        "عقد تأمين، الضمانات، القسط السنوي، المؤمن له، تاريخ التجديد",
    ],
    'employment': [
        "Attestation de travail, contrat de stage, employeur, salaire, poste occupe",
        "Employment attestation or internship agreement stating role and salary",
        "شهادة عمل، عقد تدريب، المشغل، الأجر الشهري، المنصب",
    ],
    'tax': [
        "Declaration de revenus, impot sur le revenu, Direction Generale des Impots, exercice fiscal",
        "Income tax declaration for a fiscal year with taxable income and tax due",
        "الإقرار بالضريبة على الدخل، المديرية العامة للضرائب، السنة المالية",
    ],
    'warranty': [
        "Bon de garantie, appareil, duree de garantie, numero de serie, service apres-vente",
        "Product warranty certificate with serial number and warranty period",
        "شهادة الضمان، الجهاز، مدة الضمان، الرقم التسلسلي، خدمة ما بعد البيع",
    ],
    'admin': [
        "Certificat de residence, extrait d'acte de naissance, casier judiciaire, commune, etat civil",
        "Official administrative certificate issued by a government office",
        "شهادة الإقامة، نسخة من رسم الولادة، السجل العدلي، مكتب الحالة المدنية",
    ],
    'misc': [
        "Facture de telecommunications, abonnement mobile et internet, forfait, operateur",
        "Mobile and internet telecom bill with subscription plan and charges",
        "فاتورة الاتصالات، اشتراك الهاتف والأنترنت، المشغل، الاستهلاك",
    ],
}


def read_pdf(path: Path) -> str:
    return '\n'.join(page.extract_text() or '' for page in PdfReader(str(path)).pages)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-dir', type=Path, required=True)
    parser.add_argument('--chunks', type=int, default=2,
                        help='how many leading chunks represent a document')
    args = parser.parse_args()

    truth = json.loads(GROUND_TRUTH.read_text())
    encoder = Encoder(args.model_dir)

    labels = list(PROTOTYPES)
    prototype_texts, prototype_owner = [], []
    for label in labels:
        for text in PROTOTYPES[label]:
            prototype_texts.append(f'passage: {text}')
            prototype_owner.append(label)
    prototype_vectors = encoder.encode(prototype_texts)
    prototype_owner = np.array(prototype_owner)

    # A document is represented by its opening chunks: the letterhead and first
    # paragraph are what say what a document *is*. Later pages are detail.
    document_texts, wanted = [], []
    for entry in truth:
        pieces = chunk(read_pdf(CORPUS / entry['filename']))[:args.chunks]
        document_texts.append(f'passage: {" ".join(pieces)}')
        wanted.append(entry['category'])
    document_vectors = encoder.encode(document_texts)

    scores = document_vectors @ prototype_vectors.T

    # Best single prototype wins, rather than the mean over a category's
    # prototypes: the categories are described in different languages, and
    # averaging a French and an Arabic description lands between both.
    # Kept alongside the predictions so the app's own two thresholds can be
    # applied below rather than re-derived.
    ranked_all = []
    predictions, confidences = [], []
    for row in scores:
        best_by_label = {
            label: row[prototype_owner == label].max() for label in labels
        }
        ranked = sorted(best_by_label.items(), key=lambda kv: -kv[1])
        predictions.append(ranked[0][0])
        confidences.append(ranked[0][1] - ranked[1][1])
        ranked_all.append((ranked[0][0], float(ranked[0][1]), float(ranked[0][1] - ranked[1][1])))

    correct = sum(p == w for p, w in zip(predictions, wanted))
    print(f'argmax accuracy: {correct}/{len(wanted)} = {correct / len(wanted):.1%}')

    # The number above is not what the app does, and on its own it is
    # misleading in both directions.
    #
    # DocumentClassifier only acts on a document when the winning category
    # clears MIN_SIMILARITY *and* beats the runner-up by MIN_MARGIN. Anything
    # else goes to a person. So a document sitting four ten-thousandths from a
    # category boundary is not a misfiling the user ever sees — it is the
    # review queue doing its job — and counting it as an error understates the
    # app while making the headline figure hostage to the fourth decimal
    # place. (Adding a travel category, which cannot win any document in this
    # corpus, still flipped one such document and moved "accuracy" from 100%
    # to 99%: the encoder pads each batch to its longest member, so changing
    # what is in a batch perturbs every vector in it very slightly.)
    #
    # What matters for trust is the pair below: how much the app is willing to
    # file, and how much of that it gets right.
    acted, acted_correct, declined, declined_correct = 0, 0, 0, 0
    for (label, top, margin), want in zip(ranked_all, wanted):
        if top >= MIN_SIMILARITY and margin >= MIN_MARGIN:
            acted += 1
            acted_correct += label == want
        else:
            declined += 1
            declined_correct += label == want

    print(f'filed (sim >= {MIN_SIMILARITY}, margin >= {MIN_MARGIN}): '
          f'{acted}/{len(wanted)} = {acted / len(wanted):.1%} of the corpus')
    if acted:
        print(f'  correct among those filed: {acted_correct}/{acted} = {acted_correct / acted:.1%}')
    print(f'sent to review: {declined}')
    if declined:
        print(f'  of which the argmax would have got right anyway: {declined_correct}/{declined}')

    # The review queue depends on this being meaningful: if wrong answers are
    # not less confident than right ones, there is nothing to surface.
    right = [c for c, p, w in zip(confidences, predictions, wanted) if p == w]
    wrong = [c for c, p, w in zip(confidences, predictions, wanted) if p != w]
    print(f'margin when right: {np.mean(right):.4f}')
    if wrong:
        print(f'margin when wrong: {np.mean(wrong):.4f}')

    from collections import Counter
    confusion = Counter(
        (w, p) for p, w in zip(predictions, wanted) if p != w
    )
    if confusion:
        print(f'\n{len(wrong)} wrong:')
        for i, (pred, want) in enumerate(zip(predictions, wanted)):
            if pred != want:
                e = truth[i]
                print(f'  {e["filename"]:32s} lang={e["language"]}  '
                      f'{want} -> {pred}')

    # Where do the mistakes sit when everything is ranked least-confident first?
    # A review queue is only useful if the errors are near the top of it.
    order = sorted(range(len(confidences)), key=lambda i: confidences[i])
    ranks = [pos for pos, i in enumerate(order) if predictions[i] != wanted[i]]
    print(f'\nerrors sit at positions {ranks} of {len(order)} when sorted by margin')

    for size in (3, 5, 8, 12, 20):
        head = order[:size]
        caught = sum(1 for i in head if predictions[i] != wanted[i])
        print(f'  reviewing the least-confident {size:2d}: catches {caught}/{len(wrong)}')


if __name__ == '__main__':
    main()
