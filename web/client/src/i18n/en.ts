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
 * Texts of the interface in English: the same as the desktop application
 * (messages_en.properties), adapted where the browser works differently.
 */
import { numberFormats } from './format';
import type { Messages } from './index';

const n = numberFormats('en');

/** Chooses the wording for a count, like a MessageFormat "choice" (0, 1, more). */
const count = (value: number, zero: string, one: string, many: string) =>
  value === 0 ? zero : value === 1 ? one : many;

export const en: Messages = {
  locale: 'en',
  languageName: 'English',

  menu: {
    file: 'File',
    open: 'Open spectrum file…',
    samples: 'Open a sample file',
    options: 'Options',
    language: 'Language',
    textSize: 'Text size',
    textSizes: ['Small', 'Normal', 'Large', 'Extra large'],
    zoomIn: 'Larger text',
    zoomOut: 'Smaller text',
    zoomReset: 'Default text size',
    help: 'Help',
    guide: 'User guide',
    shortcuts: 'Keyboard shortcuts',
    uniprot: 'UniProt website',
    pride: 'PRIDE Archive website',
    proforma: 'ProForma notation (PSI)',
    about: 'About ProteoSpectra-Explorer',
  },

  tab: { peptide: 'Peptide', spectra: 'Spectra', uniprot: 'UniProt', pride: 'PRIDE' },

  peptide: {
    sequence: 'Sequence (ProForma)',
    sequencePrompt: 'e.g. PEPTIDE or [Acetyl]-PEPM[Oxidation]TIDEK',
    calculate: 'Calculate',
    maxCharge: 'Max charge',
    massType: 'Mass',
    modifications: 'Modifications:',
    carbamidomethyl: 'Carbamidomethylation (C)',
    oxidation: 'Oxidation (M)',
    phospho: 'Phosphorylation (S, T, Y)',
    deamidation: 'Deamidation (N, Q)',
    decoy: 'Decoy:',
    reverse: 'Reverse',
    shuffle: 'Shuffle',
    ions: 'Ions:',
    placeholder: 'Type a sequence above, then click “Calculate”.',
    summary: (proForma, length, massType, mass) =>
      `${proForma}   |   ${n.integer(length)} residues   |   ${massType} neutral mass: ${mass} Da`,
  },

  column: {
    ion: 'Ion',
    series: 'Series',
    theoreticalMz: 'Theoretical m/z',
    observedMz: 'Observed m/z',
    errorPpm: 'Error (ppm)',
    intensity: 'Intensity',
    peptide: 'Peptide',
    start: 'Start',
    end: 'End',
    missed: 'MC',
    neutralMass: 'Neutral mass',
  },

  spectra: {
    open: 'Open file…',
    parseMode: 'Reading',
    usi: 'PRIDE USI',
    usiPrompt: 'mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:VLHPLEGAVVIIFK/2',
    download: 'Download',
    placeholder: 'No spectra.\nOpen a file, or try a sample (more in the File menu):',
    peptide: 'Peptide',
    peptidePrompt: 'empty = peptide identified for the spectrum',
    tolerance: 'Tolerance',
    annotate: 'Annotate',
    annotationPlaceholder: 'Select a spectrum, then click “Annotate” to find its b and y ions.',
    issues: 'Reading problems',
    noIssues: 'No reading problems.',
    details: (id, msLevel, precursor, rt, peaks) =>
      `${id}  |  MS${msLevel}  |  precursor ${precursor}  |  RT ${rt}  |  ${n.integer(peaks)} peaks`,
    issue: (severity, location, message) => `${severity}  ${location}: ${message}`,
    fullView: 'Full view',
    chartHint:
      'Drag across the chart to zoom, or use the mouse wheel. Double-click to see the whole ' +
      'spectrum again. Click a peak to show its details.',
    peakInfo: (mz, intensity, percent, match) =>
      `Selected peak: m/z ${mz}  |  intensity ${intensity} (${percent}% of base peak)  |  ${match}`,
    peakMatched: (label, mz, error) => `${label}: theoretical m/z ${mz}, error ${error} ppm`,
    peakUnmatched: 'not explained by the annotation',
    peakNotAnnotated: 'click “Annotate” to find which ion explains it',
    compare: 'Compare with',
    openReference: 'Other file…',
    theoretical: 'Theoretical spectrum',
    clearReference: 'Remove',
    noReference: 'no reference spectrum',
    theoreticalName: (peptide) => `Theoretical spectrum of ${peptide}`,
    similarity: (cosine, matched, tolerance, unit) =>
      `Cosine similarity with the reference: ${n.fixed(cosine, 3)}  |  ` +
      count(matched, 'no matched peak', '1 matched peak', `${n.integer(matched)} matched peaks`) +
      ` (tolerance ${n.upTo(tolerance, 3)} ${unit})`,
  },

  chart: {
    intensity: 'Intensity',
    peaks: 'Peaks',
    bIons: 'b ions',
    yIons: 'y ions',
    reference: 'Reference',
    label: (id) => `Spectrum ${id}`,
  },

  comparisonMode: { MIRROR: 'Mirror', OVERLAY: 'Overlay' },
  issue: { ERROR: 'ERROR', WARNING: 'WARNING' },

  uniprot: {
    accession: 'Accession',
    enzyme: 'Enzyme',
    missedCleavages: 'Missed cleavages',
    query: 'Query UniProt',
    placeholder: 'Enter a UniProtKB accession (e.g. P69905), then click “Query UniProt”.',
    summary: (s) =>
      `${s.accession} (${s.entryName}) — ${s.proteinName} — ${s.organism} [${s.database}]\n` +
      `${n.integer(s.length)} amino acids, ${s.massType} mass: ${s.mass} Da — ` +
      `${n.integer(s.peptides)} peptides (${s.protease}, ${n.integer(s.minLength)} to ` +
      `${n.integer(s.maxLength)} residues)`,
    length: 'Length',
    lengthTo: 'to',
    massUnavailable: 'not computable',
    skipped: (peptides) =>
      `${n.integer(peptides)} peptide(s) excluded because they contain ambiguous residues.`,
  },

  pride: {
    search: 'Search',
    searchPrompt: 'PXD000001 or keywords, e.g. hemoglobin human',
    searchButton: 'Search',
    results: (first, last, keywords) =>
      `Projects ${n.integer(first)} to ${n.integer(last)} for “${keywords}”`,
    noResultsFor: (keywords) => `No project matches “${keywords}”.`,
    resultsPlaceholder: 'Type keywords or a project accession (PXD…), then click “Search”.',
    page: (page) => `Page ${n.integer(page)}`,
    previous: '◀ Previous',
    next: 'Next ▶',
    columnAccession: 'Accession',
    columnTitle: 'Title',
    columnPublished: 'Published',
    placeholder: 'Choose a project in the results, or type its accession (e.g. PXD000001).',
    doi: 'DOI',
    dates: 'Dates',
    datesValue: (submitted, published) => `submitted on ${submitted}, published on ${published}`,
    organisms: 'Organisms',
    instruments: 'Instruments',
    keywords: 'Keywords',
    submitters: 'Submitters',
    openWebPage: 'View the project on the PRIDE website',
    files: (files) => `Project files (${n.integer(files)})`,
    filesTitle: 'Project files',
    readableOnly: 'Readable files only',
    columnFile: 'File',
    columnCategory: 'Type',
    columnSize: 'Size',
    noFiles: 'No files.',
    download: 'Open in the Spectra tab',
    save: 'Download…',
    cancel: 'Cancel',
    fileHint:
      '“Open in the Spectra tab” (or a double-click) downloads an MGF or JSON file, in black, ' +
      'and displays its spectra. “Download…” saves any file, greyed-out ones (RAW, mzML…) ' +
      'included, in the download folder of the browser.',
  },

  size: { units: ['B', 'KB', 'MB', 'GB', 'TB'], unknown: 'unknown size' },

  download: {
    title: 'Large file',
    header: (name, size) => `“${name}” is ${size}.`,
    message:
      'The download may take several minutes, and reading many spectra requires a lot of ' +
      'memory. You can stop it with the “Cancel” button.',
    confirm: 'Download',
  },

  tip: {
    tabHelp: 'Help on this tab (F1)',
    sequence:
      'One-letter codes. Modifications in brackets after the residue: M[Oxidation], ' +
      'S[Phospho], S[+79.966]. N-terminal: [Acetyl]-PEPTIDE.',
    maxCharge: 'Number of [M+zH] charge states listed (1 to 10).',
    massType:
      'Monoisotopic: lightest isotope (high-resolution spectra). Average: natural isotopic ' +
      'abundances.',
    carbamidomethyl: 'Adds +57.021 Da to every cysteine (iodoacetamide alkylation).',
    oxidation:
      'Adds +15.995 Da to every methionine. Residues already modified in the sequence are left ' +
      'as written.',
    phospho:
      'Adds +79.966 Da (HPO₃ phosphate group) to every serine, threonine and tyrosine. Residues ' +
      'already modified in the sequence are left as written.',
    deamidation:
      'Adds +0.984 Da to every asparagine and glutamine. Residues already modified in the ' +
      'sequence are left as written.',
    reverse:
      'Replaces the sequence by its reversed decoy (ACDEFGH becomes HGFEDCA) and calculates it. ' +
      'The decoy has the mass of the original peptide; only its fragments change.',
    shuffle:
      'Replaces the sequence by a decoy whose residues are randomly shuffled, and calculates ' +
      'it. Each click gives a new order; the mass stays that of the original peptide.',
    openReference:
      'Opens a second MGF or JSON file whose spectra are used as references. The spectrum ' +
      'identified with the same peptide as the selected spectrum is chosen first.',
    theoretical:
      'Computes the theoretical spectrum (b and y ions) of the peptide typed above, or of the ' +
      'peptide identified in the spectrum if the field is empty, and compares it.',
    reference:
      'Spectrum compared with the selected spectrum. Right-clicking a spectrum of the list also ' +
      'makes it the reference.',
    comparisonMode:
      'Mirror: the reference is drawn downwards. Overlay: both spectra upwards. The reference ' +
      'is scaled to the base peak of the spectrum.',
    clearReference: 'Stops comparing the selected spectrum with a reference.',
    ions: 'Fragment ion series: a and b (N-terminal), y (C-terminal).',
    open:
      'Opens a JSON or MGF spectrum file, compressed (.gz) or not. Loading replaces the current ' +
      'list.',
    parseMode:
      'Lenient: invalid entries are skipped and reported. Strict: the first error rejects the ' +
      'whole file. Choose before opening the file.',
    usi:
      'Universal Spectrum Identifier of a public spectrum, e.g. ' +
      'mzspec:PXD000561:…:scan:17555:VLHPLEGAVVIIFK/2',
    annotationPeptide:
      'Peptide to test against the spectrum. Leave empty to use the spectrum’s identification.',
    tolerance:
      'Maximum difference between observed and theoretical m/z: 10 to 20 ppm for an Orbitrap, ' +
      'about 0.5 Da for an ion trap.',
    annotate:
      'Finds the peaks of the spectrum matching the theoretical b and y ions of the peptide.',
    digestTable:
      'Double-click a peptide to compute it in the Peptide tab. Right-click: annotate the ' +
      'selected spectrum with this peptide, or copy its sequence.',
    spectrumList:
      'Right-click an identified spectrum: compute its peptide in the Peptide tab, or copy its ' +
      'sequence.',
    fullView: 'Shows the whole m/z range of the spectrum (same as a double-click on the chart).',
    accession:
      'UniProtKB accession, e.g. P69905 (hemoglobin alpha), P02769 (bovine albumin), P69905-2 ' +
      '(isoform).',
    enzyme: 'Enzyme used for the in silico digestion.',
    missedCleavages: 'Maximum number of uncleaved internal sites per peptide.',
    peptideLength:
      'Length of the peptides kept, in residues. 6 to 30 suits trypsin; raise the maximum for ' +
      'Lys-C or Glu-C, which produce longer peptides.',
    prideSearch:
      'A project accession (PXD followed by 6 digits) opens it directly; keywords (title, ' +
      'species, instrument…) search for projects.',
    projectTable: 'Click a project to display its details and files.',
    fileTable:
      'Double-click an MGF or JSON file to download it and open it in the Spectra tab. ' +
      'Greyed-out files cannot be read.',
    readableOnly: 'Hides the files that the application cannot read (RAW, mzML, results…).',
    download:
      'Downloads the selected file, then displays its spectra. Files larger than 50 MB ask for ' +
      'confirmation.',
    save:
      'Saves the selected file, whatever its format, in the download folder of the browser. ' +
      'Files larger than 50 MB ask for confirmation.',
    cancelDownload: 'Stops the current download.',
  },

  action: {
    calculatePeptide: 'Calculate in the Peptide tab',
    annotateSpectrum: 'Annotate the selected spectrum with this peptide',
    copySequence: 'Copy sequence',
    useAsReference: 'Use as reference spectrum',
  },

  massType: { MONOISOTOPIC: 'Monoisotopic', AVERAGE: 'Average' },
  parseMode: { LENIENT: 'Lenient', STRICT: 'Strict' },
  toleranceUnit: { PPM: 'ppm', DA: 'Da' },
  protease: {
    TRYPSIN: 'Trypsin',
    TRYPSIN_P: 'Trypsin/P (also cleaves before P)',
    LYS_C: 'Lys-C',
    ARG_C: 'Arg-C',
    GLU_C: 'Glu-C',
    ASP_N: 'Asp-N (cleaves before D)',
    CHYMOTRYPSIN: 'Chymotrypsin',
  },

  status: {
    ready: 'Ready. Press F1 for help.',
    peptideCalculated: (peptide) => `Peptide ${peptide} calculated.`,
    decoy: {
      REVERSE: (peptide) =>
        `Reversed decoy ${peptide} calculated: same mass as the original peptide, different ` +
        'fragments.',
      SHUFFLE: (peptide) =>
        `Shuffled decoy ${peptide} calculated: same mass as the original peptide, different ` +
        'fragments.',
    },
    referenceFile: (spectra, file) =>
      count(
        spectra,
        'No reference spectrum',
        '1 reference spectrum',
        `${n.integer(spectra)} reference spectra`,
      ) + ` read from ${file}.`,
    referenceSpectrum: (id) => `Spectrum ${id} used as reference.`,
    referenceTheoretical: (id) => `Theoretical spectrum of ${id} used as reference.`,
    reading: (file) => `Reading ${file}…`,
    downloadingSpectrum: 'Downloading the spectrum from PRIDE…',
    annotated: (peaks, coverage) =>
      `${n.integer(peaks)} peaks annotated (${n.percent(coverage)} of theoretical ions found).`,
    queryingUniProt: 'Querying UniProt…',
    digested: (accession, peptides) => `${accession}: ${n.integer(peptides)} peptides.`,
    queryingPride: 'Querying PRIDE…',
    searchingPride: (query) => `Searching PRIDE for “${query}”…`,
    prideResults: (projects, query) =>
      (projects === 1 ? '1 project found' : `${n.integer(projects)} projects found`) +
      ` for “${query}”.`,
    prideNoResults: (query) => `No project found for “${query}”.`,
    listingFiles: (accession) => `Listing the files of project ${accession}…`,
    projectLoaded: (accession, files) =>
      `Project ${accession} loaded: ` +
      count(files, 'no files', '1 file', `${n.integer(files)} files`) +
      '.',
    checkingSize: (file) => `Checking the size of ${file}…`,
    downloadStarting: (file) => `Downloading ${file}…`,
    downloading: (file, fraction) => `Downloading ${file}: ${n.percent(fraction)}`,
    downloadingUnknownSize: (file, megabytes) =>
      `Downloading ${file}: ${n.fixed(megabytes, 1)} MB received`,
    downloadCancelled: (file) => `Download of ${file} cancelled.`,
    savedByBrowser: (file) => `Download of ${file} handed to the browser (download folder).`,
    spectraLoaded: (spectra) =>
      count(
        spectra,
        'No spectra loaded',
        '1 spectrum loaded',
        `${n.integer(spectra)} spectra loaded`,
      ) + '.',
    spectraLoadedWithIssues: (spectra, issues) =>
      count(
        spectra,
        'No spectra loaded',
        '1 spectrum loaded',
        `${n.integer(spectra)} spectra loaded`,
      ) +
      ', ' +
      (issues === 1 ? '1 reading problem' : `${n.integer(issues)} reading problems`) +
      ' (see the bottom of the tab).',
    error: (message) => `Error: ${message}`,
    errorDetail: (text, detail) => `${text} (${detail})`,
  },

  error: {
    noSpectrumSelected: 'select a spectrum in the list first.',
    tolerance: 'the tolerance must be a positive number.',
    noReferenceSpectrum: (file) => `${file} contains no readable spectrum.`,
    remoteNotFound: 'accession not found',
    remoteStatus: 'the remote service returned an error',
    network: 'service unreachable, check your Internet connection',
    unavailable: 'PRIDE and ProteomeCentral are not responding, try again later',
    parsing: 'invalid data',
    invalidInput: 'invalid input',
    invalidState: 'operation not possible',
    unexpected: 'unexpected error',
    tooLarge: 'file too large',
    server: 'server unreachable, start the Java API (web/server) on port 7070',
    filesUnavailable: (accession) => `the files of project ${accession} could not be listed`,
    prideQueryEmpty: 'type a project accession or keywords.',
  },

  dialog: { close: 'Close', cancel: 'Cancel' },

  help: {
    title: 'User guide',
    searchLabel: 'Search:',
    searchPrompt: 'keyword, e.g. tolerance, USI, trypsin…',
    clear: 'Clear',
    results: (sections, occurrences) =>
      count(sections, 'no sections', '1 section', `${n.integer(sections)} sections`) +
      ' · ' +
      count(occurrences, 'no occurrences', '1 occurrence', `${n.integer(occurrences)} occurrences`),
    none: (query) => `No section of the guide contains “${query}”.`,
  },

  about: {
    title: (name) => `About ${name}`,
    version: (version) => `Version ${version}`,
    description:
      'Proteomics toolkit: theoretical peptide masses, reading and annotation of MS/MS spectra, ' +
      'in silico digestion of UniProt proteins and browsing of PRIDE projects.',
    author: (name) => `Developed by ${name}, bioinformatics consultant`,
    website: 'https://www.maximeethier.com/en',
    sources:
      'Data sources: UniProtKB (UniProt Consortium), PRIDE Archive and PROXI API (EMBL-EBI).',
    references: 'Residue masses: Unimod and ExPASy. Peptide notation: ProForma 2.0 (HUPO-PSI).',
    disclaimer: 'For research and teaching only. Not for diagnostic use.',
    runtime: (react) => `Web application · React ${react} · Java API`,
  },

  shortcuts: {
    keys: 'Keys',
    action: 'Action',
    tabs: 'Show tab 1 to 4',
    enter: 'Validate the current field (Calculate, Query…)',
    helpSearch: 'Search the guide (help panel)',
    closeHelp: 'Clear the search, then close the guide',
  },
};
