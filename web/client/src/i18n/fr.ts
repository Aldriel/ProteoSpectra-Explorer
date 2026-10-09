/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Texts of the interface in French: the same as the desktop application
 * (messages_fr.properties), adapted where the browser works differently (no folders, no "Quit").
 * This catalog defines the type of every other catalog.
 */
import { numberFormats } from './format';

const n = numberFormats('fr');

/** Chooses the wording for a count, like a MessageFormat "choice" (0, 1, more). */
const count = (value: number, zero: string, one: string, many: string) =>
  value === 0 ? zero : value === 1 ? one : many;

/** Arguments of the summary of a digested protein. */
export interface ProteinSummary {
  accession: string;
  entryName: string;
  proteinName: string;
  organism: string;
  database: string;
  length: number;
  massType: string;
  mass: string;
  peptides: number;
  protease: string;
  minLength: number;
  maxLength: number;
}

export const fr = {
  /** Language of the catalog, for number formats and the lang attribute of the page. */
  locale: 'fr',
  /** Name of the language written in itself, as listed in the Language menu. */
  languageName: 'Français',

  menu: {
    file: 'Fichier',
    open: 'Ouvrir un fichier de spectres…',
    samples: 'Ouvrir un fichier d’exemple',
    options: 'Options',
    language: 'Langue',
    textSize: 'Taille du texte',
    textSizes: ['Petite', 'Normale', 'Grande', 'Très grande'],
    zoomIn: 'Agrandir le texte',
    zoomOut: 'Réduire le texte',
    zoomReset: 'Taille de texte par défaut',
    help: 'Aide',
    guide: 'Guide d’utilisation',
    shortcuts: 'Raccourcis clavier',
    uniprot: 'Site web UniProt',
    pride: 'Site web PRIDE Archive',
    proforma: 'Notation ProForma (PSI)',
    about: 'À propos de ProteoSpectra-Explorer',
  },

  tab: { peptide: 'Peptide', spectra: 'Spectres', uniprot: 'UniProt', pride: 'PRIDE' },

  peptide: {
    sequence: 'Séquence (ProForma)',
    sequencePrompt: 'ex. PEPTIDE ou [Acetyl]-PEPM[Oxidation]TIDEK',
    calculate: 'Calculer',
    maxCharge: 'Charge max',
    massType: 'Masse',
    modifications: 'Modifications :',
    carbamidomethyl: 'Carbamidométhylation (C)',
    oxidation: 'Oxydation (M)',
    phospho: 'Phosphorylation (S, T, Y)',
    deamidation: 'Désamidation (N, Q)',
    decoy: 'Leurre :',
    reverse: 'Inverser',
    shuffle: 'Mélanger',
    ions: 'Ions :',
    placeholder: 'Tapez une séquence ci-dessus puis cliquez sur « Calculer ».',
    summary: (proForma: string, length: number, massType: string, mass: string) =>
      `${proForma}   |   ${n.integer(length)} résidus   |   masse ${massType} neutre : ${mass} Da`,
  },

  column: {
    ion: 'Ion',
    series: 'Série',
    theoreticalMz: 'm/z théorique',
    observedMz: 'm/z observée',
    errorPpm: 'Erreur (ppm)',
    intensity: 'Intensité',
    peptide: 'Peptide',
    start: 'Début',
    end: 'Fin',
    missed: 'CM',
    neutralMass: 'Masse neutre',
  },

  spectra: {
    open: 'Ouvrir un fichier…',
    parseMode: 'Lecture',
    usi: 'USI PRIDE',
    usiPrompt: 'mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:VLHPLEGAVVIIFK/2',
    download: 'Télécharger',
    placeholder:
      'Aucun spectre.\nOuvrez un fichier, ou essayez un exemple (les autres sont dans le ' +
      'menu Fichier) :',
    peptide: 'Peptide',
    peptidePrompt: 'vide = peptide identifié du spectre',
    tolerance: 'Tolérance',
    annotate: 'Annoter',
    annotationPlaceholder:
      'Sélectionnez un spectre puis cliquez sur « Annoter » pour retrouver ses ions b et y.',
    issues: 'Problèmes de lecture',
    noIssues: 'Aucun problème de lecture.',
    details: (id: string, msLevel: string, precursor: string, rt: string, peaks: number) =>
      `${id}  |  MS${msLevel}  |  précurseur ${precursor}  |  TR ${rt}  |  ${n.integer(peaks)} pics`,
    issue: (severity: string, location: string, message: string) =>
      `${severity}  ${location} : ${message}`,
    fullView: 'Vue complète',
    chartHint:
      'Glissez sur le graphique pour zoomer, ou utilisez la molette. Double-cliquez pour revoir ' +
      'tout le spectre. Cliquez sur un pic pour afficher ses détails.',
    peakInfo: (mz: string, intensity: string, percent: string, match: string) =>
      `Pic sélectionné : m/z ${mz}  |  intensité ${intensity} (${percent} % du pic de base)  |  ${match}`,
    peakMatched: (label: string, mz: string, error: string) =>
      `${label} : m/z théorique ${mz}, erreur ${error} ppm`,
    peakUnmatched: 'non expliqué par l’annotation',
    peakNotAnnotated: 'cliquez sur « Annoter » pour savoir quel ion l’explique',
    compare: 'Comparer à',
    openReference: 'Autre fichier…',
    theoretical: 'Spectre théorique',
    clearReference: 'Retirer',
    noReference: 'aucun spectre de référence',
    theoreticalName: (peptide: string) => `Spectre théorique de ${peptide}`,
    similarity: (cosine: number, matched: number, tolerance: number, unit: string) =>
      `Similarité cosinus avec la référence : ${n.fixed(cosine, 3)}  |  ` +
      count(matched, 'aucun pic apparié', '1 pic apparié', `${n.integer(matched)} pics appariés`) +
      ` (tolérance ${n.upTo(tolerance, 3)} ${unit})`,
  },

  chart: {
    intensity: 'Intensité',
    peaks: 'Pics',
    bIons: 'Ions b',
    yIons: 'Ions y',
    reference: 'Référence',
    label: (id: string) => `Spectre ${id}`,
  },

  comparisonMode: { MIRROR: 'Miroir', OVERLAY: 'Superposition' },
  issue: { ERROR: 'ERREUR', WARNING: 'AVERTISSEMENT' },

  uniprot: {
    accession: 'Accession',
    enzyme: 'Enzyme',
    missedCleavages: 'Clivages manqués',
    query: 'Interroger UniProt',
    placeholder:
      'Saisissez une accession UniProtKB (ex. P69905) puis cliquez sur « Interroger UniProt ».',
    summary: (s: ProteinSummary) =>
      `${s.accession} (${s.entryName}) — ${s.proteinName} — ${s.organism} [${s.database}]\n` +
      `${n.integer(s.length)} acides aminés, masse ${s.massType} : ${s.mass} Da — ` +
      `${n.integer(s.peptides)} peptides (${s.protease}, ${n.integer(s.minLength)} à ` +
      `${n.integer(s.maxLength)} résidus)`,
    length: 'Longueur',
    lengthTo: 'à',
    massUnavailable: 'non calculable',
    skipped: (peptides: number) =>
      `${n.integer(peptides)} peptide(s) exclu(s) car contenant des résidus ambigus.`,
  },

  pride: {
    search: 'Rechercher',
    searchPrompt: 'PXD000001 ou mots-clés, ex. hemoglobin human',
    searchButton: 'Rechercher',
    results: (first: number, last: number, keywords: string) =>
      `Projets ${n.integer(first)} à ${n.integer(last)} pour « ${keywords} »`,
    noResultsFor: (keywords: string) => `Aucun projet ne correspond à « ${keywords} ».`,
    resultsPlaceholder:
      'Tapez des mots-clés ou une accession de projet (PXD…) puis cliquez sur « Rechercher ».',
    page: (page: number) => `Page ${n.integer(page)}`,
    previous: '◀ Précédente',
    next: 'Suivante ▶',
    columnAccession: 'Accession',
    columnTitle: 'Titre',
    columnPublished: 'Publié le',
    placeholder: 'Choisissez un projet dans les résultats, ou tapez son accession (ex. PXD000001).',
    doi: 'DOI',
    dates: 'Dates',
    datesValue: (submitted: string, published: string) =>
      `soumis le ${submitted}, publié le ${published}`,
    organisms: 'Organismes',
    instruments: 'Instruments',
    keywords: 'Mots-clés',
    submitters: 'Soumetteurs',
    openWebPage: 'Voir le projet sur le site PRIDE',
    files: (files: number) => `Fichiers du projet (${n.integer(files)})`,
    filesTitle: 'Fichiers du projet',
    readableOnly: 'Seulement les fichiers lisibles',
    columnFile: 'Fichier',
    columnCategory: 'Type',
    columnSize: 'Taille',
    noFiles: 'Aucun fichier.',
    download: 'Ouvrir dans l’onglet Spectres',
    save: 'Télécharger…',
    cancel: 'Annuler',
    fileHint:
      '« Ouvrir dans l’onglet Spectres » (ou un double-clic) télécharge un fichier MGF ou JSON, ' +
      'en noir, et affiche ses spectres. « Télécharger… » enregistre n’importe quel fichier, y ' +
      'compris les fichiers grisés (RAW, mzML…), dans le dossier de téléchargement du navigateur.',
  },

  size: { units: ['o', 'Ko', 'Mo', 'Go', 'To'], unknown: 'taille inconnue' },

  download: {
    title: 'Fichier volumineux',
    header: (name: string, size: string) => `« ${name} » pèse ${size}.`,
    message:
      'Le téléchargement peut prendre plusieurs minutes, et la lecture de nombreux spectres ' +
      'demande beaucoup de mémoire. Vous pourrez l’interrompre avec le bouton « Annuler ».',
    confirm: 'Télécharger',
  },

  tip: {
    tabHelp: 'Aide sur cet onglet (F1)',
    sequence:
      'Lettres à un caractère. Modifications entre crochets après le résidu : M[Oxidation], ' +
      'S[Phospho], S[+79.966]. N-terminal : [Acetyl]-PEPTIDE.',
    maxCharge: 'Nombre d’états de charge [M+zH] listés (1 à 10).',
    massType:
      'Monoisotopique : isotope le plus léger (spectres haute résolution). Moyenne : abondances ' +
      'isotopiques naturelles.',
    carbamidomethyl: 'Ajoute +57,021 Da à chaque cystéine (alkylation à l’iodoacétamide).',
    oxidation:
      'Ajoute +15,995 Da à chaque méthionine. Les résidus déjà modifiés dans la séquence restent ' +
      'tels quels.',
    phospho:
      'Ajoute +79,966 Da (groupement phosphate HPO₃) à chaque sérine, thréonine et tyrosine. Les ' +
      'résidus déjà modifiés dans la séquence restent tels quels.',
    deamidation:
      'Ajoute +0,984 Da à chaque asparagine et glutamine. Les résidus déjà modifiés dans la ' +
      'séquence restent tels quels.',
    reverse:
      'Remplace la séquence par son leurre inversé (ACDEFGH devient HGFEDCA) et le calcule. Le ' +
      'leurre a la même masse que le peptide d’origine ; seuls ses fragments changent.',
    shuffle:
      'Remplace la séquence par un leurre aux résidus mélangés au hasard et le calcule. Chaque ' +
      'clic donne un nouvel ordre ; la masse reste celle du peptide d’origine.',
    openReference:
      'Ouvre un second fichier MGF ou JSON dont les spectres servent de référence. Le spectre ' +
      'identifié avec le même peptide que le spectre sélectionné est choisi d’office.',
    theoretical:
      'Calcule le spectre théorique (ions b et y) du peptide saisi ci-dessus, ou du peptide ' +
      'identifié du spectre si le champ est vide, et le compare au spectre sélectionné.',
    reference:
      'Spectre comparé au spectre sélectionné. Un clic droit sur un spectre de la liste permet ' +
      'aussi de l’utiliser comme référence.',
    comparisonMode:
      'Miroir : la référence est dessinée vers le bas. Superposition : les deux spectres vers le ' +
      'haut. La référence est mise à l’échelle du pic de base du spectre.',
    clearReference: 'Ne compare plus le spectre sélectionné à une référence.',
    ions: 'Séries d’ions fragments : a et b (N-terminaux), y (C-terminaux).',
    open:
      'Ouvre un fichier de spectres JSON ou MGF, compressé (.gz) ou non. Le chargement remplace ' +
      'la liste actuelle.',
    parseMode:
      'Tolérant : les entrées invalides sont ignorées et signalées. Strict : la première erreur ' +
      'rejette tout le fichier. À choisir avant d’ouvrir le fichier.',
    usi:
      'Universal Spectrum Identifier d’un spectre public, ex. ' +
      'mzspec:PXD000561:…:scan:17555:VLHPLEGAVVIIFK/2',
    annotationPeptide:
      'Peptide à tester sur le spectre. Laissez vide pour utiliser l’identification du spectre.',
    tolerance:
      'Écart maximal entre m/z observée et théorique : 10 à 20 ppm pour un Orbitrap, environ ' +
      '0,5 Da pour une trappe ionique.',
    annotate:
      'Recherche dans le spectre les pics correspondant aux ions b et y théoriques du peptide.',
    digestTable:
      'Double-cliquez sur un peptide pour le calculer dans l’onglet Peptide. Clic droit : annoter ' +
      'le spectre sélectionné avec ce peptide, ou copier sa séquence.',
    spectrumList:
      'Clic droit sur un spectre identifié : calculer son peptide dans l’onglet Peptide, ou ' +
      'copier sa séquence.',
    fullView: 'Affiche toute la plage de m/z du spectre (comme un double-clic sur le graphique).',
    accession:
      'Accession UniProtKB, ex. P69905 (hémoglobine α), P02769 (albumine bovine), P69905-2 ' +
      '(isoforme).',
    enzyme: 'Enzyme utilisée pour la digestion in silico.',
    missedCleavages: 'Nombre maximal de sites de coupure internes non coupés par peptide.',
    peptideLength:
      'Longueur des peptides conservés, en résidus. 6 à 30 convient à la trypsine ; élargissez ' +
      'le maximum pour Lys-C ou Glu-C, qui produisent des peptides plus longs.',
    prideSearch:
      'Accession d’un projet (PXD suivi de 6 chiffres) pour l’ouvrir directement, ou mots-clés ' +
      '(titre, espèce, instrument…) pour chercher.',
    projectTable: 'Cliquez sur un projet pour afficher sa fiche et ses fichiers.',
    fileTable:
      'Double-cliquez sur un fichier MGF ou JSON pour le télécharger et l’ouvrir dans l’onglet ' +
      'Spectres. Les fichiers grisés ne sont pas lisibles.',
    readableOnly: 'Masque les fichiers que l’application ne sait pas lire (RAW, mzML, résultats…).',
    download:
      'Télécharge le fichier sélectionné puis affiche ses spectres. Une confirmation est ' +
      'demandée au-delà de 50 Mo.',
    save:
      'Enregistre le fichier sélectionné, quel que soit son format, dans le dossier de ' +
      'téléchargement du navigateur. Une confirmation est demandée au-delà de 50 Mo.',
    cancelDownload: 'Interrompt le téléchargement en cours.',
  },

  action: {
    calculatePeptide: 'Calculer dans l’onglet Peptide',
    annotateSpectrum: 'Annoter le spectre sélectionné avec ce peptide',
    copySequence: 'Copier la séquence',
    useAsReference: 'Utiliser comme spectre de référence',
  },

  massType: { MONOISOTOPIC: 'Monoisotopique', AVERAGE: 'Moyenne' },
  parseMode: { LENIENT: 'Tolérant', STRICT: 'Strict' },
  toleranceUnit: { PPM: 'ppm', DA: 'Da' },
  protease: {
    TRYPSIN: 'Trypsine',
    TRYPSIN_P: 'Trypsine/P (coupe aussi devant P)',
    LYS_C: 'Lys-C',
    ARG_C: 'Arg-C',
    GLU_C: 'Glu-C',
  },

  status: {
    ready: 'Prêt. Ouvrez l’aide avec F1.',
    peptideCalculated: (peptide: string) => `Peptide ${peptide} calculé.`,
    decoy: {
      REVERSE: (peptide: string) =>
        `Leurre inversé ${peptide} calculé : même masse que le peptide d’origine, fragments ` +
        'différents.',
      SHUFFLE: (peptide: string) =>
        `Leurre mélangé ${peptide} calculé : même masse que le peptide d’origine, fragments ` +
        'différents.',
    },
    referenceFile: (spectra: number, file: string) =>
      count(
        spectra,
        'Aucun spectre de référence lu',
        '1 spectre de référence lu',
        `${n.integer(spectra)} spectres de référence lus`,
      ) + ` dans ${file}.`,
    referenceSpectrum: (id: string) => `Spectre ${id} utilisé comme référence.`,
    referenceTheoretical: (id: string) => `Spectre théorique de ${id} utilisé comme référence.`,
    reading: (file: string) => `Lecture de ${file}…`,
    downloadingSpectrum: 'Téléchargement du spectre depuis PRIDE…',
    annotated: (peaks: number, coverage: number) =>
      `${n.integer(peaks)} pics annotés (${n.percent(coverage)} des ions théoriques retrouvés).`,
    queryingUniProt: 'Interrogation d’UniProt…',
    digested: (accession: string, peptides: number) =>
      `${accession} : ${n.integer(peptides)} peptides.`,
    queryingPride: 'Interrogation de PRIDE…',
    searchingPride: (query: string) => `Recherche de « ${query} » dans PRIDE…`,
    prideResults: (projects: number, query: string) =>
      (projects === 1 ? '1 projet trouvé' : `${n.integer(projects)} projets trouvés`) +
      ` pour « ${query} ».`,
    prideNoResults: (query: string) => `Aucun projet trouvé pour « ${query} ».`,
    listingFiles: (accession: string) => `Liste des fichiers du projet ${accession}…`,
    projectLoaded: (accession: string, files: number) =>
      `Projet ${accession} chargé : ` +
      count(files, 'aucun fichier', '1 fichier', `${n.integer(files)} fichiers`) +
      '.',
    checkingSize: (file: string) => `Vérification de la taille de ${file}…`,
    downloadStarting: (file: string) => `Téléchargement de ${file}…`,
    downloading: (file: string, fraction: number) =>
      `Téléchargement de ${file} : ${n.percent(fraction)}`,
    downloadingUnknownSize: (file: string, megabytes: number) =>
      `Téléchargement de ${file} : ${n.fixed(megabytes, 1)} Mo reçus`,
    downloadCancelled: (file: string) => `Téléchargement de ${file} annulé.`,
    savedByBrowser: (file: string) =>
      `Téléchargement de ${file} confié au navigateur (dossier de téléchargement).`,
    spectraLoaded: (spectra: number) =>
      count(
        spectra,
        'Aucun spectre chargé',
        '1 spectre chargé',
        `${n.integer(spectra)} spectres chargés`,
      ) + '.',
    spectraLoadedWithIssues: (spectra: number, issues: number) =>
      count(
        spectra,
        'Aucun spectre chargé',
        '1 spectre chargé',
        `${n.integer(spectra)} spectres chargés`,
      ) +
      ', ' +
      (issues === 1 ? '1 problème de lecture' : `${n.integer(issues)} problèmes de lecture`) +
      ' (voir en bas de l’onglet).',
    error: (message: string) => `Erreur : ${message}`,
    errorDetail: (text: string, detail: string) => `${text} (${detail})`,
  },

  error: {
    noSpectrumSelected: 'sélectionnez d’abord un spectre dans la liste.',
    tolerance: 'la tolérance doit être un nombre positif.',
    noReferenceSpectrum: (file: string) => `${file} ne contient aucun spectre lisible.`,
    remoteNotFound: 'accession introuvable',
    remoteStatus: 'le service distant a renvoyé une erreur',
    network: 'service injoignable, vérifiez votre connexion Internet',
    parsing: 'données invalides',
    invalidInput: 'saisie invalide',
    invalidState: 'opération impossible',
    unexpected: 'erreur inattendue',
    tooLarge: 'fichier trop volumineux',
    server: 'serveur injoignable, lancez l’API Java (web/server) sur le port 7070',
    filesUnavailable: (accession: string) =>
      `les fichiers du projet ${accession} n’ont pas pu être listés`,
    prideQueryEmpty: 'tapez une accession de projet ou des mots-clés.',
  },

  dialog: { close: 'Fermer', cancel: 'Annuler' },

  help: {
    title: 'Guide d’utilisation',
    searchLabel: 'Rechercher :',
    searchPrompt: 'mot-clé, ex. tolérance, USI, trypsine…',
    clear: 'Effacer',
    results: (sections: number, occurrences: number) =>
      count(sections, 'aucune section', '1 section', `${n.integer(sections)} sections`) +
      ' · ' +
      count(
        occurrences,
        'aucune occurrence',
        '1 occurrence',
        `${n.integer(occurrences)} occurrences`,
      ),
    none: (query: string) => `Aucune section du guide ne contient « ${query} ».`,
  },

  about: {
    title: (name: string) => `À propos de ${name}`,
    version: (version: string) => `Version ${version}`,
    description:
      'Boîte à outils de protéomique : calcul des masses théoriques des peptides, lecture et ' +
      'annotation de spectres MS/MS, digestion in silico de protéines UniProt et consultation ' +
      'des projets PRIDE.',
    author: (name: string) => `Développé par ${name}, consultant en bio-informatique`,
    website: 'https://www.maximeethier.com',
    sources:
      'Sources des données : UniProtKB (UniProt Consortium), PRIDE Archive et API PROXI ' +
      '(EMBL-EBI).',
    references:
      'Masses des résidus : Unimod et ExPASy. Notation des peptides : ProForma 2.0 (HUPO-PSI).',
    disclaimer:
      'Destiné à la recherche et à l’enseignement uniquement. Ne pas utiliser pour un diagnostic.',
    runtime: (react: string) => `Application web · React ${react} · API Java`,
  },

  shortcuts: {
    keys: 'Touches',
    action: 'Action',
    tabs: 'Afficher l’onglet 1 à 4',
    enter: 'Valider le champ en cours (Calculer, Interroger…)',
    helpSearch: 'Rechercher dans le guide (panneau d’aide)',
    closeHelp: 'Effacer la recherche, puis fermer le guide',
  },
};
