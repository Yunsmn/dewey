"""
Document content templates.

Realism is the whole point: if the generated text is thin or the categories are
easy to tell apart, the experiment measures nothing. Content is Moroccan and
trilingual because that is the corpus the product has to survive.

Every template returns full body prose, not a header. Confusability is designed
in — consecutive months from the same provider, recurring vendors, two letters
from the same clinic — because the interesting failures are near-duplicates.
"""

import random
from dataclasses import dataclass, field

from pdf_render import Line

# --- Moroccan entities, used consistently so documents cluster realistically ---

UTILITY_PROVIDERS = {
    'Lydec': 'Casablanca',
    'Redal': 'Rabat',
    'Amendis': 'Tanger',
}
ONEE_FR = 'Office National de l\'Electricite et de l\'Eau Potable'
ONEE_AR = 'المكتب الوطني للكهرباء والماء الصالح للشرب'

BANKS_FR = ['Attijariwafa Bank', 'Bank of Africa', 'Banque Populaire', 'CIH Bank']
BANKS_AR = {'Attijariwafa Bank': 'التجاري وفا بنك', 'CIH Bank': 'بنك القرض العقاري والسياحي'}

TELECOMS = ['Maroc Telecom', 'Orange Maroc', 'Inwi']
INSURERS = ['Wafa Assurance', 'AXA Assurance Maroc', 'RMA Watanya', 'Sanlam Maroc']
UNIVERSITIES = {
    'Universite Mohammed VI Polytechnique': 'Benguerir',
    'Universite Mohammed V': 'Rabat',
    'Al Akhawayn University': 'Ifrane',
}
VENDORS = ['Marjane', 'Electroplanet', 'Decathlon Maroc', 'Jumia Maroc', 'Bricoma']
CLINICS_FR = ['Clinique Al Madina', 'Centre Medical Anfa']
CLINICS_AR = {'Clinique Al Madina': 'عيادة المدينة'}

SURNAMES = ['Bennani', 'El Amrani', 'Alaoui', 'Chraibi', 'Benjelloun', 'Tazi', 'Berrada', 'Fassi']
FIRST_NAMES = ['Youssef', 'Salma', 'Karim', 'Nadia', 'Omar', 'Leila', 'Mehdi', 'Amina']

MONTHS_FR = ['janvier', 'fevrier', 'mars', 'avril', 'mai', 'juin',
             'juillet', 'aout', 'septembre', 'octobre', 'novembre', 'decembre']
MONTHS_EN = ['January', 'February', 'March', 'April', 'May', 'June',
             'July', 'August', 'September', 'October', 'November', 'December']
MONTHS_AR = ['يناير', 'فبراير', 'مارس', 'أبريل', 'ماي', 'يونيو',
             'يوليوز', 'غشت', 'شتنبر', 'أكتوبر', 'نونبر', 'دجنبر']


@dataclass(frozen=True)
class GeneratedDoc:
    """A document plus its answer-key metadata."""

    category: str
    language: str
    date: str
    organisation: str
    description: str
    lines: list[Line]
    parties: list[str] = field(default_factory=list)
    amount: float | None = None
    currency: str | None = None
    # Terms that must survive the PDF round-trip for this document to be usable.
    key_terms: list[str] = field(default_factory=list)


def _person(rng: random.Random) -> str:
    return f'{rng.choice(FIRST_NAMES)} {rng.choice(SURNAMES)}'


def _iso(year: int, month: int, day: int) -> str:
    return f'{year:04d}-{month:02d}-{day:02d}'


# ---------------------------------------------------------------- utility bills

def utility_bill_fr(rng: random.Random, provider: str, year: int, month: int) -> GeneratedDoc:
    city = UTILITY_PROVIDERS[provider]
    client = _person(rng)
    ref = f'{rng.randint(10, 99)}-{rng.randint(100000, 999999)}'
    elec = round(rng.uniform(180, 640), 2)
    water = round(rng.uniform(60, 210), 2)
    total = round(elec + water, 2)
    consumption = rng.randint(120, 480)

    return GeneratedDoc(
        category='utility_bill',
        language='fr',
        date=_iso(year, month, 8),
        organisation=provider,
        parties=[client],
        amount=total,
        currency='MAD',
        description=f'{provider} electricity and water bill for {MONTHS_FR[month - 1]} {year}, {city}',
        key_terms=[provider, MONTHS_FR[month - 1], str(year)],
        lines=[
            Line(f'{provider} - Distribution Eau et Electricite', heading=True),
            Line(f'Agence de {city}'),
            Line(''),
            Line(f'FACTURE DE CONSOMMATION - {MONTHS_FR[month - 1].upper()} {year}'),
            Line(f'Reference client : {ref}'),
            Line(f'Titulaire du contrat : {client}'),
            Line(f'Adresse de fourniture : {rng.randint(2, 190)} rue Ibn Sina, {city}'),
            Line(''),
            Line('Detail de la consommation'),
            Line(f'Electricite : {consumption} kWh consommes sur la periode facturee.'),
            Line(f'Montant electricite : {elec} MAD'),
            Line(f'Eau potable : {rng.randint(4, 22)} m3 consommes sur la periode facturee.'),
            Line(f'Montant eau : {water} MAD'),
            Line(''),
            Line(f'Total a payer : {total} MAD'),
            Line(f'Date limite de paiement : {_iso(year, month, 28)}'),
            Line(''),
            Line('Le reglement peut etre effectue en agence, par prelevement automatique, '
                 'ou via les guichets automatiques bancaires. Tout retard de paiement '
                 'entraine des penalites conformement aux conditions generales de '
                 'fourniture. Pour toute reclamation concernant cette facture, '
                 'veuillez contacter le service clientele en precisant votre reference client.'),
            Line(''),
            Line('Conformement a la reglementation en vigueur, la releve des index est '
                 'effectuee mensuellement par un agent assermente. En cas d\'absence, '
                 'une estimation basee sur la consommation moyenne des douze derniers '
                 'mois est appliquee et regularisee lors du releve suivant.'),
        ],
    )


def utility_bill_ar(rng: random.Random, year: int, month: int) -> GeneratedDoc:
    client = _person(rng)
    amount = round(rng.uniform(200, 700), 2)
    ref = f'{rng.randint(100000, 999999)}'

    return GeneratedDoc(
        category='utility_bill',
        language='ar',
        date=_iso(year, month, 10),
        organisation=ONEE_AR,
        parties=[client],
        amount=amount,
        currency='MAD',
        description=f'ONEE electricity bill in Arabic for {MONTHS_EN[month - 1]} {year}',
        key_terms=[MONTHS_AR[month - 1], str(year), 'فاتورة'],
        lines=[
            Line(ONEE_AR, heading=True),
            Line('المديرية الجهوية للتوزيع'),
            Line(''),
            Line(f'فاتورة استهلاك الكهرباء لشهر {MONTHS_AR[month - 1]} {year}'),
            Line(f'رقم الزبون : {ref}'),
            Line(f'اسم صاحب العقد : {client}'),
            Line(''),
            Line('تفصيل الاستهلاك'),
            Line(f'الكمية المستهلكة : {rng.randint(150, 500)} كيلوواط ساعة'),
            Line(f'المبلغ الإجمالي المستحق : {amount} درهم'),
            Line(f'آخر أجل للأداء : {_iso(year, month, 27)}'),
            Line(''),
            Line('يمكن أداء مبلغ الفاتورة بالوكالات التجارية أو عبر الشبابيك البنكية '
                 'الأوتوماتيكية أو بواسطة الاقتطاع التلقائي من الحساب البنكي. كل تأخير '
                 'في الأداء يعرض المشترك لغرامات التأخير وفق الشروط العامة للتزويد.'),
            Line(''),
            Line('تتم قراءة العداد شهريا من طرف عامل محلف. في حالة غياب الزبون يتم '
                 'اعتماد تقدير للاستهلاك بناء على متوسط الاثني عشر شهرا الأخيرة '
                 'وتسوية الوضعية عند القراءة الموالية.'),
        ],
    )


# -------------------------------------------------------------- bank statements

def bank_statement(rng: random.Random, bank: str, year: int, month: int) -> GeneratedDoc:
    holder = _person(rng)
    account = f'0{rng.randint(10, 99)} 780 {rng.randint(1000000, 9999999)} {rng.randint(10, 99)}'
    opening = round(rng.uniform(1500, 24000), 2)
    lines = [
        Line(bank, heading=True),
        Line(f'Agence {rng.choice(["Anfa", "Agdal", "Gueliz", "Maarif"])}'),
        Line(''),
        Line(f'RELEVE DE COMPTE - {MONTHS_FR[month - 1].upper()} {year}'),
        Line(f'Titulaire : {holder}'),
        Line(f'Numero de compte : {account}'),
        Line(f'Periode du {_iso(year, month, 1)} au {_iso(year, month, 28)}'),
        Line(''),
        Line(f'Solde initial : {opening} MAD'),
        Line(''),
        Line('Detail des operations'),
    ]

    balance = opening
    # Signed by transaction type: a cash withdrawal that credits the account is
    # the kind of inconsistency that makes a whole corpus untrustworthy.
    CREDITS = [('Virement recu', 800, 6500), ('Remise de cheque', 400, 4000)]
    DEBITS = [('Paiement carte', 40, 1400), ('Retrait GAB', 200, 2000),
              ('Prelevement automatique', 150, 1200), ('Frais de tenue de compte', 20, 90),
              ('Virement emis', 300, 3000), ('Achat en ligne', 60, 900)]

    for day in sorted(rng.sample(range(1, 28), 22)):
        if rng.random() < 0.28:
            label, low, high = rng.choice(CREDITS)
            delta = round(rng.uniform(low, high), 2)
        else:
            label, low, high = rng.choice(DEBITS)
            delta = -round(rng.uniform(low, high), 2)

        balance = round(balance + delta, 2)
        lines.append(Line(f'{_iso(year, month, day)}  {label}  {delta} MAD  solde {balance} MAD'))

    lines += [
        Line(''),
        Line(f'Solde final au {_iso(year, month, 28)} : {balance} MAD'),
        Line(''),
        Line('Ce releve est edite automatiquement et vaut preuve des operations '
             'enregistrees sauf reclamation formulee dans un delai de trente jours '
             'a compter de sa date d\'edition. Les operations en cours de traitement '
             'a la date d\'arrete ne figurent pas sur ce releve et seront reportees '
             'sur le releve suivant.'),
    ]

    return GeneratedDoc(
        category='bank_statement',
        language='fr',
        date=_iso(year, month, 28),
        organisation=bank,
        parties=[holder],
        amount=balance,
        currency='MAD',
        description=f'{bank} account statement for {MONTHS_FR[month - 1]} {year}',
        key_terms=[bank, MONTHS_FR[month - 1], str(year), 'RELEVE'],
        lines=lines,
    )


# ------------------------------------------------------------ rental contracts

def rental_contract(rng: random.Random, year: int, city: str, language: str) -> GeneratedDoc:
    tenant = _person(rng)
    landlord = _person(rng)
    rent = rng.randrange(2500, 9000, 500)
    street = rng.choice(['rue Oued Ziz', 'avenue Hassan II', 'rue Ibn Batouta',
                         'boulevard Zerktouni', 'rue Moulay Youssef'])
    number = rng.randint(2, 180)
    address = f'{number} {street}, {city}'
    month = rng.randint(1, 12)

    if language == 'ar':
        return GeneratedDoc(
            category='rental_contract',
            language='ar',
            date=_iso(year, month, 1),
            organisation='عقد كراء',
            parties=[tenant, landlord],
            amount=float(rent),
            currency='MAD',
            description=f'Arabic residential lease in {city}, {year}, rent {rent} MAD',
            key_terms=['عقد', 'كراء', str(year), str(rent)],
            lines=[
                Line('عقد كراء سكني', heading=True),
                Line(''),
                Line(f'حرر بمدينة {city} بتاريخ {_iso(year, month, 1)}'),
                Line(''),
                Line(f'بين المكري السيد {landlord} من جهة'),
                Line(f'والمكتري السيد {tenant} من جهة أخرى'),
                Line(''),
                Line(f'الفصل الأول : موضوع العقد. يكري المكري للمكتري الشقة الكائنة '
                     f'بـ {address} والمشتملة على {rng.randint(2, 5)} غرف ومطبخ وحمام.'),
                Line(''),
                Line(f'الفصل الثاني : مدة الكراء. حددت مدة هذا العقد في سنة واحدة تبتدئ '
                     f'من {_iso(year, month, 1)} وتتجدد تلقائيا ما لم يعبر أحد الطرفين '
                     f'عن رغبته في إنهائها بإشعار مسبق مدته شهران.'),
                Line(''),
                Line(f'الفصل الثالث : الوجيبة الكرائية. حددت الوجيبة الشهرية في مبلغ '
                     f'{rent} درهم تؤدى مقدما قبل اليوم الخامس من كل شهر.'),
                Line(''),
                Line(f'الفصل الرابع : الضمانة. أدى المكتري مبلغ {rent * 2} درهم كضمانة '
                     f'ترجع إليه عند انتهاء العقد بعد معاينة حالة المحل.'),
                Line(''),
                Line('الفصل الخامس : التزامات المكتري. يتعهد المكتري باستعمال المحل '
                     'للسكنى فقط وبأداء مصاريف الماء والكهرباء وبعدم إجراء أي تغيير '
                     'في بنية المحل دون موافقة كتابية من المكري.'),
                Line(''),
                Line('الفصل السادس : الإصلاحات. تقع على المكتري الإصلاحات المعتادة '
                     'والصيانة الجارية للمحل. أما الإصلاحات الكبرى المتعلقة بالبنية '
                     'والسطح والقنوات المدمجة والتجهيز الكهربائي العام فتبقى على عاتق '
                     'المكري إلا إذا نتجت عن سوء استعمال من طرف المكتري.'),
                Line(''),
                Line('الفصل السابع : التفويت والكراء من الباطن. لا يجوز للمكتري تفويت '
                     'هذا العقد ولا كراء المحل من الباطن كلا أو جزءا دون الحصول على '
                     'موافقة كتابية مسبقة من المكري. وكل تفويت مخالف لهذا الشرط يعد '
                     'باطلا ولا يحتج به على المكري.'),
                Line(''),
                Line('الفصل الثامن : إنهاء العقد. في حالة إخلال المكتري بأحد التزاماته '
                     'ولا سيما عدم أداء الوجيبة الكرائية لمدة شهرين متتاليين يحق للمكري '
                     'طلب فسخ العقد بعد إنذار يبقى بدون جدوى لمدة خمسة عشر يوما.'),
                Line(''),
                Line('الفصل التاسع : التأمين. يلتزم المكتري بالإدلاء عند إبرام هذا العقد '
                     'وفي كل سنة بعقد تأمين يغطي المخاطر الكرائية وخاصة الحريق '
                     'والانفجار وأضرار المياه.'),
                Line(''),
                Line('الفصل العاشر : فض النزاعات. كل خلاف ينشأ عن تفسير أو تنفيذ هذا '
                     'العقد تختص به المحكمة الابتدائية الواقع بدائرتها المحل المكرى.'),
                Line(''),
                Line('حرر هذا العقد في نظيرين أصليين تسلم كل طرف نظيره.'),
            ],
        )

    return GeneratedDoc(
        category='rental_contract',
        language='fr',
        date=_iso(year, month, 1),
        organisation='Contrat de bail',
        parties=[tenant, landlord],
        amount=float(rent),
        currency='MAD',
        description=f'French residential lease in {city}, {year}, rent {rent} MAD, {address}',
        key_terms=['bail', city, str(year), str(rent)],
        lines=[
            Line('CONTRAT DE BAIL A USAGE D\'HABITATION', heading=True),
            Line(''),
            Line(f'Fait a {city}, le {_iso(year, month, 1)}'),
            Line(''),
            Line('ENTRE LES SOUSSIGNES'),
            Line(f'Le bailleur : Monsieur {landlord}, demeurant a {city}, ci-apres le Bailleur.'),
            Line(f'Le locataire : Monsieur {tenant}, ci-apres le Locataire.'),
            Line(''),
            Line(f'ARTICLE 1 - OBJET. Le Bailleur donne a bail au Locataire un appartement '
                 f'situe au {address}, compose de {rng.randint(2, 5)} pieces principales, '
                 f'une cuisine equipee et une salle de bain, d\'une superficie de '
                 f'{rng.randint(55, 165)} metres carres.'),
            Line(''),
            Line(f'ARTICLE 2 - DUREE. Le present bail est consenti pour une duree d\'un an '
                 f'a compter du {_iso(year, month, 1)}, renouvelable par tacite reconduction '
                 f'sauf denonciation par l\'une des parties avec un preavis de deux mois.'),
            Line(''),
            Line(f'ARTICLE 3 - LOYER. Le loyer mensuel est fixe a {rent} dirhams, payable '
                 f'd\'avance avant le cinq de chaque mois entre les mains du Bailleur.'),
            Line(''),
            Line(f'ARTICLE 4 - DEPOT DE GARANTIE. Le Locataire verse la somme de '
                 f'{rent * 2} dirhams a titre de depot de garantie, restituee en fin de '
                 f'bail apres etat des lieux de sortie et deduction des reparations '
                 f'locatives eventuelles.'),
            Line(''),
            Line('ARTICLE 5 - OBLIGATIONS DU LOCATAIRE. Le Locataire s\'engage a occuper '
                 'les lieux paisiblement, a usage exclusif d\'habitation, a regler les '
                 'charges d\'eau et d\'electricite, a souscrire une assurance habitation, '
                 'et a ne proceder a aucune modification de la structure sans accord ecrit.'),
            Line(''),
            Line('ARTICLE 6 - ETAT DES LIEUX. Un etat des lieux contradictoire est etabli '
                 'a l\'entree et annexe au present contrat. A defaut, le Locataire est '
                 'repute avoir recu les lieux en bon etat de reparations locatives.'),
            Line(''),
            Line('ARTICLE 7 - CHARGES ET REPARATIONS. Les reparations locatives et '
                 'l\'entretien courant incombent au Locataire. Les grosses reparations '
                 'affectant la structure, la toiture, les canalisations encastrees ou '
                 'l\'installation electrique generale demeurent a la charge du Bailleur, '
                 'sauf lorsqu\'elles resultent d\'un defaut d\'entretien imputable au '
                 'Locataire. Le Locataire informe le Bailleur sans delai de tout '
                 'desordre necessitant une intervention.'),
            Line(''),
            Line('ARTICLE 8 - CESSION ET SOUS-LOCATION. Le Locataire ne peut ceder le '
                 'present bail ni sous-louer tout ou partie des lieux loues sans '
                 'l\'accord ecrit et prealable du Bailleur. Toute cession ou '
                 'sous-location consentie en violation de la presente clause est '
                 'inopposable au Bailleur et constitue un motif de resiliation.'),
            Line(''),
            Line('ARTICLE 9 - RESILIATION. En cas de manquement du Locataire a l\'une '
                 'quelconque de ses obligations, et notamment en cas de defaut de '
                 'paiement du loyer pendant deux mois consecutifs, le Bailleur peut '
                 'poursuivre la resiliation du bail apres mise en demeure demeuree '
                 'infructueuse pendant quinze jours.'),
            Line(''),
            Line('ARTICLE 10 - ASSURANCE. Le Locataire justifie a la signature du '
                 'present bail, puis a chaque echeance annuelle, d\'une police '
                 'd\'assurance couvrant les risques locatifs, notamment l\'incendie, '
                 'l\'explosion et les degats des eaux. A defaut de justification, le '
                 'Bailleur peut souscrire une police pour le compte du Locataire et '
                 'lui en refacturer le cout.'),
            Line(''),
            Line('ARTICLE 11 - ELECTION DE DOMICILE. Pour l\'execution des presentes, '
                 'les parties elisent domicile aux adresses indiquees en tete du '
                 'present contrat. Tout changement d\'adresse doit etre notifie a '
                 'l\'autre partie par lettre recommandee avec accuse de reception.'),
            Line(''),
            Line('ARTICLE 12 - LITIGES. Tout differend relatif a l\'interpretation ou a '
                 'l\'execution du present bail releve de la competence du tribunal de '
                 'premiere instance du lieu de situation de l\'immeuble.'),
            Line(''),
            Line('Fait en deux exemplaires originaux, chaque partie reconnaissant avoir '
                 'recu le sien.'),
        ],
    )


# --------------------------------------------------------------------- invoices

def vendor_invoice(rng: random.Random, vendor: str, year: int, month: int,
                   language: str) -> GeneratedDoc:
    customer = _person(rng)
    items = rng.sample([
        ('Chaise de bureau', 890), ('Lampe LED', 149), ('Cable HDMI 2m', 79),
        ('Bouilloire electrique', 249), ('Casque audio', 599), ('Tapis de course', 3490),
        ('Ventilateur', 329), ('Perceuse sans fil', 749), ('Sac a dos', 399),
        ('Ecran 24 pouces', 1790), ('Clavier mecanique', 690), ('Chaussures de sport', 549),
    ], rng.randint(2, 4))
    total = round(sum(price for _, price in items) * 1.2, 2)
    invoice_no = f'{year}-{rng.randint(10000, 99999)}'

    if language == 'en':
        return GeneratedDoc(
            category='invoice',
            language='en',
            date=_iso(year, month, rng.randint(3, 26)),
            organisation=vendor,
            parties=[customer],
            amount=total,
            currency='MAD',
            description=f'{vendor} purchase receipt, {MONTHS_EN[month - 1]} {year}, {total} MAD',
            key_terms=[vendor, str(year), invoice_no],
            lines=[
                Line(f'{vendor} - Sales Receipt', heading=True),
                Line(f'Invoice number {invoice_no}'),
                Line(f'Date of purchase: {_iso(year, month, 12)}'),
                Line(f'Customer: {customer}'),
                Line(''),
                Line('Items purchased'),
                *[Line(f'{name} - quantity 1 - {price} MAD') for name, price in items],
                Line(''),
                Line(f'Subtotal excluding tax: {round(total / 1.2, 2)} MAD'),
                Line(f'Value added tax at 20 percent: {round(total - total / 1.2, 2)} MAD'),
                Line(f'Total amount paid: {total} MAD'),
                Line(''),
                Line('Payment received by bank card. Goods may be exchanged within '
                     'thirty days of purchase upon presentation of this receipt, provided '
                     'the original packaging is intact. This receipt also serves as proof '
                     'of purchase for any warranty claim.'),
            ],
        )

    return GeneratedDoc(
        category='invoice',
        language='fr',
        date=_iso(year, month, rng.randint(3, 26)),
        organisation=vendor,
        parties=[customer],
        amount=total,
        currency='MAD',
        description=f'{vendor} receipt, {MONTHS_FR[month - 1]} {year}, {total} MAD',
        key_terms=[vendor, str(year), invoice_no],
        lines=[
            Line(f'{vendor} - Ticket de caisse', heading=True),
            Line(f'Facture numero {invoice_no}'),
            Line(f'Date d\'achat : {_iso(year, month, 12)}'),
            Line(f'Client : {customer}'),
            Line(''),
            Line('Articles achetes'),
            *[Line(f'{name} - quantite 1 - {price} MAD') for name, price in items],
            Line(''),
            Line(f'Total hors taxe : {round(total / 1.2, 2)} MAD'),
            Line(f'TVA 20 pour cent : {round(total - total / 1.2, 2)} MAD'),
            Line(f'Montant total regle : {total} MAD'),
            Line(''),
            Line('Reglement effectue par carte bancaire. Les articles peuvent etre '
                 'echanges dans un delai de trente jours sur presentation de ce ticket '
                 'et dans leur emballage d\'origine. Ce document fait office de preuve '
                 'd\'achat pour toute demande de garantie.'),
        ],
    )


# --------------------------------------------------------------------- medical

def medical_letter(rng: random.Random, clinic: str, year: int, month: int,
                   language: str) -> GeneratedDoc:
    patient = _person(rng)
    doctor = f'Dr {rng.choice(SURNAMES)}'

    if language == 'ar':
        return GeneratedDoc(
            category='medical',
            language='ar',
            date=_iso(year, month, rng.randint(3, 27)),
            organisation=CLINICS_AR.get(clinic, clinic),
            parties=[patient, doctor],
            description=f'Arabic medical report from {clinic}, {MONTHS_EN[month - 1]} {year}',
            key_terms=['تقرير', 'طبي', str(year)],
            lines=[
                Line(CLINICS_AR.get(clinic, clinic), heading=True),
                Line(''),
                Line('تقرير طبي'),
                Line(f'اسم المريض : {patient}'),
                Line(f'تاريخ الفحص : {_iso(year, month, 14)}'),
                Line(f'الطبيب المعالج : {doctor}'),
                Line(''),
                Line('أشهد أنا الطبيب الموقع أدناه أنني فحصت المريض المذكور أعلاه '
                     'ولاحظت أعراضا تستوجب الراحة والمتابعة الطبية.'),
                Line(''),
                Line(f'التشخيص : {rng.choice(["التهاب حاد في الجهاز التنفسي", "إجهاد عضلي", "التهاب المعدة"])}'),
                Line(f'العلاج الموصوف : أدوية مضادة للالتهاب لمدة {rng.randint(5, 12)} أيام'),
                Line(f'مدة الراحة : {rng.randint(2, 8)} أيام ابتداء من تاريخ الفحص'),
                Line(''),
                Line('سلم هذا التقرير للمعني بالأمر بطلب منه لاستعماله عند الحاجة '
                     'ولدى الجهات المختصة.'),
            ],
        )

    return GeneratedDoc(
        category='medical',
        language='fr',
        date=_iso(year, month, rng.randint(3, 27)),
        organisation=clinic,
        parties=[patient, doctor],
        description=f'Medical certificate from {clinic}, {MONTHS_FR[month - 1]} {year}, by {doctor}',
        key_terms=[clinic.split()[-1], doctor.split()[-1], str(year)],
        lines=[
            Line(clinic, heading=True),
            Line(f'{rng.choice(["Anfa", "Agdal", "Gueliz"])}, Maroc'),
            Line(''),
            Line('CERTIFICAT MEDICAL'),
            Line(f'Patient : {patient}'),
            Line(f'Date de consultation : {_iso(year, month, 14)}'),
            Line(f'Medecin traitant : {doctor}'),
            Line(''),
            Line('Je certifie, soussigne medecin, avoir examine ce jour le patient '
                 'designe ci-dessus et avoir constate un etat necessitant du repos '
                 'et un suivi medical.'),
            Line(''),
            Line(f'Diagnostic : {rng.choice(["infection respiratoire aigue", "lombalgie mecanique", "gastrite"])}'),
            Line(f'Traitement prescrit : anti-inflammatoires pendant {rng.randint(5, 12)} jours'),
            Line(f'Repos prescrit : {rng.randint(2, 8)} jours a compter de ce jour'),
            Line(''),
            Line('Certificat delivre a la demande de l\'interesse et remis en main '
                 'propre pour servir et valoir ce que de droit.'),
        ],
    )


# ------------------------------------------------------------------ university

def university_doc(rng: random.Random, university: str, year: int, kind: str) -> GeneratedDoc:
    student = _person(rng)
    student_id = f'{year}{rng.randint(10000, 99999)}'
    city = UNIVERSITIES[university]

    if kind == 'transcript':
        courses = rng.sample([
            ('Analyse mathematique', 'MATH201'), ('Algorithmique avancee', 'CS310'),
            ('Physique quantique', 'PHY220'), ('Bases de donnees', 'CS340'),
            ('Economie generale', 'ECO110'), ('Anglais technique', 'ENG150'),
            ('Apprentissage automatique', 'CS420'), ('Reseaux informatiques', 'CS360'),
        ], 6)
        rows = [Line(f'{code}  {name}  note {round(rng.uniform(11, 18.5), 2)}/20  '
                     f'{rng.choice(["valide", "valide", "valide", "ajourne"])}')
                for name, code in courses]

        return GeneratedDoc(
            category='university',
            language='en',
            date=_iso(year, 7, 15),
            organisation=university,
            parties=[student],
            description=f'Academic transcript from {university}, academic year {year}',
            key_terms=[student_id, str(year), 'transcript'],
            lines=[
                Line(f'{university}', heading=True),
                Line(f'{city}, Morocco'),
                Line(''),
                Line('OFFICIAL ACADEMIC TRANSCRIPT'),
                Line(f'Student name: {student}'),
                Line(f'Student identification number: {student_id}'),
                Line(f'Academic year: {year} - {year + 1}'),
                Line(f'Programme: {rng.choice(["Computer Science", "Applied Mathematics", "Industrial Engineering"])}'),
                Line(''),
                Line('Course results'),
                *rows,
                Line(''),
                Line(f'Grade point average: {round(rng.uniform(12.4, 17.2), 2)} out of 20'),
                Line(f'Credits earned this year: {rng.randint(48, 60)} ECTS'),
                Line(''),
                Line('This transcript is issued by the registrar and bears the official '
                     'seal of the university. It is valid for administrative purposes '
                     'including visa applications, scholarship files and transfer '
                     'requests. Any alteration renders this document void.'),
            ],
        )

    return GeneratedDoc(
        category='university',
        language='fr',
        date=_iso(year, 10, 5),
        organisation=university,
        parties=[student],
        description=f'Enrollment certificate from {university} for {year}, needed for admin files',
        key_terms=[student_id, str(year), 'scolarite'],
        lines=[
            Line(f'{university}', heading=True),
            Line(f'{city}, Maroc'),
            Line(''),
            Line('ATTESTATION DE SCOLARITE'),
            Line(''),
            Line(f'Le President de l\'universite certifie que l\'etudiant {student}, '
                 f'inscrit sous le numero {student_id}, est regulierement inscrit '
                 f'au titre de l\'annee universitaire {year} - {year + 1}.'),
            Line(''),
            Line(f'Filiere : {rng.choice(["Genie Informatique", "Mathematiques Appliquees", "Genie Industriel"])}'),
            Line(f'Niveau : {rng.choice(["Licence 3", "Master 1", "Master 2"])}'),
            Line(''),
            Line('Cette attestation est delivree a l\'interesse pour servir et valoir '
                 'ce que de droit, notamment pour la constitution d\'un dossier de '
                 'demande de visa, d\'une demande de bourse, d\'une inscription en '
                 'residence universitaire ou de toute autre demarche administrative '
                 'exigeant une preuve d\'inscription.'),
        ],
    )


# ------------------------------------------------------------------- insurance

def insurance_policy(rng: random.Random, insurer: str, kind: str, year: int) -> GeneratedDoc:
    holder = _person(rng)
    policy = f'{rng.randint(100000, 999999)}-{rng.randint(10, 99)}'
    premium = rng.randrange(1800, 9600, 100)
    month = rng.randint(1, 12)
    kind_fr = {'auto': 'Automobile', 'health': 'Maladie', 'home': 'Habitation'}[kind]

    return GeneratedDoc(
        category='insurance',
        language='fr',
        date=_iso(year, month, 1),
        organisation=insurer,
        parties=[holder],
        amount=float(premium),
        currency='MAD',
        description=f'{insurer} {kind} insurance policy, renews {MONTHS_FR[month - 1]} {year + 1}',
        key_terms=[insurer.split()[0], policy, str(year)],
        lines=[
            Line(f'{insurer}', heading=True),
            Line(''),
            Line(f'POLICE D\'ASSURANCE {kind_fr.upper()}'),
            Line(f'Numero de police : {policy}'),
            Line(f'Souscripteur : {holder}'),
            Line(f'Date d\'effet : {_iso(year, month, 1)}'),
            Line(f'Date d\'echeance : {_iso(year + 1, month, 1)}'),
            Line(f'Prime annuelle : {premium} MAD'),
            Line(''),
            Line('Garanties souscrites'),
            *([Line('Responsabilite civile illimitee envers les tiers'),
               Line(f'Dommages collision avec franchise de {rng.randrange(1000, 4000, 500)} MAD'),
               Line('Vol et incendie du vehicule assure'),
               Line('Assistance et remorquage vingt-quatre heures sur vingt-quatre')]
              if kind == 'auto' else
              [Line(f'Hospitalisation prise en charge a {rng.choice([80, 90, 100])} pour cent'),
               Line('Consultations et actes de specialistes'),
               Line('Pharmacie sur prescription medicale'),
               Line('Analyses de laboratoire et imagerie medicale')]
              if kind == 'health' else
              [Line('Incendie, explosion et degats des eaux'),
               Line('Vol par effraction du contenu assure'),
               Line('Bris de glaces et de sanitaires'),
               Line('Responsabilite civile du chef de famille')]),
            Line(''),
            Line('La presente police est renouvelable par tacite reconduction a sa date '
                 'd\'echeance sauf resiliation notifiee par lettre recommandee au moins '
                 'trente jours avant celle-ci. Toute declaration inexacte ou omission '
                 'de nature a modifier l\'appreciation du risque entraine la nullite '
                 'du contrat conformement au code des assurances.'),
        ],
    )


# ------------------------------------------------------------------ employment

def employment_doc(rng: random.Random, year: int, kind: str) -> GeneratedDoc:
    employee = _person(rng)
    company = rng.choice(['OCP Group', 'Capgemini Maroc', 'Sofrecom Maroc', 'Managem'])
    salary = rng.randrange(6000, 24000, 500)
    month = rng.randint(1, 12)

    if kind == 'internship':
        return GeneratedDoc(
            category='employment',
            language='fr',
            date=_iso(year, month, 1),
            organisation=company,
            parties=[employee],
            amount=float(rng.randrange(2000, 5000, 500)),
            currency='MAD',
            description=f'Internship agreement at {company}, starting {MONTHS_FR[month - 1]} {year}',
            key_terms=[company.split()[0], str(year), 'stage'],
            lines=[
                Line(f'{company}', heading=True),
                Line('Direction des Ressources Humaines'),
                Line(''),
                Line('CONVENTION DE STAGE'),
                Line(f'Stagiaire : {employee}'),
                Line(f'Periode : du {_iso(year, month, 1)} au {_iso(year, min(month + 5, 12), 28)}'),
                Line(f'Service d\'affectation : {rng.choice(["Systemes d\'information", "Data et analytique", "Ingenierie procedes"])}'),
                Line(f'Gratification mensuelle : {rng.randrange(2000, 5000, 500)} MAD'),
                Line(''),
                Line('Le stagiaire est accueilli au sein de l\'entreprise dans le cadre '
                     'de sa formation academique. Il demeure sous statut etudiant et ne '
                     'peut se voir confier de taches relevant d\'un poste permanent. '
                     'Un tuteur est designe pour assurer son encadrement et evaluer '
                     'son travail en fin de periode.'),
                Line(''),
                Line('Le stagiaire s\'engage a respecter le reglement interieur et une '
                     'obligation stricte de confidentialite portant sur toute information '
                     'technique, commerciale ou financiere dont il aurait connaissance.'),
            ],
        )

    return GeneratedDoc(
        category='employment',
        language='fr',
        date=_iso(year, month, 20),
        organisation=company,
        parties=[employee],
        amount=float(salary),
        currency='MAD',
        description=f'Employment attestation from {company}, issued {MONTHS_FR[month - 1]} {year}',
        key_terms=[company.split()[0], str(year), 'travail'],
        lines=[
            Line(f'{company}', heading=True),
            Line('Direction des Ressources Humaines'),
            Line(''),
            Line('ATTESTATION DE TRAVAIL'),
            Line(''),
            Line(f'Nous soussignes, {company}, attestons que Monsieur {employee} '
                 f'est employe au sein de notre societe depuis le '
                 f'{_iso(year - rng.randint(1, 5), rng.randint(1, 12), 1)} '
                 f'en qualite de {rng.choice(["Ingenieur d\'etudes", "Analyste de donnees", "Chef de projet"])}.'),
            Line(''),
            Line(f'Salaire mensuel brut : {salary} MAD'),
            Line(f'Numero d\'immatriculation CNSS : {rng.randint(1000000, 9999999)}'),
            Line(''),
            Line('La presente attestation est delivree a l\'interesse a sa demande '
                 'pour servir et valoir ce que de droit, notamment aupres des '
                 'organismes bancaires et des representations consulaires.'),
            Line(''),
            Line(f'Fait a {rng.choice(["Casablanca", "Rabat", "Marrakech"])}, '
                 f'le {_iso(year, month, 20)}'),
        ],
    )


# ------------------------------------------------------------------------- tax

def tax_form(rng: random.Random, year: int) -> GeneratedDoc:
    taxpayer = _person(rng)
    income = rng.randrange(80000, 420000, 5000)
    tax = round(income * rng.uniform(0.12, 0.31), 2)

    return GeneratedDoc(
        category='tax',
        language='fr',
        date=_iso(year + 1, 3, 28),
        organisation='Direction Generale des Impots',
        parties=[taxpayer],
        amount=tax,
        currency='MAD',
        description=f'Income tax declaration for fiscal year {year}, filed {year + 1}',
        key_terms=['impots', str(year), 'revenu'],
        lines=[
            Line('Royaume du Maroc - Direction Generale des Impots', heading=True),
            Line(''),
            Line(f'DECLARATION ANNUELLE DU REVENU GLOBAL - EXERCICE {year}'),
            Line(f'Contribuable : {taxpayer}'),
            Line(f'Identifiant fiscal : {rng.randint(10000000, 99999999)}'),
            Line(f'Date de depot : {_iso(year + 1, 3, 28)}'),
            Line(''),
            Line('Revenus declares'),
            Line(f'Revenus salariaux nets imposables : {income} MAD'),
            Line(f'Revenus fonciers : {rng.randrange(0, 60000, 5000)} MAD'),
            Line(f'Deductions pour charges de famille : {rng.randrange(0, 1080, 360)} MAD'),
            Line(''),
            Line(f'Impot sur le revenu du : {tax} MAD'),
            Line(''),
            Line('Le contribuable certifie l\'exactitude des renseignements portes sur '
                 'la presente declaration. Toute omission ou inexactitude est passible '
                 'des sanctions prevues par le code general des impots. La declaration '
                 'doit etre deposee avant le trente et un mars de l\'annee suivant '
                 'celle de realisation des revenus.'),
        ],
    )


# ------------------------------------------------------- warranties, ids, misc

def warranty(rng: random.Random, year: int) -> GeneratedDoc:
    product, brand = rng.choice([
        ('Refrigerateur combine', 'Samsung'), ('Machine a laver', 'LG'),
        ('Televiseur 55 pouces', 'TCL'), ('Ordinateur portable', 'Lenovo'),
        ('Climatiseur split', 'Daikin'), ('Four encastrable', 'Bosch'),
    ])
    months = rng.choice([12, 24, 36])
    month = rng.randint(1, 12)

    return GeneratedDoc(
        category='warranty',
        language='fr',
        date=_iso(year, month, rng.randint(2, 27)),
        organisation=brand,
        description=f'{months}-month warranty certificate for {brand} {product}, {year}',
        key_terms=[brand, str(year), 'garantie'],
        lines=[
            Line(f'{brand} Maroc - Certificat de garantie', heading=True),
            Line(''),
            Line(f'Produit : {product}'),
            Line(f'Modele : {rng.choice(["XR", "GT", "Pro", "Plus"])}-{rng.randint(1000, 9999)}'),
            Line(f'Numero de serie : {rng.randint(10**9, 10**10 - 1)}'),
            Line(f'Date d\'achat : {_iso(year, month, 12)}'),
            Line(f'Duree de garantie : {months} mois'),
            Line(f'Fin de garantie : {_iso(year + months // 12, month, 12)}'),
            Line(''),
            Line('La garantie couvre les defauts de fabrication et les pannes '
                 'survenant dans des conditions normales d\'utilisation. Sont exclus '
                 'les dommages resultant d\'une mauvaise manipulation, d\'une '
                 'installation non conforme, d\'une surtension electrique, ou d\'une '
                 'intervention par un reparateur non agree.'),
            Line(''),
            Line('Pour toute intervention, presenter ce certificat accompagne du '
                 'ticket de caisse original au service apres-vente le plus proche.'),
        ],
    )


def admin_doc(rng: random.Random, year: int) -> GeneratedDoc:
    person = _person(rng)
    kind = rng.choice(['residence', 'birth', 'police_record'])

    if kind == 'residence':
        commune = rng.choice(['Casablanca', 'Rabat', 'Marrakech'])
        issued = _iso(year, rng.randint(1, 12), rng.randint(2, 27))
        return GeneratedDoc(
            category='admin',
            language='fr',
            date=issued,
            organisation='Commune Urbaine',
            parties=[person],
            description=f'Certificate of residence issued {year}, for administrative use',
            key_terms=['residence', str(year)],
            lines=[
                Line('Royaume du Maroc - Ministere de l\'Interieur', heading=True),
                Line(f'Commune Urbaine de {commune}'),
                Line(''),
                Line('CERTIFICAT DE RESIDENCE'),
                Line(''),
                Line(f'Le President du Conseil Communal certifie que Monsieur {person}, '
                     f'titulaire de la carte nationale d\'identite numero '
                     f'{rng.choice("ABCDEJKQ")}{rng.randint(100000, 999999)}, '
                     f'reside effectivement a l\'adresse declaree ci-dessous.'),
                Line(''),
                Line(f'Adresse : {rng.randint(2, 180)} rue {rng.choice(["Al Massira", "Ibn Rochd", "Al Wahda"])}'),
                Line(''),
                Line('Le present certificat est delivre a l\'interesse a sa demande '
                     'pour servir et valoir ce que de droit. Sa validite est de trois '
                     'mois a compter de sa date de delivrance.'),
                Line(''),
                Line(f'Fait a {commune}, le {issued}'),
            ],
        )

    if kind == 'birth':
        return GeneratedDoc(
            category='admin',
            language='ar',
            date=_iso(year, rng.randint(1, 12), rng.randint(2, 27)),
            organisation='مكتب الحالة المدنية',
            parties=[person],
            description=f'Arabic birth certificate extract issued {year}',
            key_terms=['الحالة', 'المدنية', str(year)],
            lines=[
                Line('المملكة المغربية - وزارة الداخلية', heading=True),
                Line('مكتب الحالة المدنية'),
                Line(''),
                Line('نسخة موجزة من رسم الولادة'),
                Line(''),
                Line(f'الاسم الكامل : {person}'),
                Line(f'رقم رسم الولادة : {rng.randint(100, 9999)}'),
                Line(f'سنة التسجيل : {year - rng.randint(18, 30)}'),
                Line(f'مكان الولادة : {rng.choice(["الدار البيضاء", "الرباط", "مراكش", "فاس"])}'),
                Line(f'تاريخ تسليم النسخة : {_iso(year, rng.randint(1, 12), rng.randint(2, 27))}'),
                Line(''),
                Line('سلمت هذه النسخة الموجزة بطلب من المعني بالأمر لاستعمالها في '
                     'المساطر الإدارية. وهي صالحة لمدة ثلاثة أشهر من تاريخ تسليمها.'),
            ],
        )

    return GeneratedDoc(
        category='admin',
        language='fr',
        date=_iso(year, rng.randint(1, 12), rng.randint(2, 27)),
        organisation='Ministere de la Justice',
        parties=[person],
        description=f'Criminal record extract (casier judiciaire) issued {year}',
        key_terms=['casier', 'judiciaire', str(year)],
        lines=[
            Line('Royaume du Maroc - Ministere de la Justice', heading=True),
            Line(''),
            Line('EXTRAIT DE CASIER JUDICIAIRE'),
            Line(''),
            Line(f'Nom et prenom : {person}'),
            Line(f'Numero de la carte nationale : {rng.choice("ABCDEJKQ")}{rng.randint(100000, 999999)}'),
            Line(f'Date de delivrance : {_iso(year, rng.randint(1, 12), 15)}'),
            Line(''),
            Line('Apres consultation du casier judiciaire national, il resulte que '
                 'l\'interesse n\'a fait l\'objet d\'aucune condamnation inscrite.'),
            Line(''),
            Line('Cet extrait est delivre pour les besoins d\'un dossier administratif '
                 'ou d\'une demande de visa. Il ne peut etre utilise a d\'autres fins '
                 'et sa validite est limitee a trois mois.'),
        ],
    )


def telecom_bill(rng: random.Random, operator: str, year: int, month: int) -> GeneratedDoc:
    client = _person(rng)
    amount = round(rng.uniform(99, 749), 2)

    return GeneratedDoc(
        category='misc',
        language='fr',
        date=_iso(year, month, 5),
        organisation=operator,
        parties=[client],
        amount=amount,
        currency='MAD',
        description=f'{operator} mobile and internet bill, {MONTHS_FR[month - 1]} {year}',
        key_terms=[operator.split()[0], MONTHS_FR[month - 1], str(year)],
        lines=[
            Line(f'{operator}', heading=True),
            Line(''),
            Line(f'FACTURE DE TELECOMMUNICATIONS - {MONTHS_FR[month - 1].upper()} {year}'),
            Line(f'Titulaire : {client}'),
            Line(f'Numero de ligne : 06{rng.randint(10000000, 99999999)}'),
            Line(f'Numero de compte : {rng.randint(100000, 999999)}'),
            Line(''),
            Line(f'Abonnement forfait mobile : {round(amount * 0.55, 2)} MAD'),
            Line(f'Abonnement internet fibre : {round(amount * 0.35, 2)} MAD'),
            Line(f'Consommation hors forfait : {round(amount * 0.10, 2)} MAD'),
            Line(f'Total a regler : {amount} MAD'),
            Line(f'Date limite de paiement : {_iso(year, month, 25)}'),
            Line(''),
            Line('Le detail des communications est disponible dans l\'espace client. '
                 'En cas de non paiement dans les delais, la ligne est suspendue puis '
                 'resiliee apres mise en demeure conformement aux conditions generales '
                 'd\'abonnement.'),
        ],
    )
