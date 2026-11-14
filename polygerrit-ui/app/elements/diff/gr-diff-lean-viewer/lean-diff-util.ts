/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, TemplateResult} from 'lit';
import {
  AlignedDiffRow,
  alignHomologousItems,
  alignMarkdownTokens,
  attachThreadsToRows,
  computeLinearExactAnchors,
  computeNormalizedEditDistance,
  computeSequenceDiff,
  DEFAULT_MAX_HOMOLOGOUS_DISTANCE,
  DiffBlockStatus,
  InlineDiffSegment,
  parseMarkdownBlocks,
  renderInlineMarkdown,
  renderInlineTextDiff,
  renderTokenToHtml,
} from '../gr-diff-markdown-viewer/markdown-diff-util';
import {CommentThread, PatchRange} from '../../../types/common';

export type LeanBlockType =
  | 'module_doc'
  | 'section_banner'
  | 'imports'
  | 'directive'
  | 'declaration'
  | 'comment';

export type LeanDeclKind =
  | 'theorem'
  | 'lemma'
  | 'example'
  | 'def'
  | 'abbrev'
  | 'structure'
  | 'class'
  | 'inductive'
  | 'coinductive'
  | 'instance'
  | 'axiom'
  | 'opaque';

export type LeanProofStrategy = 'tactic' | 'decide' | 'term';

export interface LeanCodeLine {
  lineNum: number;
  text: string;
}

export interface LeanStructureField {
  name: string;
  typeText: string;
  defaultValue?: string;
  comment?: string;
  lineNum: number;
  rawLine: string;
}

export interface LeanInductiveCtor {
  name: string;
  signatureText: string;
  comment?: string;
  lineNum: number;
  rawLine: string;
}

export interface LeanDeclHygiene {
  hasSorry: boolean;
  hasAdmit: boolean;
  isAxiom: boolean;
  isPartial: boolean;
  isUnsafe: boolean;
  hasNativeDecide: boolean;
}

export interface LeanDeclarationDetails {
  kind: LeanDeclKind;
  name: string;
  attributes: string[];
  modifiers: string[];
  docComment?: string;
  docStartLine?: number;
  docEndLine?: number;
  signatureLines: LeanCodeLine[];
  bodyLines: LeanCodeLine[];
  isTacticProof: boolean;
  proofStrategy?: LeanProofStrategy;
  fields?: LeanStructureField[];
  constructors?: LeanInductiveCtor[];
  derivingClause?: string[];
  isCompactDef: boolean;
  hygiene: LeanDeclHygiene;
}

export interface LeanBlock {
  id: string;
  type: LeanBlockType;
  raw: string;
  startLine: number;
  endLine: number;
  /** Markdown content for `module_doc`, or extracted title for `section_banner`. */
  markdownContent?: string;
  /** Import lines for `imports`. */
  importLines?: LeanCodeLine[];
  /** Directive keyword + text for `directive` (e.g., namespace, end, open). */
  directiveKind?: string;
  directiveLines?: LeanCodeLine[];
  /** Comment lines and license detection for `comment`. */
  commentLines?: LeanCodeLine[];
  isLicenseHeader?: boolean;
  /** Structured declaration metadata for `declaration`. */
  decl?: LeanDeclarationDetails;
}

export interface LeanAlignedCodeLine {
  status: 'unchanged' | 'added' | 'deleted' | 'modified' | 'placeholder';
  leftLine?: LeanCodeLine;
  rightLine?: LeanCodeLine;
  leftContent?: TemplateResult;
  rightContent?: TemplateResult;
}

export interface LeanAlignedField {
  status: 'unchanged' | 'added' | 'deleted' | 'modified';
  leftField?: LeanStructureField;
  rightField?: LeanStructureField;
  leftTypeHtml?: TemplateResult;
  rightTypeHtml?: TemplateResult;
  leftCommentHtml?: TemplateResult;
  rightCommentHtml?: TemplateResult;
}

export interface LeanDeclarationDiffData {
  docLeftHtml?: TemplateResult;
  docRightHtml?: TemplateResult;
  docChanged: boolean;
  signatureLines: LeanAlignedCodeLine[];
  signatureChanged: boolean;
  bodyLines: LeanAlignedCodeLine[];
  bodyChanged: boolean;
  fields?: LeanAlignedField[];
  fieldsChanged: boolean;
}

export interface AlignedLeanRow extends AlignedDiffRow {
  rowKey: string;
  status: DiffBlockStatus;
  leftBlock?: LeanBlock;
  rightBlock?: LeanBlock;
  moduleDocRows?: AlignedDiffRow[];
  bannerDiff?: {leftHtml: TemplateResult; rightHtml: TemplateResult};
  lineDiff?: LeanAlignedCodeLine[];
  declDiff?: LeanDeclarationDiffData;
}

export interface AlignedLeanRowWithThreads extends AlignedLeanRow {
  leftThreads: CommentThread[];
  rightThreads: CommentThread[];
}

export interface LeanFileCounts {
  theorems: number;
  definitions: number;
  types: number;
  instances: number;
  axioms: number;
  sorryCount: number;
  admitCount: number;
  partialCount: number;
  unsafeCount: number;
  nativeDecideCount: number;
}

export interface LeanDiffSummary {
  base: LeanFileCounts;
  revision: LeanFileCounts;
  hasBase: boolean;
  hasRevision: boolean;
  addedDeclarations: number;
  deletedDeclarations: number;
  modifiedDeclarations: number;
}

export type LeanTokenKind =
  | 'plain'
  | 'comment'
  | 'string'
  | 'keyword'
  | 'tactic'
  | 'sorry'
  | 'type'
  | 'operator'
  | 'bullet'
  | 'attribute'
  | 'number'
  | 'literal';

export interface LeanHighlightToken {
  text: string;
  kind: LeanTokenKind;
}

const LEAN_DECL_KEYWORDS = new Set<LeanDeclKind>([
  'theorem',
  'lemma',
  'example',
  'def',
  'abbrev',
  'structure',
  'class',
  'inductive',
  'coinductive',
  'instance',
  'axiom',
  'opaque',
]);

const LEAN_MODIFIER_KEYWORDS = new Set([
  'public',
  'private',
  'protected',
  'noncomputable',
  'partial',
  'unsafe',
  'scoped',
  'local',
  'nonrec',
  'meta',
]);

const LEAN_DIRECTIVE_KEYWORDS = new Set([
  'module',
  'namespace',
  'section',
  'end',
  'open',
  'variable',
  'variables',
  'universe',
  'universes',
  'set_option',
  'attribute',
  'export',
  'notation',
  'infix',
  'infixl',
  'infixr',
  'prefix',
  'postfix',
  'macro',
  'macro_rules',
  'syntax',
  'elab',
  'elab_rules',
  'declare_syntax_cat',
  'initialize',
  'builtin_initialize',
  'mutual',
  'deriving',
  '#check',
  '#eval',
  '#print',
  '#reduce',
  '#guard',
  '#guard_msgs',
  '#synth',
  '#where',
  '#exit',
]);

const LEAN_KEYWORDS = new Set([
  'module',
  'theorem',
  'lemma',
  'example',
  'def',
  'abbrev',
  'structure',
  'class',
  'inductive',
  'coinductive',
  'instance',
  'axiom',
  'opaque',
  'namespace',
  'section',
  'end',
  'import',
  'open',
  'variable',
  'variables',
  'universe',
  'universes',
  'where',
  'deriving',
  'extends',
  'public',
  'private',
  'protected',
  'noncomputable',
  'partial',
  'unsafe',
  'scoped',
  'local',
  'nonrec',
  'meta',
  'mutual',
  'set_option',
  'attribute',
  'export',
  'notation',
  'infix',
  'infixl',
  'infixr',
  'prefix',
  'postfix',
  'macro',
  'macro_rules',
  'syntax',
  'elab',
  'elab_rules',
  'termination_by',
  'decreasing_by',
  'partial_fixpoint',
  'coinductive_fixpoint',
  'inductive_fixpoint',
  'with',
  'let',
  'rec',
  'in',
  'if',
  'then',
  'else',
  'match',
  'fun',
  'do',
  'for',
  'return',
  'have',
  'show',
  'from',
  'by',
  'calc',
  'conv',
  'suffices',
  'try',
  'mut',
  'unless',
  'while',
  'break',
  'continue',
  'nomatch',
  'nofun',
  'throw',
  'catch',
  'finally',
  'forall',
  'exists',
  'at',
  'using',
]);

const LEAN_TACTICS = new Set([
  'intro',
  'intros',
  'rintro',
  'rw',
  'rewrite',
  'simp',
  'simp_all',
  'simp_wf',
  'dsimp',
  'simpa',
  'exact',
  'apply',
  'refine',
  'constructor',
  'cases',
  'rcases',
  'induction',
  'obtain',
  'choose',
  'unfold',
  'by_cases',
  'by_contra',
  'subst',
  'omega',
  'decide',
  'rfl',
  'contradiction',
  'funext',
  'ext',
  'congr',
  'ring',
  'ring_nf',
  'field_simp',
  'linarith',
  'nlinarith',
  'lia',
  'lra',
  'positivity',
  'aesop',
  'norm_num',
  'norm_cast',
  'push_neg',
  'split_ifs',
  'fin_cases',
  'interval_cases',
  'gcongr',
  'trivial',
  'assumption',
  'clear',
  'rename_i',
  'revert',
  'generalize',
  'specialize',
  'use',
  'left',
  'right',
  'split',
  'exfalso',
  'injection',
  'change',
  'set',
  'grind',
  'bv_decide',
  'decreasing_tactic',
  'decreasing_trivial',
  'enter',
  'lhs',
  'rhs',
  'arg',
  'only',
  'first',
  'repeat',
  'all_goals',
  'any_goals',
  'focus',
  'next',
  'case',
  'done',
  'tauto',
  'itauto',
  'ac_rfl',
  'extract_lets',
  'fun_prop',
  'nontriviality',
]);

const UNAMBIGUOUS_TACTICS = new Set([
  'decide',
  'omega',
  'rfl',
  'simp_wf',
  'all_goals',
  'any_goals',
  'bv_decide',
  'decreasing_tactic',
  'decreasing_trivial',
]);

const LEAN_SORRY_TOKENS = new Set([
  'sorry',
  'sorryAx',
  'admit',
  'native_decide',
  'ofReduceBool',
  'ofReduceNat',
]);

// Kernel soundness constant names that resolve to the exact same `Name` even
// when wrapped in French guillemets (`«sorryAx»`, `Lean.«ofReduceBool»`).
// Syntax keywords (`sorry`, `admit`, `native_decide`) are intentionally
// excluded because guillemets escape them into ordinary user identifiers.
const SOUNDNESS_GUILLEMET_NAMES = new Set([
  'sorryAx',
  'ofReduceBool',
  'ofReduceNat',
  'Lean.ofReduceBool',
  'Lean.ofReduceNat',
]);

const LEAN_BUILTIN_TYPES = new Set([
  'Prop',
  'Type',
  'Sort',
  'Nat',
  'Int',
  'Bool',
  'String',
  'Char',
  'Float',
  'Float32',
  'Unit',
  'PUnit',
  'Empty',
  'PEmpty',
  'Fin',
  'List',
  'Array',
  'ByteArray',
  'Vector',
  'Subarray',
  'Option',
  'Except',
  'ExceptT',
  'StateM',
  'ReaderM',
  'Id',
  'IO',
  'Task',
  'Thunk',
  'Name',
  'Expr',
  'Decidable',
  'DecidableEq',
  'Repr',
  'Hashable',
  'Inhabited',
  'Nonempty',
  'ToString',
  'LE',
  'LT',
  'BEq',
  'Ord',
  'Ordering',
  'Eq',
  'And',
  'Or',
  'Not',
  'True',
  'False',
  'UInt8',
  'UInt16',
  'UInt32',
  'UInt64',
  'USize',
  'Int8',
  'Int16',
  'Int32',
  'Int64',
  'ISize',
  'BitVec',
  'Prod',
  'Sum',
  'Subtype',
  'Sigma',
]);

const LEAN_LITERALS = new Set(['true', 'false', 'none', 'some', 'nil', 'cons']);

const UNICODE_OPERATORS = new Set([
  '∀',
  '∃',
  'λ',
  '→',
  '←',
  '↔',
  '⇒',
  '⇐',
  '⇔',
  '↦',
  '≤',
  '≥',
  '≠',
  '∧',
  '∨',
  '¬',
  '⊢',
  '⊥',
  '⊤',
  '⊔',
  '⊓',
  '∈',
  '∉',
  '⊆',
  '⊇',
  '⊂',
  '⊃',
  '∪',
  '∩',
  '∅',
  '⟨',
  '⟩',
  '×',
  '⊕',
  '⊗',
  '∘',
  '▸',
  '≡',
  '≈',
  '≃',
  '≅',
  '∣',
  '∑',
  '∏',
  '∫',
]);

const MULTI_CHAR_OPERATORS = [
  '<;>',
  '<<<',
  '>>>',
  '|||',
  '&&&',
  '^^^',
  ':=',
  '=>',
  '->',
  '<-',
  '<->',
  '<=',
  '>=',
  '!=',
  '==',
  '&&',
  '||',
  '::',
  '++',
  '|>',
  '<|',
  '..',
];

const IDENT_START_RE = /[\p{L}_#]/u;
const IDENT_PART_RE = /[\p{L}\p{N}_'?!₀-₉⁰-⁹ₐ-ₜᵢᵣᵤᵥ]/u;
const LEAN_CONTENT_TOKEN_RE = /[\p{L}\p{N}_]/u;
const LEAN_INTERP_STRING_PREFIXES = new Set(['s!', 'm!', 'f!']);
const CHAR_LITERAL_RE =
  /'(?:\\x[0-9a-fA-F]{2}|\\u[0-9a-fA-F]{4}|\\[^\n]|[^'\\\n])'/uy;
const NUMBER_LITERAL_RE =
  /(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|[0-9][0-9_]*(?:\.[0-9_]+)?)/y;

function isLeanContentToken(tok: string): boolean {
  return LEAN_CONTENT_TOKEN_RE.test(tok) || UNICODE_OPERATORS.has(tok);
}

function normalizeLeanSource(source: string): string {
  return source.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
}

function isIdentStart(ch: string): boolean {
  return IDENT_START_RE.test(ch);
}

function isIdentPart(ch: string): boolean {
  return IDENT_PART_RE.test(ch);
}

function getCharAt(text: string, i: number): {ch: string; len: number} {
  const cp = text.codePointAt(i);
  if (cp === undefined) return {ch: '', len: 0};
  return {ch: String.fromCodePoint(cp), len: cp > 0xffff ? 2 : 1};
}

function hasIdentPartBefore(text: string, i: number): boolean {
  if (i <= 0) return false;
  const prevCode = text.charCodeAt(i - 1);
  if (
    prevCode >= 0xdc00 &&
    prevCode <= 0xdfff &&
    i >= 2 &&
    text.charCodeAt(i - 2) >= 0xd800 &&
    text.charCodeAt(i - 2) <= 0xdbff
  ) {
    const cp = text.codePointAt(i - 2);
    return cp !== undefined && isIdentPart(String.fromCodePoint(cp));
  }
  return isIdentPart(text[i - 1]);
}

/**
 * Match a Lean 4 character literal (`'a'`, `'"'`, `'\''`, `'\n'`, `'\x41'`, `'\u0041'`, `'😀'`)
 * at index `i`, ensuring prime suffixes on identifiers (`hlt'`, `x'`) are never misread.
 */
function matchCharLiteral(text: string, i: number): number {
  if (text[i] !== "'" || hasIdentPartBefore(text, i)) return 0;
  CHAR_LITERAL_RE.lastIndex = i;
  const match = CHAR_LITERAL_RE.exec(text);
  return match ? match[0].length : 0;
}

/**
 * Match a Lean 4 raw string opener (`r"..."`, `r#"..."#`, `r##"..."##`) at index `i`.
 */
function matchRawStringOpen(
  text: string,
  i: number
): {hashes: number; contentStart: number} | null {
  if (text[i] !== 'r' || hasIdentPartBefore(text, i)) return null;
  let hashes = 0;
  while (i + 1 + hashes < text.length && text[i + 1 + hashes] === '#') {
    hashes++;
  }
  if (text[i + 1 + hashes] === '"') {
    return {hashes, contentStart: i + 2 + hashes};
  }
  return null;
}

/** Check whether `text` at index `i` closes a raw string with `hashes` trailing `#`s. */
function matchRawStringCloseAt(
  text: string,
  i: number,
  hashes: number
): boolean {
  if (text[i] !== '"') return false;
  for (let k = 1; k <= hashes; k++) {
    if (text[i + k] !== '#') return false;
  }
  return true;
}

/**
 * Find the matching `]` for `[` at `openIdx`, respecting nested brackets,
 * comments (`--` and nested `/- ... -/`), strings, raw strings, char literals,
 * and guillemet identifiers.
 */
function findMatchingBracket(text: string, openIdx: number): number {
  if (text[openIdx] !== '[') return -1;
  let depth = 0;
  let inString = false;
  for (let i = openIdx; i < text.length; i++) {
    const ch = text[i];
    if (inString) {
      if (ch === '\\') i++;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '-' && text[i + 1] === '-') {
      const nl = text.indexOf('\n', i + 2);
      if (nl < 0) return -1;
      i = nl;
      continue;
    }
    if (ch === '/' && text[i + 1] === '-') {
      let cDepth = 1;
      let j = i + 2;
      while (j < text.length && cDepth > 0) {
        if (text[j] === '/' && text[j + 1] === '-') {
          cDepth++;
          j += 2;
        } else if (text[j] === '-' && text[j + 1] === '/') {
          cDepth--;
          j += 2;
        } else {
          j++;
        }
      }
      i = j - 1;
      continue;
    }
    const rawOpen = matchRawStringOpen(text, i);
    if (rawOpen) {
      let j = rawOpen.contentStart;
      while (j < text.length) {
        if (matchRawStringCloseAt(text, j, rawOpen.hashes)) {
          j += rawOpen.hashes;
          break;
        }
        j++;
      }
      i = j;
      continue;
    }
    const charLen = matchCharLiteral(text, i);
    if (charLen > 0) {
      i += charLen - 1;
      continue;
    }
    if (ch === '«') {
      const closeGuillemet = text.indexOf('»', i + 1);
      i = closeGuillemet >= 0 ? closeGuillemet : text.length - 1;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === '[') depth++;
    else if (ch === ']') {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

/**
 * Incrementally scan a multi-line `@[...]` attribute in a single forward pass
 * without re-concatenating or rescanning prior lines. Tracks nested brackets,
 * block comments (`/- ... -/`), line comments (`--`), strings, raw strings,
 * char literals, and guillemet identifiers across line boundaries.
 */
function findMultiLineAttributeEnd(
  lines: string[],
  startRow: number,
  attrStartCol: number,
  openBracketCol: number,
  maxEndRow: number
): {
  closed: boolean;
  endRow: number;
  closeColExclusive: number;
  text: string;
  scannedLines: number;
} {
  let depth = 1;
  let blockCommentDepth = 0;
  let inString = false;
  let rawStringHashes = -1;
  let inGuillemet = false;
  const limitRow = Math.min(lines.length - 1, maxEndRow);

  for (let row = startRow; row <= limitRow; row++) {
    const line = lines[row];
    const len = line.length;
    let c = row === startRow ? openBracketCol + 1 : 0;

    while (c < len) {
      const ch = line[c];
      const next = c + 1 < len ? line[c + 1] : '';

      if (blockCommentDepth > 0) {
        if (ch === '/' && next === '-') {
          blockCommentDepth++;
          c += 2;
        } else if (ch === '-' && next === '/') {
          blockCommentDepth--;
          c += 2;
        } else {
          c++;
        }
        continue;
      }

      if (rawStringHashes >= 0) {
        if (matchRawStringCloseAt(line, c, rawStringHashes)) {
          c += 1 + rawStringHashes;
          rawStringHashes = -1;
        } else {
          c++;
        }
        continue;
      }

      if (inGuillemet) {
        if (ch === '»') {
          inGuillemet = false;
        }
        c++;
        continue;
      }

      if (inString) {
        if (ch === '\\' && c + 1 < len) {
          c += 2;
        } else if (ch === '"') {
          inString = false;
          c++;
        } else {
          c++;
        }
        continue;
      }

      if (ch === '-' && next === '-') {
        break;
      }

      if (ch === '/' && next === '-') {
        blockCommentDepth = 1;
        c += 2;
        continue;
      }

      const rawOpen = matchRawStringOpen(line, c);
      if (rawOpen) {
        rawStringHashes = rawOpen.hashes;
        c = rawOpen.contentStart;
        continue;
      }

      const charLen = matchCharLiteral(line, c);
      if (charLen > 0) {
        c += charLen;
        continue;
      }

      if (ch === '«') {
        inGuillemet = true;
        c++;
        continue;
      }

      if (ch === '"') {
        inString = true;
        c++;
        continue;
      }

      if (ch === '[') {
        depth++;
      } else if (ch === ']') {
        depth--;
        if (depth === 0) {
          const text =
            row === startRow
              ? line.slice(attrStartCol, c + 1)
              : [
                  lines[startRow].slice(attrStartCol),
                  ...lines.slice(startRow + 1, row),
                  line.slice(0, c + 1),
                ].join('\n');
          return {
            closed: true,
            endRow: row,
            closeColExclusive: c + 1,
            text,
            scannedLines: row - startRow + 1,
          };
        }
      }
      c++;
    }
  }

  return {
    closed: false,
    endRow: -1,
    closeColExclusive: -1,
    text: '',
    scannedLines: Math.max(1, limitRow - startRow + 1),
  };
}

/**
 * Strip Lean 4 comments (including nested block comments), string literals
 * (including raw `r#"..."#`, multi-line strings, and stack-aware interpolated
 * strings `s!"...{expr}..."`), char literals, and non-soundness
 * guillemet-escaped identifiers (`«...»`) while preserving exact 1:1 character
 * offsets and newlines.
 *
 * Expressions inside `{...}` of interpolated strings and guillemet-wrapped
 * kernel soundness names (`«sorryAx»`, `«ofReduceBool»`, etc.) are preserved so
 * hygiene analysis cannot be bypassed via quote-parity flips or escaping.
 */
export function stripLeanCommentsAndStrings(rawCode: string): string {
  const code = normalizeLeanSource(rawCode);
  const out: string[] = [];
  let i = 0;
  const len = code.length;
  let blockDepth = 0;
  let inLineComment = false;
  let inString = false;
  let inInterpString = false;
  const interpBraceDepths: number[] = [];
  let rawStringHashes = -1;

  while (i < len) {
    const ch = code[i];
    const next = i + 1 < len ? code[i + 1] : '';

    if (inLineComment) {
      if (ch === '\n') {
        inLineComment = false;
        out.push('\n');
      } else {
        out.push(' ');
      }
      i++;
      continue;
    }

    if (blockDepth > 0) {
      if (ch === '/' && next === '-') {
        blockDepth++;
        out.push(' ', ' ');
        i += 2;
      } else if (ch === '-' && next === '/') {
        blockDepth--;
        out.push(' ', ' ');
        i += 2;
      } else {
        out.push(ch === '\n' ? '\n' : ' ');
        i++;
      }
      continue;
    }

    if (rawStringHashes >= 0) {
      if (matchRawStringCloseAt(code, i, rawStringHashes)) {
        const closeLen = 1 + rawStringHashes;
        for (let k = 0; k < closeLen; k++) out.push(' ');
        i += closeLen;
        rawStringHashes = -1;
      } else {
        out.push(ch === '\n' ? '\n' : ' ');
        i++;
      }
      continue;
    }

    if (inInterpString) {
      if (ch === '\\' && i + 1 < len) {
        out.push(' ', next === '\n' ? '\n' : ' ');
        i += 2;
      } else if (ch === '"') {
        inInterpString = false;
        out.push(' ');
        i++;
      } else if (ch === '{') {
        inInterpString = false;
        interpBraceDepths.push(1);
        out.push('{');
        i++;
      } else {
        out.push(ch === '\n' ? '\n' : ' ');
        i++;
      }
      continue;
    }

    if (inString) {
      if (ch === '\\' && i + 1 < len) {
        out.push(' ', next === '\n' ? '\n' : ' ');
        i += 2;
      } else if (ch === '"') {
        inString = false;
        out.push(' ');
        i++;
      } else {
        out.push(ch === '\n' ? '\n' : ' ');
        i++;
      }
      continue;
    }

    if (ch === '/' && next === '-') {
      blockDepth = 1;
      out.push(' ', ' ');
      i += 2;
      continue;
    }

    if (ch === '-' && next === '-') {
      inLineComment = true;
      out.push(' ', ' ');
      i += 2;
      continue;
    }

    if (interpBraceDepths.length > 0) {
      if (ch === '{') {
        interpBraceDepths[interpBraceDepths.length - 1]++;
        out.push('{');
        i++;
        continue;
      }
      if (ch === '}') {
        const nextDepth = interpBraceDepths[interpBraceDepths.length - 1] - 1;
        if (nextDepth <= 0) {
          interpBraceDepths.pop();
          inInterpString = true;
        } else {
          interpBraceDepths[interpBraceDepths.length - 1] = nextDepth;
        }
        out.push('}');
        i++;
        continue;
      }
    }

    const rawOpen = matchRawStringOpen(code, i);
    if (rawOpen) {
      const openLen = rawOpen.contentStart - i;
      for (let k = 0; k < openLen; k++) out.push(' ');
      rawStringHashes = rawOpen.hashes;
      i = rawOpen.contentStart;
      continue;
    }

    const charLen = matchCharLiteral(code, i);
    if (charLen > 0) {
      for (let k = 0; k < charLen; k++) out.push(' ');
      i += charLen;
      continue;
    }

    // Multi-line guillemet identifier `«...»`.
    // Unwrap kernel soundness names (`«sorryAx»`, `«ofReduceBool»`, etc.) so
    // guillemet escaping cannot bypass soundness detection; blank all others.
    if (ch === '«') {
      let j = i + 1;
      while (j < len && code[j] !== '»') j++;
      const hasClose = j < len && code[j] === '»';
      const inner = code.slice(i + 1, j);
      const end = hasClose ? j + 1 : j;
      if (hasClose && SOUNDNESS_GUILLEMET_NAMES.has(inner)) {
        out.push(' ');
        for (let k = 0; k < inner.length; k++) out.push(inner[k]);
        out.push(' ');
      } else {
        for (let k = i; k < end; k++) {
          out.push(code[k] === '\n' ? '\n' : ' ');
        }
      }
      i = end;
      continue;
    }

    // Greedy Unicode identifier scan (prevents `f!'"'"`, `f!r"\""`, `fé'"'"`
    // from mis-triggering char/raw-string lexers and handles `s!"..."`, `m!"..."`).
    const {ch: startChar, len: startLen} = getCharAt(code, i);
    if (startChar && isIdentStart(startChar)) {
      let j = i + startLen;
      while (j < len) {
        const {ch: partChar, len: partLen} = getCharAt(code, j);
        if (!partChar || !isIdentPart(partChar)) break;
        j += partLen;
      }
      // Interpolated string opener (`s!"..."`, `m!"..."`, `f!"..."`).
      // Only Lean's string interpolators enter interpolated mode so ordinary
      // `!`-suffixed macros/functions like `panic!"{"` cannot flip quote parity.
      const ident = code.slice(i, j);
      if (
        LEAN_INTERP_STRING_PREFIXES.has(ident) &&
        j < len &&
        code[j] === '"'
      ) {
        for (let k = i; k <= j; k++) out.push(' ');
        inInterpString = true;
        i = j + 1;
        continue;
      }
      for (let k = i; k < j; k++) out.push(code[k]);
      i = j;
      continue;
    }

    if (ch === '"') {
      inString = true;
      out.push(' ');
      i++;
      continue;
    }

    out.push(ch);
    i++;
  }

  return out.join('');
}

/**
 * Scan the end position (0-based `endRow`, exclusive `endCol` after `-/`).
 * Handles nested Lean 4 block comments (`/-`, `/--`, `/-!`) starting at
 * `(startIdx, startCol)`.
 */
function findBlockCommentEnd(
  lines: string[],
  startIdx: number,
  startCol = 0
): {endRow: number; endCol: number; closed: boolean} {
  let depth = 0;
  for (let row = startIdx; row < lines.length; row++) {
    const line = lines[row];
    let i = row === startIdx ? startCol : 0;
    while (i < line.length) {
      if (line[i] === '/' && line[i + 1] === '-') {
        depth++;
        i += 2;
      } else if (line[i] === '-' && line[i + 1] === '/') {
        depth--;
        i += 2;
        if (depth <= 0) {
          return {endRow: row, endCol: i, closed: true};
        }
      } else {
        i++;
      }
    }
  }
  let endRow = lines.length - 1;
  // Avoid attributing a phantom trailing empty line from a final newline to an
  // unclosed EOF block comment.
  if (endRow > startIdx && lines[endRow] === '') {
    endRow--;
  }
  return {endRow, endCol: lines[endRow].length, closed: false};
}

/** Clean the outer delimiters (`/-! ... -/`, `/-- ... -/`, `/- ... -/`) of a block comment. */
function unwrapBlockComment(raw: string, openerLen: number): string {
  let inner = raw.trim();
  if (inner.length >= openerLen) {
    inner = inner.slice(openerLen);
  }
  if (inner.endsWith('-/')) {
    inner = inner.slice(0, -2);
  }
  // Remove leading/trailing blank lines while preserving internal markdown indentation.
  const innerLines = inner.split('\n');
  let start = 0;
  let end = innerLines.length;
  while (start < end && innerLines[start].trim() === '') {
    start++;
  }
  while (end > start && innerLines[end - 1].trim() === '') {
    end--;
  }
  if (end - start === 1) {
    return innerLines[start].trim();
  }
  return innerLines.slice(start, end).join('\n');
}

function isDividerCommentLine(line: string): boolean {
  return /^--\s*[=\-~*─━]{4,}\s*$/.test(line.trim());
}

/**
 * Strip leading whitespace and any leading closed Lean block comments (`/- ... -/`).
 */
function skipLeadingWsAndBlockComments(text: string): string {
  let s = text.trimStart();
  while (s.startsWith('/-')) {
    let depth = 1;
    let i = 2;
    while (i < s.length && depth > 0) {
      if (s[i] === '/' && s[i + 1] === '-') {
        depth++;
        i += 2;
      } else if (s[i] === '-' && s[i + 1] === '/') {
        depth--;
        i += 2;
      } else {
        i++;
      }
    }
    if (depth !== 0) break;
    s = s.slice(i).trimStart();
  }
  return s;
}

/** Check if `trimmed` is a standalone attribute line (`@[simp]`, `@[simp] -- comment`, etc.) with no trailing code. */
function isStandaloneAttributeLine(trimmed: string): boolean {
  if (!trimmed.startsWith('@[')) return false;
  let rest = trimmed;
  let count = 0;
  while (rest.startsWith('@[')) {
    const closeIdx = findMatchingBracket(rest, 1);
    if (closeIdx < 0) return false;
    count++;
    rest = skipLeadingWsAndBlockComments(rest.slice(closeIdx + 1));
  }
  return count > 0 && (rest === '' || rest.startsWith('--'));
}

/** Check if a line consists solely of Lean declaration modifiers (`unsafe`, `partial`, `private`, ...) + optional comments. */
function parseStandaloneModifiersLine(line: string): string[] | null {
  let rest = skipLeadingWsAndBlockComments(line);
  if (!rest || rest.startsWith('--')) return null;
  const mods: string[] = [];
  while (rest && !rest.startsWith('--')) {
    const match = rest.match(/^([A-Za-z_]+)\b/);
    if (!match || !LEAN_MODIFIER_KEYWORDS.has(match[1])) {
      return null;
    }
    mods.push(match[1]);
    rest = skipLeadingWsAndBlockComments(rest.slice(match[0].length));
  }
  return mods.length > 0 ? mods : null;
}

const LEAN_QUALIFIED_NAME_RE =
  /^(?:«[^»]+»|[\p{L}_][\p{L}\p{N}_'?!₀-₉⁰-⁹]*)(?:\.(?:«[^»]+»|[\p{L}_][\p{L}\p{N}_'?!₀-₉⁰-⁹]*))*/u;

/** Check if a line starts a Lean declaration (with optional attributes, modifiers, and inline comments). */
function parseDeclarationHeaderStart(
  line: string,
  allowIndent = false
): {
  attributes: string[];
  modifiers: string[];
  kind: LeanDeclKind;
  name: string;
} | null {
  if (!line || (!allowIndent && /^\s/.test(line))) return null;
  let rest = skipLeadingWsAndBlockComments(line);
  const attributes: string[] = [];
  const modifiers: string[] = [];

  // Consume leading @[...] attributes on the same line (including nested brackets and inline comments).
  while (rest.startsWith('@[')) {
    const closeIdx = findMatchingBracket(rest, 1);
    if (closeIdx < 0) break;
    attributes.push(rest.slice(0, closeIdx + 1));
    rest = skipLeadingWsAndBlockComments(rest.slice(closeIdx + 1));
  }

  // Consume leading modifiers (`public`, `private`, `protected`, `noncomputable`, `partial`, `unsafe`, `meta`, ...).
  while (true) {
    const match = rest.match(/^([A-Za-z_]+)\b/);
    if (match && LEAN_MODIFIER_KEYWORDS.has(match[1])) {
      modifiers.push(match[1]);
      rest = skipLeadingWsAndBlockComments(rest.slice(match[0].length));
    } else {
      break;
    }
  }

  const kindMatch = rest.match(/^([A-Za-z_]+)\b/);
  if (!kindMatch) return null;
  let candidate = kindMatch[1] as LeanDeclKind;
  if (!LEAN_DECL_KEYWORDS.has(candidate)) return null;

  let afterKind = skipLeadingWsAndBlockComments(
    rest.slice(kindMatch[0].length)
  );
  // Support `class inductive Foo` and `class abbrev Bar`.
  if (candidate === 'class') {
    const subKind = afterKind.match(/^(inductive|abbrev)\b/);
    if (subKind) {
      candidate = subKind[1] as LeanDeclKind;
      afterKind = skipLeadingWsAndBlockComments(
        afterKind.slice(subKind[0].length)
      );
    }
  }

  let name = '';
  if (candidate !== 'example') {
    // Match qualified identifiers segment-by-segment so universe params like `id.{u}` yield `id`.
    const nameMatch = afterKind.match(LEAN_QUALIFIED_NAME_RE);
    if (
      nameMatch &&
      nameMatch[0] !== 'where' &&
      nameMatch[0] !== 'extends' &&
      !nameMatch[0].startsWith(':')
    ) {
      name = nameMatch[0];
    }
  }

  return {attributes, modifiers, kind: candidate, name};
}

function getDirectiveKeyword(line: string, allowIndent = false): string | null {
  if (!line || (!allowIndent && /^\s/.test(line))) return null;
  let rest = skipLeadingWsAndBlockComments(line);
  // Strip optional leading modifiers such as `noncomputable section`, `public section`, `scoped notation`, `local macro`.
  while (true) {
    const modMatch = rest.match(/^([A-Za-z_]+)\b/);
    if (modMatch && LEAN_MODIFIER_KEYWORDS.has(modMatch[1])) {
      const nextRest = skipLeadingWsAndBlockComments(
        rest.slice(modMatch[0].length)
      );
      if (!nextRest) break;
      rest = nextRest;
    } else {
      break;
    }
  }
  const match = rest.match(/^(#[A-Za-z_]+|[A-Za-z_]+)\b/);
  if (!match) return null;
  const word = match[1];
  if (LEAN_DIRECTIVE_KEYWORDS.has(word)) {
    return word;
  }
  return null;
}

/**
 * Find the character index of a top-level `:=` (outside parens/brackets/braces,
 * strings, raw strings, char literals, guillemets, and comments) in a single line.
 */
function findTopLevelAssign(
  line: string,
  initialDepth: {paren: number; bracket: number; brace: number}
): {
  index: number;
  depth: {paren: number; bracket: number; brace: number};
} {
  let {paren, bracket, brace} = initialDepth;
  let inString = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    const next = line[i + 1];
    if (inString) {
      if (ch === '\\') {
        i++;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }
    if (ch === '-' && next === '-') {
      break;
    }
    if (ch === '/' && next === '-') {
      let depth = 1;
      let j = i + 2;
      while (j < line.length && depth > 0) {
        if (line[j] === '/' && line[j + 1] === '-') {
          depth++;
          j += 2;
        } else if (line[j] === '-' && line[j + 1] === '/') {
          depth--;
          j += 2;
        } else {
          j++;
        }
      }
      i = j - 1;
      continue;
    }
    const rawOpen = matchRawStringOpen(line, i);
    if (rawOpen) {
      let j = rawOpen.contentStart;
      while (j < line.length) {
        if (matchRawStringCloseAt(line, j, rawOpen.hashes)) {
          j += rawOpen.hashes;
          break;
        }
        j++;
      }
      i = j;
      continue;
    }
    const charLen = matchCharLiteral(line, i);
    if (charLen > 0) {
      i += charLen - 1;
      continue;
    }
    if (ch === '«') {
      const closeGuillemet = line.indexOf('»', i + 1);
      i = closeGuillemet >= 0 ? closeGuillemet : line.length - 1;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === '(') paren++;
    else if (ch === ')' && paren > 0) paren--;
    else if (ch === '[') bracket++;
    else if (ch === ']' && bracket > 0) bracket--;
    else if (ch === '{') brace++;
    else if (ch === '}' && brace > 0) brace--;
    else if (
      ch === ':' &&
      next === '=' &&
      paren === 0 &&
      bracket === 0 &&
      brace === 0
    ) {
      return {index: i, depth: {paren, bracket, brace}};
    }
  }
  return {index: -1, depth: {paren, bracket, brace}};
}

/** Split trailing `-- ...` comment outside strings, raw strings, char literals, and block comments. */
function splitLineComment(line: string): {code: string; comment?: string} {
  let inString = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inString) {
      if (ch === '\\') i++;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '/' && line[i + 1] === '-') {
      let depth = 1;
      let j = i + 2;
      while (j < line.length && depth > 0) {
        if (line[j] === '/' && line[j + 1] === '-') {
          depth++;
          j += 2;
        } else if (line[j] === '-' && line[j + 1] === '/') {
          depth--;
          j += 2;
        } else {
          j++;
        }
      }
      i = j - 1;
      continue;
    }
    const rawOpen = matchRawStringOpen(line, i);
    if (rawOpen) {
      let j = rawOpen.contentStart;
      while (j < line.length) {
        if (matchRawStringCloseAt(line, j, rawOpen.hashes)) {
          j += rawOpen.hashes;
          break;
        }
        j++;
      }
      i = j;
      continue;
    }
    const charLen = matchCharLiteral(line, i);
    if (charLen > 0) {
      i += charLen - 1;
      continue;
    }
    if (ch === '«') {
      const closeGuillemet = line.indexOf('»', i + 1);
      i = closeGuillemet >= 0 ? closeGuillemet : line.length - 1;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === '-' && line[i + 1] === '-') {
      return {
        code: line.slice(0, i).trimEnd(),
        comment: line.slice(i + 2).trim(),
      };
    }
  }
  return {code: line.trimEnd()};
}

function parseDerivingClause(lines: LeanCodeLine[]): {
  remaining: LeanCodeLine[];
  derivingClause?: string[];
} {
  if (lines.length === 0) return {remaining: lines};
  const last = lines[lines.length - 1];
  const {code} = splitLineComment(last.text);
  const match = code.trim().match(/^deriving\s+(.+)$/);
  if (!match) return {remaining: lines};
  const clauses = match[1]
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);
  return {
    remaining: lines.slice(0, -1),
    derivingClause: clauses.length > 0 ? clauses : undefined,
  };
}

function parseStructureFields(
  bodyLines: LeanCodeLine[]
): LeanStructureField[] | undefined {
  if (bodyLines.length === 0) return undefined;
  const fields: LeanStructureField[] = [];
  for (const line of bodyLines) {
    const trimmed = line.text.trim();
    if (!trimmed) continue;
    // If there are standalone doc/comment lines or multi-line types, fall back to code view.
    if (trimmed.startsWith('--') || trimmed.startsWith('/-')) {
      return undefined;
    }
    const {code, comment} = splitLineComment(line.text);
    const fieldMatch = code
      .trim()
      .match(
        /^(«[^»]+»|[A-Za-z_α-ωΑ-Ω][A-Za-z0-9_'α-ωΑ-Ω₀-₉]*)\s*:(?!:)\s*(.+)$/
      );
    if (!fieldMatch) {
      return undefined;
    }
    const fieldName = fieldMatch[1];
    const rhs = fieldMatch[2].trim();
    const assign = findTopLevelAssign(rhs, {paren: 0, bracket: 0, brace: 0});
    let typeText = rhs;
    let defaultValue: string | undefined;
    if (assign.index >= 0) {
      typeText = rhs.slice(0, assign.index).trim();
      defaultValue = rhs.slice(assign.index + 2).trim();
    }
    fields.push({
      name: fieldName,
      typeText,
      defaultValue,
      comment,
      lineNum: line.lineNum,
      rawLine: line.text,
    });
  }
  return fields.length > 0 ? fields : undefined;
}

/**
 * Split a Lean inductive constructor line (starting with `|`) into one or more
 * constructor segments, supporting inline enum syntax like `| north | south | east | west`
 * while never splitting on `||`, `|||`, `|>`, `<|`, or pipes inside delimiters/strings.
 */
function splitTopLevelPipes(trimmedCode: string): string[] | undefined {
  if (!trimmedCode.startsWith('|')) return undefined;
  const segments: string[] = [];
  let segStart = 1;
  let paren = 0;
  let bracket = 0;
  let brace = 0;
  let inString = false;

  for (let i = 1; i < trimmedCode.length; i++) {
    const ch = trimmedCode[i];
    if (inString) {
      if (ch === '\\') i++;
      else if (ch === '"') inString = false;
      continue;
    }
    const rawOpen = matchRawStringOpen(trimmedCode, i);
    if (rawOpen) {
      let j = rawOpen.contentStart;
      while (j < trimmedCode.length) {
        if (matchRawStringCloseAt(trimmedCode, j, rawOpen.hashes)) {
          j += rawOpen.hashes;
          break;
        }
        j++;
      }
      i = j;
      continue;
    }
    const charLen = matchCharLiteral(trimmedCode, i);
    if (charLen > 0) {
      i += charLen - 1;
      continue;
    }
    if (ch === '«') {
      const closeGuillemet = trimmedCode.indexOf('»', i + 1);
      i = closeGuillemet >= 0 ? closeGuillemet : trimmedCode.length - 1;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === '(') paren++;
    else if (ch === ')' && paren > 0) paren--;
    else if (ch === '[') bracket++;
    else if (ch === ']' && bracket > 0) bracket--;
    else if (ch === '{') brace++;
    else if (ch === '}' && brace > 0) brace--;
    else if (
      ch === '|' &&
      paren === 0 &&
      bracket === 0 &&
      brace === 0 &&
      trimmedCode[i - 1] !== '|' &&
      trimmedCode[i - 1] !== '<' &&
      trimmedCode[i + 1] !== '|' &&
      trimmedCode[i + 1] !== '>'
    ) {
      const seg = trimmedCode.slice(segStart, i).trim();
      if (!seg) return undefined;
      segments.push(seg);
      segStart = i + 1;
    }
  }
  const lastSeg = trimmedCode.slice(segStart).trim();
  if (!lastSeg) return undefined;
  segments.push(lastSeg);
  return segments;
}

function parseInductiveConstructors(
  bodyLines: LeanCodeLine[]
): LeanInductiveCtor[] | undefined {
  if (bodyLines.length === 0) return undefined;
  const ctors: LeanInductiveCtor[] = [];
  for (const line of bodyLines) {
    const trimmed = line.text.trim();
    if (!trimmed) continue;
    if (!trimmed.startsWith('|')) {
      return undefined;
    }
    const {code, comment} = splitLineComment(line.text);
    const segments = splitTopLevelPipes(code.trim());
    if (!segments) return undefined;
    for (let s = 0; s < segments.length; s++) {
      const nameMatch = segments[s].match(
        /^(«[^»]+»|[A-Za-z_α-ωΑ-Ω][A-Za-z0-9_'α-ωΑ-Ω₀-₉]*)(.*)$/
      );
      if (!nameMatch) return undefined;
      ctors.push({
        name: nameMatch[1],
        signatureText: nameMatch[2].trim(),
        comment: s === segments.length - 1 ? comment : undefined,
        lineNum: line.lineNum,
        rawLine: line.text,
      });
    }
  }
  return ctors.length > 0 ? ctors : undefined;
}

/**
 * Extract all Lean identifier tokens from comment/string-stripped code using
 * exact Lean identifier boundaries (so `sorry'`, `sorry?`, `sorry₀`, and
 * `αsorry` do not match `sorry`).
 */
function extractLeanIdentifiers(cleanCode: string): Set<string> {
  const ids = new Set<string>();
  const len = cleanCode.length;
  let i = 0;
  while (i < len) {
    const {ch, len: chLen} = getCharAt(cleanCode, i);
    if (ch && ch !== '#' && isIdentStart(ch)) {
      let j = i + chLen;
      while (j < len) {
        const {ch: partCh, len: partLen} = getCharAt(cleanCode, j);
        if (!partCh || !isIdentPart(partCh)) break;
        j += partLen;
      }
      ids.add(cleanCode.slice(i, j));
      i = j;
    } else {
      i += Math.max(1, chLen);
    }
  }
  return ids;
}

function analyzeDeclarationHygiene(
  kind: LeanDeclKind,
  modifiers: string[],
  pendingAttrs: string[],
  codeLines: LeanCodeLine[]
): LeanDeclHygiene {
  const fullText = [...pendingAttrs, ...codeLines.map(l => l.text)].join('\n');
  const cleanCode = stripLeanCommentsAndStrings(fullText);
  const ids = extractLeanIdentifiers(cleanCode);
  return {
    hasSorry: ids.has('sorry') || ids.has('sorryAx'),
    hasAdmit: ids.has('admit'),
    isAxiom: kind === 'axiom' || ids.has('axiom'),
    isPartial: modifiers.includes('partial') || ids.has('partial'),
    isUnsafe: modifiers.includes('unsafe') || ids.has('unsafe'),
    hasNativeDecide:
      ids.has('native_decide') ||
      ids.has('ofReduceBool') ||
      ids.has('ofReduceNat'),
  };
}

function detectProofStrategy(
  kind: LeanDeclKind,
  isTacticProof: boolean,
  bodyLines: LeanCodeLine[],
  hygiene: LeanDeclHygiene
): LeanProofStrategy | undefined {
  if (kind !== 'theorem' && kind !== 'lemma' && kind !== 'example') {
    return undefined;
  }
  const cleanBody = stripLeanCommentsAndStrings(
    bodyLines.map(l => l.text).join('\n')
  ).trim();
  if (!cleanBody) return undefined;
  if (
    !hygiene.hasSorry &&
    !hygiene.hasAdmit &&
    !hygiene.hasNativeDecide &&
    /^(?:by\s+)?decide$/.test(cleanBody)
  ) {
    return 'decide';
  }
  if (isTacticProof) {
    return 'tactic';
  }
  return 'term';
}

function findTopLevelAssignInStrippedLine(
  strippedLine: string,
  initialDepth: {paren: number; bracket: number; brace: number}
): {
  index: number;
  depth: {paren: number; bracket: number; brace: number};
} {
  let {paren, bracket, brace} = initialDepth;
  for (let i = 0; i < strippedLine.length; i++) {
    const ch = strippedLine[i];
    if (ch === '(') paren++;
    else if (ch === ')' && paren > 0) paren--;
    else if (ch === '[') bracket++;
    else if (ch === ']' && bracket > 0) bracket--;
    else if (ch === '{') brace++;
    else if (ch === '}' && brace > 0) brace--;
    else if (
      ch === ':' &&
      strippedLine[i + 1] === '=' &&
      paren === 0 &&
      bracket === 0 &&
      brace === 0
    ) {
      return {index: i, depth: {paren, bracket, brace}};
    }
  }
  return {index: -1, depth: {paren, bracket, brace}};
}

function buildDeclarationDetails(
  header: {
    attributes: string[];
    modifiers: string[];
    kind: LeanDeclKind;
    name: string;
  },
  pendingAttrs: string[],
  docComment: string | undefined,
  docStartLine: number | undefined,
  docEndLine: number | undefined,
  codeLines: LeanCodeLine[]
): LeanDeclarationDetails {
  const attributes = [...pendingAttrs, ...header.attributes];
  const modifiers = [...header.modifiers];
  const hygiene = analyzeDeclarationHygiene(
    header.kind,
    modifiers,
    pendingAttrs,
    codeLines
  );

  // Pre-strip multi-line comments/strings once with exact 1:1 character offsets
  // so signature/body splitting is immune to multi-line comments containing `:=`.
  const strippedLines = stripLeanCommentsAndStrings(
    codeLines.map(l => l.text).join('\n')
  ).split('\n');

  // Split declaration lines into signature vs body.
  let signatureLines: LeanCodeLine[] = [];
  let bodyLines: LeanCodeLine[] = [];
  let isTacticProof = false;
  let fields: LeanStructureField[] | undefined;
  let constructors: LeanInductiveCtor[] | undefined;
  let derivingClause: string[] | undefined;

  if (
    header.kind === 'structure' ||
    header.kind === 'class' ||
    header.kind === 'inductive' ||
    header.kind === 'coinductive'
  ) {
    // Find `where` keyword line (or end of header).
    let whereIdx = -1;
    for (let idx = 0; idx < codeLines.length; idx++) {
      const strippedTrimmed = (strippedLines[idx] ?? '').trim();
      if (/\bwhere\s*$/.test(strippedTrimmed)) {
        whereIdx = idx;
        break;
      }
      if (idx > 0 && strippedTrimmed.startsWith('|')) {
        whereIdx = idx - 1;
        break;
      }
    }
    if (whereIdx >= 0) {
      signatureLines = codeLines.slice(0, whereIdx + 1);
      const rawBody = codeLines.slice(whereIdx + 1);
      const parsedDeriving = parseDerivingClause(rawBody);
      derivingClause = parsedDeriving.derivingClause;
      bodyLines = parsedDeriving.remaining;
      if (header.kind === 'structure' || header.kind === 'class') {
        fields = parseStructureFields(bodyLines);
      } else {
        constructors = parseInductiveConstructors(bodyLines);
      }
    } else {
      signatureLines = codeLines;
    }
  } else {
    // Support `instance ... where` blocks before looking for top-level `:=`.
    let instanceWhereIdx = -1;
    if (header.kind === 'instance') {
      for (let idx = 0; idx < codeLines.length; idx++) {
        const strippedTrimmed = (strippedLines[idx] ?? '').trim();
        if (/\bwhere\s*$/.test(strippedTrimmed)) {
          instanceWhereIdx = idx;
          break;
        }
      }
    }

    if (instanceWhereIdx >= 0) {
      signatureLines = codeLines.slice(0, instanceWhereIdx + 1);
      bodyLines = codeLines.slice(instanceWhereIdx + 1);
    } else {
      // Look for top-level `:=` or equation-compiler `|` arms.
      let depth = {paren: 0, bracket: 0, brace: 0};
      let splitLineIdx = -1;
      let assignCharIdx = -1;
      let isMatchArmSplit = false;

      for (let idx = 0; idx < codeLines.length; idx++) {
        const strippedLine = strippedLines[idx] ?? '';
        if (
          idx > 0 &&
          depth.paren === 0 &&
          depth.bracket === 0 &&
          depth.brace === 0 &&
          /^\s*\|/.test(strippedLine)
        ) {
          splitLineIdx = idx;
          isMatchArmSplit = true;
          break;
        }
        const res = findTopLevelAssignInStrippedLine(strippedLine, depth);
        depth = res.depth;
        if (res.index >= 0) {
          splitLineIdx = idx;
          assignCharIdx = res.index;
          break;
        }
      }

      if (splitLineIdx >= 0 && isMatchArmSplit) {
        signatureLines = codeLines.slice(0, splitLineIdx);
        bodyLines = codeLines.slice(splitLineIdx);
        const cleanBody = strippedLines.slice(splitLineIdx).join('\n');
        isTacticProof = extractLeanIdentifiers(cleanBody).has('by');
      } else if (splitLineIdx >= 0 && assignCharIdx >= 0) {
        const splitLine = codeLines[splitLineIdx];
        const beforeAssign = splitLine.text.slice(0, assignCharIdx).trimEnd();
        const afterAssign = splitLine.text.slice(assignCharIdx + 2).trim();
        const afterAssignStripped = (strippedLines[splitLineIdx] ?? '')
          .slice(assignCharIdx + 2)
          .trim();

        signatureLines = [
          ...codeLines.slice(0, splitLineIdx),
          {lineNum: splitLine.lineNum, text: beforeAssign},
        ];

        const byMatch = afterAssignStripped.match(/^by(?=$|\s)/);
        if (byMatch) {
          isTacticProof = true;
          const leadingStripped = skipLeadingWsAndBlockComments(afterAssign);
          const remainder = leadingStripped.startsWith('by')
            ? leadingStripped.slice(2).trim()
            : afterAssign.trim();
          if (remainder) {
            bodyLines = [
              {lineNum: splitLine.lineNum, text: remainder},
              ...codeLines.slice(splitLineIdx + 1),
            ];
          } else {
            bodyLines = codeLines.slice(splitLineIdx + 1);
          }
        } else if (/^do(?=$|\s)/.test(afterAssignStripped)) {
          bodyLines = [
            {lineNum: splitLine.lineNum, text: afterAssign},
            ...codeLines.slice(splitLineIdx + 1),
          ];
        } else if (afterAssign) {
          bodyLines = [
            {lineNum: splitLine.lineNum, text: afterAssign},
            ...codeLines.slice(splitLineIdx + 1),
          ];
        } else {
          bodyLines = codeLines.slice(splitLineIdx + 1);
          const firstNonBlankIdx = bodyLines.findIndex(
            (_, offset) =>
              (strippedLines[splitLineIdx + 1 + offset] ?? '').trim().length > 0
          );
          if (
            firstNonBlankIdx >= 0 &&
            /^by(?=$|\s)/.test(
              (strippedLines[splitLineIdx + 1 + firstNonBlankIdx] ?? '').trim()
            )
          ) {
            isTacticProof = true;
          }
        }
      } else {
        signatureLines = codeLines;
      }
    }
  }

  const isCompactDef =
    (header.kind === 'def' || header.kind === 'abbrev') &&
    !docComment &&
    attributes.length === 0 &&
    codeLines.length === 1 &&
    !hygiene.hasSorry &&
    !hygiene.hasAdmit &&
    !hygiene.hasNativeDecide &&
    !hygiene.isAxiom &&
    !hygiene.isPartial &&
    !hygiene.isUnsafe;

  const proofStrategy = detectProofStrategy(
    header.kind,
    isTacticProof,
    bodyLines,
    hygiene
  );

  return {
    kind: header.kind,
    name: header.name,
    attributes,
    modifiers,
    docComment,
    docStartLine,
    docEndLine,
    signatureLines,
    bodyLines,
    isTacticProof,
    proofStrategy,
    fields,
    constructors,
    derivingClause,
    isCompactDef,
    hygiene,
  };
}

const IMPORT_LINE_RE = /^(?:(?:public|private|meta)\s+)*import(?:\s+all)?\s+/;

const SINGLE_LINE_DIRECTIVES = new Set([
  'module',
  'namespace',
  'section',
  'end',
  'universe',
  'universes',
  'export',
  '#where',
  '#exit',
]);

const BODY_DIRECTIVES = new Set([
  'macro',
  'macro_rules',
  'syntax',
  'elab',
  'elab_rules',
  'initialize',
  'builtin_initialize',
  'notation',
  'infix',
  'infixl',
  'infixr',
  'prefix',
  'postfix',
]);

const IN_SCOPING_DIRECTIVES = new Set([
  'open',
  'set_option',
  'variable',
  'variables',
  'universe',
  'universes',
  'attribute',
  'local',
  'scoped',
  'export',
]);

interface LeanLexState {
  blockDepth: number;
  inString: boolean;
  inInterpString: boolean;
  interpBraceDepths: number[];
  rawStringHashes: number;
  inGuillemet: boolean;
  paren: number;
  bracket: number;
  brace: number;
}

function createLeanLexState(): LeanLexState {
  return {
    blockDepth: 0,
    inString: false,
    inInterpString: false,
    interpBraceDepths: [],
    rawStringHashes: -1,
    inGuillemet: false,
    paren: 0,
    bracket: 0,
    brace: 0,
  };
}

function hasOpenLiteralOrComment(state: LeanLexState): boolean {
  return (
    state.blockDepth > 0 ||
    state.inString ||
    state.inInterpString ||
    state.rawStringHashes >= 0 ||
    state.inGuillemet
  );
}

function hasOpenDelimiters(state: LeanLexState): boolean {
  return state.paren > 0 || state.bracket > 0 || state.brace > 0;
}

/**
 * Incrementally advance multi-line lexical & delimiter state across a single
 * line and return the comment/string-stripped view of that line.
 */
function advanceLeanLexState(state: LeanLexState, line: string): string {
  const out: string[] = [];
  let i = 0;
  const len = line.length;
  while (i < len) {
    const ch = line[i];
    const next = i + 1 < len ? line[i + 1] : '';

    if (state.blockDepth > 0) {
      if (ch === '/' && next === '-') {
        state.blockDepth++;
        out.push(' ', ' ');
        i += 2;
      } else if (ch === '-' && next === '/') {
        state.blockDepth--;
        out.push(' ', ' ');
        i += 2;
      } else {
        out.push(' ');
        i++;
      }
      continue;
    }

    if (state.inGuillemet) {
      out.push(' ');
      if (ch === '»') {
        state.inGuillemet = false;
      }
      i++;
      continue;
    }

    if (state.rawStringHashes >= 0) {
      if (matchRawStringCloseAt(line, i, state.rawStringHashes)) {
        const closeLen = 1 + state.rawStringHashes;
        for (let k = 0; k < closeLen; k++) out.push(' ');
        i += closeLen;
        state.rawStringHashes = -1;
      } else {
        out.push(' ');
        i++;
      }
      continue;
    }

    if (state.inInterpString) {
      if (ch === '\\' && i + 1 < len) {
        out.push(' ', ' ');
        i += 2;
      } else if (ch === '"') {
        state.inInterpString = false;
        out.push(' ');
        i++;
      } else if (ch === '{') {
        state.inInterpString = false;
        state.interpBraceDepths.push(1);
        out.push('{');
        i++;
      } else {
        out.push(' ');
        i++;
      }
      continue;
    }

    if (state.inString) {
      if (ch === '\\' && i + 1 < len) {
        out.push(' ', ' ');
        i += 2;
      } else if (ch === '"') {
        state.inString = false;
        out.push(' ');
        i++;
      } else {
        out.push(' ');
        i++;
      }
      continue;
    }

    if (ch === '/' && next === '-') {
      state.blockDepth = 1;
      out.push(' ', ' ');
      i += 2;
      continue;
    }

    if (ch === '-' && next === '-') {
      break;
    }

    if (state.interpBraceDepths.length > 0) {
      if (ch === '{') {
        state.interpBraceDepths[state.interpBraceDepths.length - 1]++;
        out.push('{');
        i++;
        continue;
      }
      if (ch === '}') {
        const nextDepth =
          state.interpBraceDepths[state.interpBraceDepths.length - 1] - 1;
        if (nextDepth <= 0) {
          state.interpBraceDepths.pop();
          state.inInterpString = true;
        } else {
          state.interpBraceDepths[state.interpBraceDepths.length - 1] =
            nextDepth;
        }
        out.push('}');
        i++;
        continue;
      }
    }

    const rawOpen = matchRawStringOpen(line, i);
    if (rawOpen) {
      const openLen = rawOpen.contentStart - i;
      for (let k = 0; k < openLen; k++) out.push(' ');
      state.rawStringHashes = rawOpen.hashes;
      i = rawOpen.contentStart;
      continue;
    }

    const charLen = matchCharLiteral(line, i);
    if (charLen > 0) {
      for (let k = 0; k < charLen; k++) out.push(' ');
      i += charLen;
      continue;
    }

    if (ch === '«') {
      state.inGuillemet = true;
      out.push(' ');
      i++;
      continue;
    }

    const {ch: startChar, len: startLen} = getCharAt(line, i);
    if (startChar && isIdentStart(startChar)) {
      let j = i + startLen;
      while (j < len) {
        const {ch: partChar, len: partLen} = getCharAt(line, j);
        if (!partChar || !isIdentPart(partChar)) break;
        j += partLen;
      }
      const ident = line.slice(i, j);
      if (
        LEAN_INTERP_STRING_PREFIXES.has(ident) &&
        j < len &&
        line[j] === '"'
      ) {
        for (let k = i; k <= j; k++) out.push(' ');
        state.inInterpString = true;
        i = j + 1;
        continue;
      }
      for (let k = i; k < j; k++) out.push(line[k]);
      i = j;
      continue;
    }

    if (ch === '"') {
      state.inString = true;
      out.push(' ');
      i++;
      continue;
    }

    if (ch === '(') state.paren++;
    else if (ch === ')' && state.paren > 0) state.paren--;
    else if (ch === '[') state.bracket++;
    else if (ch === ']' && state.bracket > 0) state.bracket--;
    else if (ch === '{') state.brace++;
    else if (ch === '}' && state.brace > 0) state.brace--;

    out.push(ch);
    i++;
  }
  return out.join('');
}

function lineExpectsDeclContinuation(strippedLine: string): boolean {
  const trimmed = strippedLine.trimEnd();
  if (!trimmed) return false;
  return /(?:^|[\s()[\]{}])(?:by|where|extends)$|(?::=|=>)$/.test(trimmed);
}

function extractBlockCommentRaw(
  lines: string[],
  startRow: number,
  startCol: number,
  endRow: number,
  endCol: number
): string {
  if (startRow === endRow) {
    return lines[startRow].slice(startCol, endCol);
  }
  const parts: string[] = [lines[startRow].slice(startCol)];
  for (let r = startRow + 1; r < endRow; r++) {
    parts.push(lines[r]);
  }
  parts.push(lines[endRow].slice(0, endCol));
  return parts.join('\n');
}

function getLineIndent(line: string): number {
  let idx = 0;
  while (idx < line.length && (line[idx] === ' ' || line[idx] === '\t')) {
    idx++;
  }
  return idx;
}

/**
 * Determine whether `rawAfterKw` (following a directive keyword inside a `where`
 * block) matches a parameterized member/field signature such as
 * `syntax (n : Nat) : String` or `prefix x := x + 1` rather than a command.
 */
function hasMemberSignatureAfterKeyword(rawAfterKw: string): boolean {
  let i = 0;
  const len = rawAfterKw.length;
  let sawParam = false;
  while (i < len) {
    while (i < len && /\s/.test(rawAfterKw[i])) i++;
    if (i >= len) return false;
    const ch = rawAfterKw[i];
    const next = i + 1 < len ? rawAfterKw[i + 1] : '';
    if (ch === '-' && next === '-') return false;
    if (ch === '/' && next === '-') {
      let depth = 1;
      let j = i + 2;
      while (j < len && depth > 0) {
        if (rawAfterKw[j] === '/' && rawAfterKw[j + 1] === '-') {
          depth++;
          j += 2;
        } else if (rawAfterKw[j] === '-' && rawAfterKw[j + 1] === '/') {
          depth--;
          j += 2;
        } else {
          j++;
        }
      }
      if (depth > 0) return false;
      i = j;
      continue;
    }
    if (ch === ':' && next === '=') {
      return true;
    }
    if (ch === ':' && !/^:\d/.test(rawAfterKw.slice(i))) {
      return true;
    }
    if (ch === '|' && sawParam) {
      return true;
    }
    if (ch === '(' || ch === '{' || ch === '[') {
      const closeCh = ch === '(' ? ')' : ch === '{' ? '}' : ']';
      let depth = 1;
      let j = i + 1;
      let inStr = false;
      while (j < len && depth > 0) {
        const c = rawAfterKw[j];
        if (inStr) {
          if (c === '\\') j++;
          else if (c === '"') inStr = false;
        } else if (c === '"') {
          inStr = true;
        } else if (c === ch) {
          depth++;
        } else if (c === closeCh) {
          depth--;
        }
        j++;
      }
      if (depth > 0) return false;
      sawParam = true;
      i = j;
      continue;
    }
    const {ch: startChar, len: startLen} = getCharAt(rawAfterKw, i);
    if (startChar && (isIdentStart(startChar) || ch === '«')) {
      if (ch === '«') {
        const closeG = rawAfterKw.indexOf('»', i + 1);
        if (closeG < 0) return false;
        i = closeG + 1;
      } else {
        let j = i + startLen;
        while (j < len) {
          const {ch: partCh, len: partLen} = getCharAt(rawAfterKw, j);
          if (!partCh || !isIdentPart(partCh)) break;
          j += partLen;
        }
        i = j;
      }
      sawParam = true;
      continue;
    }
    return false;
  }
  return false;
}

/**
 * Determine whether a line starts a brand-new top-level Lean command (declaration,
 * declaration prefix such as attributes/standalone modifiers/module-or-decl docs,
 * or non-scoping directive) that should terminate an active declaration's body
 * when delimiters are balanced.
 */
function startsNewTopLevelCommandInDeclBody(
  line: string,
  isTypeDecl: boolean,
  inWhereBlock = false
): boolean {
  const trimmed = line.trim();
  if (trimmed.startsWith('/-!')) return true;
  if (!isTypeDecl && trimmed.startsWith('/--') && !trimmed.startsWith('/---')) {
    return true;
  }
  const restAfterLeading = skipLeadingWsAndBlockComments(line);
  if (restAfterLeading.startsWith('@[')) return true;
  if (parseDeclarationHeaderStart(line, true)) return true;
  if (parseStandaloneModifiersLine(line)) return true;
  const dirKw = getDirectiveKeyword(line, true);
  if (!dirKw) return false;
  const kwIdx = restAfterLeading.indexOf(dirKw);
  const rawAfterKw =
    kwIdx >= 0 ? restAfterLeading.slice(kwIdx + dirKw.length) : '';
  const afterKw = skipLeadingWsAndBlockComments(rawAfterKw);
  // `deriving` clauses belong to the preceding declaration unless starting a
  // top-level `deriving instance ... for ...` command.
  if (dirKw === 'deriving') {
    return /^instance(?=$|\s|--)/.test(afterKw);
  }
  // Avoid breaking on structure fields or local bindings whose name collides
  // with a context-sensitive directive keyword (e.g. `module : String`,
  // `variables := ...`, `prefix x := ...`, `syntax (n : Nat) : String`).
  if (/^:(?!\d)/.test(afterKw) || afterKw.startsWith(':=')) return false;
  if (inWhereBlock && hasMemberSignatureAfterKeyword(rawAfterKw)) {
    return false;
  }
  // `open ... in`, `set_option ... in`, `attribute ... in`, `variable ... in`
  // can legitimately scope expressions/tactics inside declaration bodies.
  if (IN_SCOPING_DIRECTIVES.has(dirKw)) {
    const stripped = stripLeanCommentsAndStrings(line);
    if (/(?:^|\s)in(?=$|\s)/.test(stripped)) return false;
  }
  return true;
}

/**
 * Parse a Lean 4 source file into semantic top-level blocks with exact 1-based
 * `startLine`..`endLine` ranges.
 */
export function parseLeanBlocks(source: string): LeanBlock[] {
  if (!source) return [];
  const lines = normalizeLeanSource(source).split('\n');
  const blocks: LeanBlock[] = [];

  let i = 0;
  let colOffset = 0;
  let blockCounter = 0;
  let inMutual = false;
  let mutualIndent = -1;

  // Lazy watermark and linear scan budget for multi-line `@[...]` attributes so
  // files with many unclosed `@[` lines stay strictly O(N).
  let lastClosingBracketRow: number | undefined;
  let remainingUnclosedAttrScanBudget = Math.max(256, lines.length * 2);
  const getLastClosingBracketRow = (): number => {
    if (lastClosingBracketRow !== undefined) return lastClosingBracketRow;
    for (let r = lines.length - 1; r >= 0; r--) {
      if (lines[r].includes(']')) {
        lastClosingBracketRow = r;
        return r;
      }
    }
    lastClosingBracketRow = -1;
    return -1;
  };

  let pendingDoc: {text: string; startLine: number; endLine: number} | null =
    null;
  let pendingAttrs: Array<{text: string; startLine: number; endLine: number}> =
    [];
  let pendingModifiers: Array<{text: string; mods: string[]; lineNum: number}> =
    [];
  let pendingComments: Array<{
    startLine: number;
    endLine: number;
    lines: LeanCodeLine[];
    raw: string;
  }> = [];

  const hasPendingDeclPrefix = () =>
    pendingDoc !== null ||
    pendingAttrs.length > 0 ||
    pendingModifiers.length > 0;

  const flushPendingAsStandalone = () => {
    const standalone: LeanBlock[] = [];
    if (pendingDoc) {
      standalone.push({
        id: `doc-${blockCounter++}`,
        type: 'module_doc',
        raw: lines
          .slice(pendingDoc.startLine - 1, pendingDoc.endLine)
          .join('\n'),
        startLine: pendingDoc.startLine,
        endLine: pendingDoc.endLine,
        markdownContent: pendingDoc.text,
      });
      pendingDoc = null;
    }
    if (pendingAttrs.length > 0) {
      const startLine = pendingAttrs[0].startLine;
      const endLine = pendingAttrs[pendingAttrs.length - 1].endLine;
      standalone.push({
        id: `dir-${blockCounter++}`,
        type: 'directive',
        raw: pendingAttrs.map(a => a.text).join('\n'),
        startLine,
        endLine,
        directiveKind: 'attribute',
        directiveLines: pendingAttrs.map(a => {
          return {
            lineNum: a.startLine,
            text: a.text,
          };
        }),
      });
      pendingAttrs = [];
    }
    if (pendingModifiers.length > 0) {
      const startLine = pendingModifiers[0].lineNum;
      const endLine = pendingModifiers[pendingModifiers.length - 1].lineNum;
      standalone.push({
        id: `dir-mod-${blockCounter++}`,
        type: 'directive',
        raw: pendingModifiers.map(m => m.text).join('\n'),
        startLine,
        endLine,
        directiveKind: 'modifier',
        directiveLines: pendingModifiers.map(m => {
          return {lineNum: m.lineNum, text: m.text};
        }),
      });
      pendingModifiers = [];
    }
    for (const c of pendingComments) {
      standalone.push({
        id: `comment-${blockCounter++}`,
        type: 'comment',
        raw: c.raw,
        startLine: c.startLine,
        endLine: c.endLine,
        commentLines: c.lines,
      });
    }
    pendingComments = [];
    standalone.sort((a, b) => a.startLine - b.startLine);
    blocks.push(...standalone);
  };

  while (i < lines.length) {
    const loopStartI = i;
    const loopStartCol = colOffset;
    const fullLine = lines[i];
    const line = colOffset > 0 ? fullLine.slice(colOffset) : fullLine;
    const trimmed = line.trim();

    if (trimmed === '') {
      i++;
      colOffset = 0;
      continue;
    }

    const leadingWs = getLineIndent(line);
    const startCol = colOffset + leadingWs;
    const lineIndent = getLineIndent(fullLine);

    // Exit `mutual ... end` scope on column-0 `end`.
    if (
      inMutual &&
      colOffset === 0 &&
      lineIndent === 0 &&
      /^end(?:\s|--|$)/.test(trimmed)
    ) {
      inMutual = false;
      mutualIndent = -1;
    }

    // 1. Module doc comment `/-! ... -/`
    if (trimmed.startsWith('/-!')) {
      flushPendingAsStandalone();
      const {endRow, endCol, closed} = findBlockCommentEnd(lines, i, startCol);
      const raw = extractBlockCommentRaw(lines, i, startCol, endRow, endCol);
      blocks.push({
        id: `moddoc-${blockCounter++}`,
        type: 'module_doc',
        raw,
        startLine: i + 1,
        endLine: endRow + 1,
        markdownContent: unwrapBlockComment(raw, 3),
      });
      if (closed && lines[endRow].slice(endCol).trim() !== '') {
        i = endRow;
        colOffset = endCol;
      } else {
        i = endRow + 1;
        colOffset = 0;
      }
      continue;
    }

    // 2. Declaration doc comment `/-- ... -/` (not `/---`)
    if (trimmed.startsWith('/--') && !trimmed.startsWith('/---')) {
      flushPendingAsStandalone();
      if (inMutual && mutualIndent < 0) {
        mutualIndent = lineIndent;
      }
      const {endRow, endCol, closed} = findBlockCommentEnd(lines, i, startCol);
      const raw = extractBlockCommentRaw(lines, i, startCol, endRow, endCol);
      pendingDoc = {
        text: unwrapBlockComment(raw, 3),
        startLine: i + 1,
        endLine: endRow + 1,
      };
      if (closed && lines[endRow].slice(endCol).trim() !== '') {
        i = endRow;
        colOffset = endCol;
      } else {
        i = endRow + 1;
        colOffset = 0;
      }
      continue;
    }

    // 3. Regular block comment `/- ... -/`
    if (trimmed.startsWith('/-')) {
      flushPendingAsStandalone();
      const {endRow, endCol, closed} = findBlockCommentEnd(lines, i, startCol);
      const raw = extractBlockCommentRaw(lines, i, startCol, endRow, endCol);
      const inner = unwrapBlockComment(raw, 2);
      const commentLines = raw.split('\n').map((t, idx) => {
        return {lineNum: i + 1 + idx, text: t};
      });
      const isLicenseHeader =
        /copyright|apache license|spdx-license-identifier|licensed under/i.test(
          inner
        );
      blocks.push({
        id: `comment-${blockCounter++}`,
        type: 'comment',
        raw,
        startLine: i + 1,
        endLine: endRow + 1,
        commentLines,
        markdownContent: inner,
        isLicenseHeader,
      });
      if (closed && lines[endRow].slice(endCol).trim() !== '') {
        i = endRow;
        colOffset = endCol;
      } else {
        i = endRow + 1;
        colOffset = 0;
      }
      continue;
    }

    // 4. Section divider banners (`-- ===... / -- Title / -- ===...`)
    if (colOffset === 0 && isDividerCommentLine(line)) {
      flushPendingAsStandalone();
      const startIdx = i;
      let j = i + 1;
      const titleLines: string[] = [];
      while (j < lines.length && lines[j].trim().startsWith('--')) {
        if (isDividerCommentLine(lines[j])) {
          j++;
          break;
        }
        titleLines.push(lines[j].trim().replace(/^--\s?/, ''));
        j++;
      }
      if (titleLines.length > 0) {
        const endIdx = j - 1;
        blocks.push({
          id: `section-${blockCounter++}`,
          type: 'section_banner',
          raw: lines.slice(startIdx, endIdx + 1).join('\n'),
          startLine: startIdx + 1,
          endLine: endIdx + 1,
          markdownContent: titleLines.join(' ').trim(),
        });
        i = endIdx + 1;
        colOffset = 0;
        continue;
      }
      // Standalone divider comment(s) with no title lines: emit as regular comment
      // and strictly advance `i` to prevent infinite loops.
      const dividerEnd = j;
      blocks.push({
        id: `comment-${blockCounter++}`,
        type: 'comment',
        raw: lines.slice(startIdx, dividerEnd).join('\n'),
        startLine: startIdx + 1,
        endLine: dividerEnd,
        commentLines: lines.slice(startIdx, dividerEnd).map((t, idx) => {
          return {lineNum: startIdx + 1 + idx, text: t};
        }),
      });
      i = dividerEnd;
      colOffset = 0;
      continue;
    }

    // 5. Standalone line comments `-- ...`
    // Preserve pending docs/attributes/modifiers across intervening `--` comments
    // so they remain attached to the following declaration (and flush in source
    // order if no declaration follows).
    if (trimmed.startsWith('--')) {
      const startIdx = i;
      let commentLines: LeanCodeLine[];
      let raw: string;
      let endLine: number;
      if (colOffset > 0) {
        raw = trimmed;
        endLine = startIdx + 1;
        commentLines = [{lineNum: startIdx + 1, text: trimmed}];
        i = startIdx + 1;
        colOffset = 0;
      } else {
        commentLines = [];
        while (
          i < lines.length &&
          lines[i].trim().startsWith('--') &&
          (i === startIdx || !isDividerCommentLine(lines[i]))
        ) {
          commentLines.push({lineNum: i + 1, text: lines[i]});
          i++;
        }
        raw = lines.slice(startIdx, i).join('\n');
        endLine = i;
        colOffset = 0;
      }
      if (hasPendingDeclPrefix()) {
        pendingComments.push({
          startLine: startIdx + 1,
          endLine,
          lines: commentLines,
          raw,
        });
      } else {
        blocks.push({
          id: `comment-${blockCounter++}`,
          type: 'comment',
          raw,
          startLine: startIdx + 1,
          endLine,
          commentLines,
        });
      }
      continue;
    }

    // 6. Contiguous `import` / `public import` / `private import` / `meta import` / `import all` block
    if (colOffset === 0 && IMPORT_LINE_RE.test(line)) {
      flushPendingAsStandalone();
      const startIdx = i;
      const importLines: LeanCodeLine[] = [];
      while (i < lines.length && IMPORT_LINE_RE.test(lines[i])) {
        importLines.push({lineNum: i + 1, text: lines[i]});
        i++;
      }
      blocks.push({
        id: `imports-${blockCounter++}`,
        type: 'imports',
        raw: lines.slice(startIdx, i).join('\n'),
        startLine: startIdx + 1,
        endLine: i,
        importLines,
      });
      colOffset = 0;
      continue;
    }

    // 6b. `mutual` opener: emit directive and allow nested declarations until `end`
    if (
      colOffset === 0 &&
      lineIndent === 0 &&
      /^mutual(?:\s|--|$)/.test(trimmed)
    ) {
      flushPendingAsStandalone();
      blocks.push({
        id: `dir-mutual-${blockCounter++}`,
        type: 'directive',
        raw: line,
        startLine: i + 1,
        endLine: i + 1,
        directiveKind: 'mutual',
        directiveLines: [{lineNum: i + 1, text: line}],
      });
      inMutual = true;
      mutualIndent = -1;
      i++;
      colOffset = 0;
      continue;
    }

    // 7. Standalone or multi-line attribute `@[...]` preceding a declaration
    if (trimmed.startsWith('@[')) {
      if (isStandaloneAttributeLine(trimmed)) {
        if (inMutual && mutualIndent < 0) {
          mutualIndent = lineIndent;
        }
        pendingAttrs.push({
          text: colOffset > 0 ? trimmed : line,
          startLine: i + 1,
          endLine: i + 1,
        });
        i++;
        colOffset = 0;
        continue;
      }
      // Multi-line attribute `@[simp,\n  inline]` whose closing `]` is on a later line
      if (findMatchingBracket(trimmed, 1) < 0) {
        const lastBracketRow = getLastClosingBracketRow();
        if (lastBracketRow > i) {
          const maxAdditionalLines =
            remainingUnclosedAttrScanBudget > 0
              ? Math.max(8, remainingUnclosedAttrScanBudget)
              : 8;
          const maxEndRow = Math.min(lastBracketRow, i + maxAdditionalLines);
          const attrScan = findMultiLineAttributeEnd(
            lines,
            i,
            startCol,
            startCol + 1,
            maxEndRow
          );
          if (attrScan.closed) {
            if (inMutual && mutualIndent < 0) {
              mutualIndent = lineIndent;
            }
            pendingAttrs.push({
              text: attrScan.text,
              startLine: i + 1,
              endLine: attrScan.endRow + 1,
            });
            if (
              lines[attrScan.endRow]
                .slice(attrScan.closeColExclusive)
                .trim() !== ''
            ) {
              i = attrScan.endRow;
              colOffset = attrScan.closeColExclusive;
            } else {
              i = attrScan.endRow + 1;
              colOffset = 0;
            }
            continue;
          }
          remainingUnclosedAttrScanBudget = Math.max(
            0,
            remainingUnclosedAttrScanBudget - attrScan.scannedLines
          );
        }
      }
    }

    // 7b. Standalone modifier line (`unsafe`, `partial`, `private`, ...) preceding a declaration
    const standaloneMods = parseStandaloneModifiersLine(line);
    if (standaloneMods) {
      if (inMutual && mutualIndent < 0) {
        mutualIndent = lineIndent;
      }
      pendingModifiers.push({
        text: colOffset > 0 ? trimmed : line,
        mods: standaloneMods,
        lineNum: i + 1,
      });
      i++;
      colOffset = 0;
      continue;
    }

    // 8. Declaration (`theorem`, `lemma`, `def`, `structure`, `inductive`, `axiom`, ...)
    // Allowed at column 0, inside `mutual`, inside `namespace`/`section`, or after `... in` / `/- ... -/`.
    const declHeader = parseDeclarationHeaderStart(line, true);
    if (declHeader) {
      if (inMutual && mutualIndent < 0) {
        mutualIndent = lineIndent;
      }
      const declIndent = lineIndent;
      const declStartIdx = i;
      const firstCodeText = colOffset > 0 ? trimmed : line;
      const prefixLines: LeanCodeLine[] = [
        ...pendingModifiers.map(m => {
          return {lineNum: m.lineNum, text: m.text};
        }),
        ...pendingComments.flatMap(c => c.lines),
      ].sort((a, b) => a.lineNum - b.lineNum);
      const codeLines: LeanCodeLine[] = [
        ...prefixLines,
        {lineNum: i + 1, text: firstCodeText},
      ];
      const combinedHeader = {
        ...declHeader,
        modifiers: [
          ...pendingModifiers.flatMap(m => m.mods),
          ...declHeader.modifiers,
        ],
      };

      const isTypeDecl =
        combinedHeader.kind === 'structure' ||
        combinedHeader.kind === 'class' ||
        combinedHeader.kind === 'inductive' ||
        combinedHeader.kind === 'coinductive';

      const lexState = createLeanLexState();
      let lastNonBlankStripped = '';
      let inWhereBlock = false;
      const updateWhereBlock = (stripped: string) => {
        if (
          !hasOpenDelimiters(lexState) &&
          /(?:^|[\s()[\]{}])where(?=$|\s)/.test(stripped)
        ) {
          inWhereBlock = true;
        }
      };
      for (const cl of codeLines) {
        const s = advanceLeanLexState(lexState, cl.text);
        if (s.trim() !== '') {
          lastNonBlankStripped = s;
          updateWhereBlock(s);
        }
      }

      i++;
      colOffset = 0;

      // Collect continuation lines belonging to this declaration.
      while (i < lines.length) {
        const cur = lines[i];
        const curTrimmed = cur.trim();

        // Inside an unclosed multi-line comment, string, raw string, or guillemet:
        // always consume the line regardless of blankness or column-0 indentation.
        if (hasOpenLiteralOrComment(lexState)) {
          codeLines.push({lineNum: i + 1, text: cur});
          const s = advanceLeanLexState(lexState, cur);
          if (s.trim() !== '') {
            lastNonBlankStripped = s;
            updateWhereBlock(s);
          }
          i++;
          continue;
        }

        // Blank line: continue if next non-blank line is more indented,
        // is a `|` arm / termination clause at declIndent, or delimiters remain open.
        if (curTrimmed === '') {
          let lookahead = i + 1;
          while (lookahead < lines.length && lines[lookahead].trim() === '') {
            lookahead++;
          }
          if (lookahead < lines.length) {
            const nextLine = lines[lookahead];
            const nextIndent = getLineIndent(nextLine);
            const nextTrimmed = nextLine.trim();
            if (
              (nextIndent > declIndent &&
                (hasOpenDelimiters(lexState) ||
                  !startsNewTopLevelCommandInDeclBody(
                    nextLine,
                    isTypeDecl,
                    inWhereBlock
                  ))) ||
              (hasOpenDelimiters(lexState) && nextIndent >= declIndent) ||
              (nextIndent === declIndent &&
                (nextTrimmed.startsWith('|') ||
                  /^(?:termination_by|decreasing_by|partial_fixpoint|coinductive_fixpoint|inductive_fixpoint)\b/.test(
                    nextTrimmed
                  )))
            ) {
              while (i < lookahead) {
                codeLines.push({lineNum: i + 1, text: lines[i]});
                i++;
              }
              continue;
            }
          }
          break;
        }

        const curIndent = getLineIndent(cur);

        // More-indented line belongs to the active declaration unless delimiters
        // are balanced and a new top-level declaration or directive starts.
        if (curIndent > declIndent) {
          if (
            !hasOpenDelimiters(lexState) &&
            startsNewTopLevelCommandInDeclBody(cur, isTypeDecl, inWhereBlock)
          ) {
            break;
          }
          codeLines.push({lineNum: i + 1, text: cur});
          const s = advanceLeanLexState(lexState, cur);
          if (s.trim() !== '') {
            lastNonBlankStripped = s;
            updateWhereBlock(s);
          }
          i++;
          continue;
        }

        // Column-0 / same-indent `--` comment inside an ongoing indented body:
        // peek past consecutive comments/blanks to see if an indented body continues.
        if (
          curIndent === declIndent &&
          curTrimmed.startsWith('--') &&
          !isDividerCommentLine(cur)
        ) {
          let lookahead = i + 1;
          while (
            lookahead < lines.length &&
            (lines[lookahead].trim() === '' ||
              (lines[lookahead].trim().startsWith('--') &&
                !isDividerCommentLine(lines[lookahead])))
          ) {
            lookahead++;
          }
          if (
            lookahead < lines.length &&
            getLineIndent(lines[lookahead]) > declIndent &&
            (hasOpenDelimiters(lexState) ||
              !startsNewTopLevelCommandInDeclBody(
                lines[lookahead],
                isTypeDecl,
                inWhereBlock
              ))
          ) {
            codeLines.push({lineNum: i + 1, text: cur});
            i++;
            continue;
          }
        }

        // Open parens/brackets/braces or trailing `:=` / `by` / `where` / `extends` / `=>`:
        // continue even at `curIndent === declIndent` unless a brand-new top-level command starts
        // when delimiters are balanced.
        if (
          curIndent === declIndent &&
          (hasOpenDelimiters(lexState) ||
            (lineExpectsDeclContinuation(lastNonBlankStripped) &&
              !startsNewTopLevelCommandInDeclBody(
                cur,
                isTypeDecl,
                inWhereBlock
              ) &&
              !isDividerCommentLine(cur)))
        ) {
          codeLines.push({lineNum: i + 1, text: cur});
          const s = advanceLeanLexState(lexState, cur);
          if (s.trim() !== '') {
            lastNonBlankStripped = s;
            updateWhereBlock(s);
          }
          i++;
          continue;
        }

        // Same-indent `|`, `where`, `extends`, non-`instance` `deriving`,
        // `termination_by`, `decreasing_by`, or fixpoint clauses continue the active declaration.
        if (
          curIndent === declIndent &&
          (curTrimmed.startsWith('|') ||
            (isTypeDecl && /^extends\b/.test(curTrimmed)) ||
            (/^deriving\b/.test(curTrimmed) &&
              !startsNewTopLevelCommandInDeclBody(
                cur,
                isTypeDecl,
                inWhereBlock
              )) ||
            /^(?:where|termination_by|decreasing_by|partial_fixpoint|coinductive_fixpoint|inductive_fixpoint)\b/.test(
              curTrimmed
            ))
        ) {
          codeLines.push({lineNum: i + 1, text: cur});
          const s = advanceLeanLexState(lexState, cur);
          if (s.trim() !== '') {
            lastNonBlankStripped = s;
            updateWhereBlock(s);
          }
          i++;
          continue;
        }

        break;
      }

      const candidateStarts: number[] = [declStartIdx + 1];
      if (pendingDoc) candidateStarts.push(pendingDoc.startLine);
      if (pendingAttrs.length > 0) {
        candidateStarts.push(pendingAttrs[0].startLine);
      }
      if (pendingModifiers.length > 0) {
        candidateStarts.push(pendingModifiers[0].lineNum);
      }
      if (pendingComments.length > 0) {
        candidateStarts.push(pendingComments[0].startLine);
      }
      const blockStartLine = Math.min(...candidateStarts);
      const blockEndLine = codeLines[codeLines.length - 1].lineNum;
      const details = buildDeclarationDetails(
        combinedHeader,
        pendingAttrs.map(a => a.text),
        pendingDoc?.text,
        pendingDoc?.startLine,
        pendingDoc?.endLine,
        codeLines
      );
      const rawText =
        loopStartCol > 0 && blockStartLine === declStartIdx + 1
          ? codeLines.map(l => l.text).join('\n')
          : lines.slice(blockStartLine - 1, blockEndLine).join('\n');
      blocks.push({
        id: `decl-${combinedHeader.kind}-${
          combinedHeader.name || blockCounter
        }-${blockCounter++}`,
        type: 'declaration',
        raw: rawText,
        startLine: blockStartLine,
        endLine: blockEndLine,
        decl: details,
      });
      pendingDoc = null;
      pendingAttrs = [];
      pendingModifiers = [];
      pendingComments = [];
      continue;
    }

    // Any column-0 non-declaration exits an unclosed `mutual` scope cleanly.
    if (inMutual && colOffset === 0 && lineIndent === 0) {
      inMutual = false;
      mutualIndent = -1;
    }

    // 9. Scope / file directives (`module`, `namespace`, `section`, `end`, `open`, `variable`, ...)
    const dirKeyword = getDirectiveKeyword(line, true);
    if (dirKeyword) {
      flushPendingAsStandalone();
      const startIdx = i;

      // Support Lean 4 `... in <command>` prefix scoping (e.g. `open Nat in theorem ...`,
      // `set_option ... in\n  axiom ...`) so scoped declarations are never hidden inside directives.
      if (IN_SCOPING_DIRECTIVES.has(dirKeyword)) {
        const strippedLine = stripLeanCommentsAndStrings(line);
        const inMatch = strippedLine.match(/(?:^|\s)(in)(?=$|\s)/);
        if (inMatch && inMatch.index !== undefined) {
          const inEnd = inMatch.index + inMatch[0].length;
          const trailing = line.slice(inEnd);
          const prefixText = line.slice(0, inEnd).trimEnd();
          blocks.push({
            id: `dir-${dirKeyword}-${blockCounter++}`,
            type: 'directive',
            raw: prefixText,
            startLine: startIdx + 1,
            endLine: startIdx + 1,
            directiveKind: dirKeyword,
            directiveLines: [{lineNum: startIdx + 1, text: prefixText}],
          });
          if (trailing.trim() !== '') {
            colOffset = loopStartCol + inEnd;
          } else {
            i = startIdx + 1;
            colOffset = 0;
          }
          continue;
        }
      }

      const dirLines: LeanCodeLine[] = [
        {lineNum: i + 1, text: colOffset > 0 ? trimmed : line},
      ];
      i++;
      colOffset = 0;

      if (!SINGLE_LINE_DIRECTIVES.has(dirKeyword)) {
        const isBodyDir = BODY_DIRECTIVES.has(dirKeyword);
        while (
          i < lines.length &&
          lines[i].trim() !== '' &&
          getLineIndent(lines[i]) > lineIndent
        ) {
          const nextTrimmed = lines[i].trim();
          if (
            !isBodyDir &&
            (parseDeclarationHeaderStart(lines[i], true) ||
              nextTrimmed.startsWith('/-!') ||
              nextTrimmed.startsWith('/--') ||
              nextTrimmed.startsWith('@['))
          ) {
            break;
          }
          dirLines.push({lineNum: i + 1, text: lines[i]});
          i++;
        }
      }

      blocks.push({
        id: `dir-${dirKeyword}-${blockCounter++}`,
        type: 'directive',
        raw:
          loopStartCol > 0
            ? dirLines.map(l => l.text).join('\n')
            : lines.slice(startIdx, i).join('\n'),
        startLine: startIdx + 1,
        endLine: i,
        directiveKind: dirKeyword,
        directiveLines: dirLines,
      });
      continue;
    }

    // 10. Fallback: any unrecognized top-level Lean snippet stays intact as a directive block.
    flushPendingAsStandalone();
    const startIdx = i;
    const fallbackLines: LeanCodeLine[] = [
      {lineNum: i + 1, text: colOffset > 0 ? trimmed : line},
    ];
    i++;
    colOffset = 0;
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      getLineIndent(lines[i]) > lineIndent &&
      !parseDeclarationHeaderStart(lines[i], true)
    ) {
      fallbackLines.push({lineNum: i + 1, text: lines[i]});
      i++;
    }
    blocks.push({
      id: `code-${blockCounter++}`,
      type: 'directive',
      raw:
        loopStartCol > 0
          ? fallbackLines.map(l => l.text).join('\n')
          : lines.slice(startIdx, i).join('\n'),
      startLine: startIdx + 1,
      endLine: i,
      directiveKind: 'code',
      directiveLines: fallbackLines,
    });

    // Mandatory forward-progress invariant.
    if (i < loopStartI || (i === loopStartI && colOffset <= loopStartCol)) {
      i = loopStartI + 1;
      colOffset = 0;
    }
  }

  flushPendingAsStandalone();
  return blocks;
}

export interface LeanHighlightOptions {
  initialBlockCommentDepth?: number;
  forceComment?: boolean;
}

export interface AlignLeanCodeLinesOptions {
  initialLeftCommentDepth?: number;
  initialRightCommentDepth?: number;
  forceComment?: boolean;
}

/**
 * Tokenize a single line of Lean 4 code for syntax highlighting while tracking
 * nested multi-line block comment (`/- ... -/`) depth across lines.
 * Preserves exact character offsets so intra-line diff ranges map 1:1.
 */
export function tokenizeLeanLineWithState(
  line: string,
  inTacticContext = false,
  initialBlockCommentDepth = 0
): {tokens: LeanHighlightToken[]; blockCommentDepth: number} {
  let blockCommentDepth = Math.max(0, initialBlockCommentDepth);
  if (!line) return {tokens: [], blockCommentDepth};
  const tokens: LeanHighlightToken[] = [];
  const push = (text: string, kind: LeanTokenKind) => {
    if (!text) return;
    const prev = tokens[tokens.length - 1];
    if (prev && prev.kind === kind) {
      prev.text += text;
    } else {
      tokens.push({text, kind});
    }
  };

  let i = 0;
  const len = line.length;

  while (i < len) {
    if (blockCommentDepth > 0) {
      const start = i;
      while (i < len && blockCommentDepth > 0) {
        if (line[i] === '/' && line[i + 1] === '-') {
          blockCommentDepth++;
          i += 2;
        } else if (line[i] === '-' && line[i + 1] === '/') {
          blockCommentDepth--;
          i += 2;
        } else {
          i++;
        }
      }
      push(line.slice(start, i), 'comment');
      continue;
    }

    const ch = line[i];
    const next = i + 1 < len ? line[i + 1] : '';

    // Whitespace
    if (/\s/.test(ch)) {
      let j = i + 1;
      while (j < len && /\s/.test(line[j])) j++;
      push(line.slice(i, j), 'plain');
      i = j;
      continue;
    }

    // Line comment `-- ...`
    if (ch === '-' && next === '-') {
      push(line.slice(i), 'comment');
      break;
    }

    // Inline or multi-line block comment `/- ... -/`
    if (ch === '/' && next === '-') {
      blockCommentDepth = 1;
      let j = i + 2;
      while (j < len && blockCommentDepth > 0) {
        if (line[j] === '/' && line[j + 1] === '-') {
          blockCommentDepth++;
          j += 2;
        } else if (line[j] === '-' && line[j + 1] === '/') {
          blockCommentDepth--;
          j += 2;
        } else {
          j++;
        }
      }
      push(line.slice(i, j), 'comment');
      i = j;
      continue;
    }

    // Raw string literal `r"..."`, `r#"..."#`, `r##"..."##`
    const rawOpen = matchRawStringOpen(line, i);
    if (rawOpen) {
      let j = rawOpen.contentStart;
      while (j < len) {
        if (matchRawStringCloseAt(line, j, rawOpen.hashes)) {
          j += 1 + rawOpen.hashes;
          break;
        }
        j++;
      }
      push(line.slice(i, j), 'string');
      i = j;
      continue;
    }

    // Interpolated string `s!"..."`, `m!"..."`, `f!"..."`, brace-aware
    // so `s!"{'"'}"` or `s!"{r#"""#}"` cannot flip quote parity.
    if (
      (ch === 's' || ch === 'm' || ch === 'f') &&
      next === '!' &&
      line[i + 2] === '"' &&
      !hasIdentPartBefore(line, i)
    ) {
      let j = i + 3;
      let braceDepth = 0;
      while (j < len) {
        if (braceDepth === 0) {
          if (line[j] === '\\' && j + 1 < len) j += 2;
          else if (line[j] === '{') {
            braceDepth = 1;
            j++;
          } else if (line[j] === '"') {
            j++;
            break;
          } else {
            j++;
          }
        } else {
          const innerRaw = matchRawStringOpen(line, j);
          if (innerRaw) {
            let k = innerRaw.contentStart;
            while (k < len) {
              if (matchRawStringCloseAt(line, k, innerRaw.hashes)) {
                k += 1 + innerRaw.hashes;
                break;
              }
              k++;
            }
            j = k;
            continue;
          }
          const innerChar = matchCharLiteral(line, j);
          if (innerChar > 0) {
            j += innerChar;
            continue;
          }
          if (line[j] === '"') {
            j++;
            while (j < len) {
              if (line[j] === '\\' && j + 1 < len) j += 2;
              else if (line[j] === '"') {
                j++;
                break;
              } else j++;
            }
            continue;
          }
          if (line[j] === '{') braceDepth++;
          else if (line[j] === '}') braceDepth--;
          j++;
        }
      }
      push(line.slice(i, j), 'string');
      i = j;
      continue;
    }

    // String literal `"..."`
    if (ch === '"') {
      let j = i + 1;
      while (j < len) {
        if (line[j] === '\\' && j + 1 < len) j += 2;
        else if (line[j] === '"') {
          j++;
          break;
        } else j++;
      }
      push(line.slice(i, j), 'string');
      i = j;
      continue;
    }

    // Character literal `'a'`, `'"'`, `'\n'`, `'\x41'`, `'\u0041'`, `'😀'`
    const charLen = matchCharLiteral(line, i);
    if (charLen > 0) {
      push(line.slice(i, i + charLen), 'string');
      i += charLen;
      continue;
    }

    // Attribute `@[...]` (supports nested brackets like `@[simp [foo]]`)
    if (ch === '@' && next === '[') {
      const closeIdx = findMatchingBracket(line, i + 1);
      const end = closeIdx >= 0 ? closeIdx + 1 : len;
      push(line.slice(i, end), 'attribute');
      i = end;
      continue;
    }

    // Goal-focusing bullet `·`
    if (ch === '·') {
      push(ch, 'bullet');
      i++;
      continue;
    }

    // Number literal (hex `0x...`, binary `0b...`, octal `0o...`, decimal/float)
    if (ch >= '0' && ch <= '9' && !hasIdentPartBefore(line, i)) {
      NUMBER_LITERAL_RE.lastIndex = i;
      const numMatch = NUMBER_LITERAL_RE.exec(line);
      if (numMatch) {
        push(numMatch[0], 'number');
        i += numMatch[0].length;
        continue;
      }
    }

    // Escaped identifier `«...»`
    if (ch === '«') {
      const closeIdx = line.indexOf('»', i + 1);
      const end = closeIdx >= 0 ? closeIdx + 1 : len;
      const inner = closeIdx >= 0 ? line.slice(i + 1, closeIdx) : '';
      push(
        line.slice(i, end),
        SOUNDNESS_GUILLEMET_NAMES.has(inner) ? 'sorry' : 'plain'
      );
      i = end;
      continue;
    }

    // Identifiers & keywords
    const {ch: startChar, len: startLen} = getCharAt(line, i);
    if (startChar && isIdentStart(startChar)) {
      let j = i + startLen;
      while (j < len) {
        const {ch: partChar, len: partLen} = getCharAt(line, j);
        if (!partChar || !isIdentPart(partChar)) break;
        j += partLen;
      }
      const word = line.slice(i, j);
      if (LEAN_SORRY_TOKENS.has(word)) {
        push(word, 'sorry');
      } else if (LEAN_KEYWORDS.has(word)) {
        push(word, 'keyword');
      } else if (
        LEAN_TACTICS.has(word) &&
        (inTacticContext || UNAMBIGUOUS_TACTICS.has(word))
      ) {
        push(word, 'tactic');
      } else if (LEAN_BUILTIN_TYPES.has(word)) {
        push(word, 'type');
      } else if (LEAN_LITERALS.has(word)) {
        push(word, 'literal');
      } else {
        push(word, 'plain');
      }
      i = j;
      continue;
    }

    // Unicode math / logic operators
    if (UNICODE_OPERATORS.has(ch)) {
      push(ch, 'operator');
      i++;
      continue;
    }

    // Multi-char ASCII operators
    let matchedOp = '';
    for (const op of MULTI_CHAR_OPERATORS) {
      if (line.startsWith(op, i)) {
        matchedOp = op;
        break;
      }
    }
    if (matchedOp) {
      push(matchedOp, 'operator');
      i += matchedOp.length;
      continue;
    }

    push(ch, 'plain');
    i++;
  }

  return {tokens, blockCommentDepth};
}

/**
 * Tokenize a single line of Lean 4 code for syntax highlighting.
 * Preserves exact character offsets so intra-line diff ranges map 1:1.
 */
export function tokenizeLeanLine(
  line: string,
  inTacticContext = false,
  initialBlockCommentDepth = 0
): LeanHighlightToken[] {
  return tokenizeLeanLineWithState(
    line,
    inTacticContext,
    initialBlockCommentDepth
  ).tokens;
}

/**
 * Compute the entry block-comment (`/- ... -/`) nesting depth for each line in
 * `lines` along with the exit depth after the last line.
 */
export function computeBlockCommentDepths(
  lines: LeanCodeLine[],
  initialDepth = 0
): {depths: number[]; finalDepth: number} {
  const depths: number[] = [];
  let depth = Math.max(0, initialDepth);
  for (const cl of lines) {
    depths.push(depth);
    depth = tokenizeLeanLineWithState(cl.text, false, depth).blockCommentDepth;
  }
  return {depths, finalDepth: depth};
}

function renderLeanTokenSpan(
  text: string,
  kind: LeanTokenKind
): TemplateResult | string {
  if (kind === 'plain') return text;
  if (kind === 'operator' && UNICODE_OPERATORS.has(text)) {
    return html`<span class="lean-tok-operator lean-tok-unicode-op"
      >${text}</span
    >`;
  }
  return html`<span class="lean-tok-${kind}">${text}</span>`;
}

/** Render a single line of Lean 4 code with syntax highlighting as a safe Lit TemplateResult. */
export function highlightLeanLine(
  line: string,
  inTacticContext = false,
  options: LeanHighlightOptions = {}
): TemplateResult {
  if (!line) return html``;
  const tokens: LeanHighlightToken[] = options.forceComment
    ? [{text: line, kind: 'comment'}]
    : tokenizeLeanLine(
        line,
        inTacticContext,
        options.initialBlockCommentDepth ?? 0
      );
  return html`${tokens.map(tok => renderLeanTokenSpan(tok.text, tok.kind))}`;
}

const LEAN_DIFF_WORD_RE =
  /«[^«»\n]+»|0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|[0-9][0-9_]*(?:\.[0-9_]+)?|[\p{L}_][\p{L}\p{N}_'?!₀-₉⁰-⁹]*|\s+|[∀∃λ→←↔⇒⇐⇔↦≤≥≠∧∨¬⊢⊥⊤⊔⊓∈∉⊆⊇⊂⊃∪∩∅⟨⟩×⊕⊗∘▸≡≈≃≅∣∑∏∫·]|:=|=>|->|<-|<->|<;>|\|\|\||&&&|\^\^\^|<<<|>>>|\|>|<\||[\s\S]/gu;

/**
 * Tokenize a Lean 4 line for intra-line diffing (preserving Unicode symbols,
 * guillemets, decimal/float/radix literals, and guaranteeing lossless
 * `tokens.join('') === line` with linear-time non-backtracking matching).
 */
export function tokenizeLeanDiffWords(line: string): string[] {
  if (!line) return [];
  return line.match(LEAN_DIFF_WORD_RE) || [];
}

/**
 * Render a syntax-highlighted Lean line with intra-line diff highlights
 * (`<del class="diff-highlight-del">` or `<ins class="diff-highlight-add">`)
 * overlaid cleanly across character offsets.
 *
 * Expects raw (uncoalesced) diff segments so `common` + `editType` segments
 * map 1:1 onto `fullLine` without opposite-side whitespace drops, and coalesces
 * pure-whitespace runs sandwiched between edits on this side only.
 */
export function renderLeanLineWithInlineDiff(
  fullLine: string,
  segments: InlineDiffSegment[],
  editType: 'deleted' | 'added',
  inTacticContext = false,
  options: LeanHighlightOptions = {}
): TemplateResult {
  if (!fullLine) return html``;

  // Build per-side segment list preserving exact 1:1 offsets with `fullLine`.
  const sideSegs: Array<{edited: boolean; text: string}> = [];
  for (const seg of segments) {
    if (seg.type === 'common') {
      const prev = sideSegs[sideSegs.length - 1];
      if (prev && !prev.edited) {
        prev.text += seg.text;
      } else {
        sideSegs.push({edited: false, text: seg.text});
      }
    } else if (seg.type === editType) {
      const prev = sideSegs[sideSegs.length - 1];
      if (prev && prev.edited) {
        prev.text += seg.text;
      } else {
        sideSegs.push({edited: true, text: seg.text});
      }
    }
  }

  // Coalesce pure-whitespace common runs sandwiched between two edits on this side.
  for (let idx = 1; idx + 1 < sideSegs.length; idx++) {
    if (
      !sideSegs[idx].edited &&
      /^\s+$/.test(sideSegs[idx].text) &&
      sideSegs[idx - 1].edited &&
      sideSegs[idx + 1].edited
    ) {
      sideSegs[idx].edited = true;
    }
  }

  const isEditedAt = new Array<boolean>(fullLine.length).fill(false);
  let pos = 0;
  for (const seg of sideSegs) {
    if (seg.edited) {
      for (let k = 0; k < seg.text.length && pos + k < fullLine.length; k++) {
        isEditedAt[pos + k] = true;
      }
    }
    pos += seg.text.length;
  }

  const tokens: LeanHighlightToken[] = options.forceComment
    ? [{text: fullLine, kind: 'comment'}]
    : tokenizeLeanLine(
        fullLine,
        inTacticContext,
        options.initialBlockCommentDepth ?? 0
      );
  const out: Array<TemplateResult | string> = [];
  let activeHighlight: Array<TemplateResult | string> = [];

  const flushHighlight = () => {
    if (activeHighlight.length === 0) return;
    if (editType === 'deleted') {
      out.push(html`<del class="diff-highlight-del">${activeHighlight}</del>`);
    } else {
      out.push(html`<ins class="diff-highlight-add">${activeHighlight}</ins>`);
    }
    activeHighlight = [];
  };

  let offset = 0;
  for (const tok of tokens) {
    const tokStart = offset;
    const tokEnd = offset + tok.text.length;
    offset = tokEnd;

    let runStart = tokStart;
    while (runStart < tokEnd) {
      const edited = isEditedAt[runStart];
      let runEnd = runStart + 1;
      while (runEnd < tokEnd && isEditedAt[runEnd] === edited) {
        runEnd++;
      }
      // Never split a UTF-16 surrogate pair across a highlight slice boundary.
      if (runEnd < tokEnd) {
        const lastCode = tok.text.charCodeAt(runEnd - tokStart - 1);
        const nextCode = tok.text.charCodeAt(runEnd - tokStart);
        if (
          lastCode >= 0xd800 &&
          lastCode <= 0xdbff &&
          nextCode >= 0xdc00 &&
          nextCode <= 0xdfff
        ) {
          runEnd++;
        }
      }
      const slice = tok.text.slice(runStart - tokStart, runEnd - tokStart);
      const rendered = renderLeanTokenSpan(slice, tok.kind);
      if (edited) {
        activeHighlight.push(rendered);
      } else {
        flushHighlight();
        out.push(rendered);
      }
      runStart = runEnd;
    }
  }
  flushHighlight();

  return html`${out}`;
}

/**
 * Align and diff two sequences of Lean code lines with two-tier delta-style
 * highlighting while preserving Lean 4 syntax highlighting.
 */
export function alignLeanCodeLines(
  linesA: LeanCodeLine[],
  linesB: LeanCodeLine[],
  inTacticContext = false,
  options: AlignLeanCodeLinesOptions = {}
): LeanAlignedCodeLine[] {
  const textsA = linesA.map(l => l.text);
  const textsB = linesB.map(l => l.text);
  const aligned = alignHomologousItems(
    textsA,
    textsB,
    tokenizeLeanDiffWords,
    DEFAULT_MAX_HOMOLOGOUS_DISTANCE,
    isLeanContentToken
  );

  const forceComment = Boolean(options.forceComment);
  const depthsA = forceComment
    ? undefined
    : computeBlockCommentDepths(linesA, options.initialLeftCommentDepth ?? 0)
        .depths;
  const depthsB = forceComment
    ? undefined
    : computeBlockCommentDepths(linesB, options.initialRightCommentDepth ?? 0)
        .depths;

  const result: LeanAlignedCodeLine[] = [];
  for (const item of aligned) {
    if (item.type === 'unchanged') {
      const leftLine = linesA[item.aIdx];
      const rightLine = linesB[item.bIdx];
      const leftDepth = depthsA ? depthsA[item.aIdx] : 0;
      const rightDepth = depthsB ? depthsB[item.bIdx] : 0;
      const leftContent = highlightLeanLine(leftLine.text, inTacticContext, {
        initialBlockCommentDepth: leftDepth,
        forceComment,
      });
      const rightContent =
        leftDepth === rightDepth
          ? leftContent
          : highlightLeanLine(rightLine.text, inTacticContext, {
              initialBlockCommentDepth: rightDepth,
              forceComment,
            });
      result.push({
        status: 'unchanged',
        leftLine,
        rightLine,
        leftContent,
        rightContent,
      });
    } else if (item.type === 'deleted') {
      const leftLine = linesA[item.aIdx];
      result.push({
        status: 'deleted',
        leftLine,
        leftContent: highlightLeanLine(leftLine.text, inTacticContext, {
          initialBlockCommentDepth: depthsA ? depthsA[item.aIdx] : 0,
          forceComment,
        }),
      });
    } else if (item.type === 'added') {
      const rightLine = linesB[item.bIdx];
      result.push({
        status: 'added',
        rightLine,
        rightContent: highlightLeanLine(rightLine.text, inTacticContext, {
          initialBlockCommentDepth: depthsB ? depthsB[item.bIdx] : 0,
          forceComment,
        }),
      });
    } else {
      const leftLine = linesA[item.aIdx];
      const rightLine = linesB[item.bIdx];
      const leftOpts: LeanHighlightOptions = {
        initialBlockCommentDepth: depthsA ? depthsA[item.aIdx] : 0,
        forceComment,
      };
      const rightOpts: LeanHighlightOptions = {
        initialBlockCommentDepth: depthsB ? depthsB[item.bIdx] : 0,
        forceComment,
      };
      const wordsA = tokenizeLeanDiffWords(leftLine.text);
      const wordsB = tokenizeLeanDiffWords(rightLine.text);
      const rawDiff = computeSequenceDiff(wordsA, wordsB);
      const commonNonWs = rawDiff.filter(
        s => s.type === 'common' && !/^\s+$/.test(s.text)
      ).length;
      if (commonNonWs < 2) {
        result.push({
          status: 'modified',
          leftLine,
          rightLine,
          leftContent: html`<del class="diff-highlight-del"
            >${highlightLeanLine(leftLine.text, inTacticContext, leftOpts)}</del
          >`,
          rightContent: html`<ins class="diff-highlight-add"
            >${highlightLeanLine(
              rightLine.text,
              inTacticContext,
              rightOpts
            )}</ins
          >`,
        });
      } else {
        result.push({
          status: 'modified',
          leftLine,
          rightLine,
          leftContent: renderLeanLineWithInlineDiff(
            leftLine.text,
            rawDiff,
            'deleted',
            inTacticContext,
            leftOpts
          ),
          rightContent: renderLeanLineWithInlineDiff(
            rightLine.text,
            rawDiff,
            'added',
            inTacticContext,
            rightOpts
          ),
        });
      }
    }
  }
  return result;
}

function alignStructureFields(
  fieldsA: LeanStructureField[],
  fieldsB: LeanStructureField[]
): {fields: LeanAlignedField[]; changed: boolean} {
  const keysA = fieldsA.map(
    f =>
      `${f.name} : ${f.typeText}${
        f.defaultValue ? ` := ${f.defaultValue}` : ''
      } ${f.comment ?? ''}`
  );
  const keysB = fieldsB.map(
    f =>
      `${f.name} : ${f.typeText}${
        f.defaultValue ? ` := ${f.defaultValue}` : ''
      } ${f.comment ?? ''}`
  );
  const aligned = alignHomologousItems(
    keysA,
    keysB,
    tokenizeLeanDiffWords,
    0.75,
    isLeanContentToken
  );

  const out: LeanAlignedField[] = [];
  let changed = false;

  for (const item of aligned) {
    if (item.type === 'unchanged') {
      const leftField = fieldsA[item.aIdx];
      const rightField = fieldsB[item.bIdx];
      out.push({
        status: 'unchanged',
        leftField,
        rightField,
        leftTypeHtml: highlightLeanLine(leftField.typeText),
        rightTypeHtml: highlightLeanLine(rightField.typeText),
        leftCommentHtml: leftField.comment
          ? renderInlineMarkdown(leftField.comment)
          : undefined,
        rightCommentHtml: rightField.comment
          ? renderInlineMarkdown(rightField.comment)
          : undefined,
      });
    } else if (item.type === 'deleted') {
      changed = true;
      const leftField = fieldsA[item.aIdx];
      out.push({
        status: 'deleted',
        leftField,
        leftTypeHtml: html`<del class="diff-highlight-del"
          >${highlightLeanLine(leftField.typeText)}</del
        >`,
        leftCommentHtml: leftField.comment
          ? html`<del class="diff-highlight-del"
              >${renderInlineMarkdown(leftField.comment)}</del
            >`
          : undefined,
      });
    } else if (item.type === 'added') {
      changed = true;
      const rightField = fieldsB[item.bIdx];
      out.push({
        status: 'added',
        rightField,
        rightTypeHtml: html`<ins class="diff-highlight-add"
          >${highlightLeanLine(rightField.typeText)}</ins
        >`,
        rightCommentHtml: rightField.comment
          ? html`<ins class="diff-highlight-add"
              >${renderInlineMarkdown(rightField.comment)}</ins
            >`
          : undefined,
      });
    } else {
      changed = true;
      const leftField = fieldsA[item.aIdx];
      const rightField = fieldsB[item.bIdx];
      let leftTypeHtml: TemplateResult;
      let rightTypeHtml: TemplateResult;
      if (leftField.typeText === rightField.typeText) {
        leftTypeHtml = highlightLeanLine(leftField.typeText);
        rightTypeHtml = leftTypeHtml;
      } else {
        const segs = computeSequenceDiff(
          tokenizeLeanDiffWords(leftField.typeText),
          tokenizeLeanDiffWords(rightField.typeText)
        );
        leftTypeHtml = renderLeanLineWithInlineDiff(
          leftField.typeText,
          segs,
          'deleted'
        );
        rightTypeHtml = renderLeanLineWithInlineDiff(
          rightField.typeText,
          segs,
          'added'
        );
      }

      let leftCommentHtml: TemplateResult | undefined;
      let rightCommentHtml: TemplateResult | undefined;
      if ((leftField.comment ?? '') === (rightField.comment ?? '')) {
        if (leftField.comment) {
          leftCommentHtml = renderInlineMarkdown(leftField.comment);
          rightCommentHtml = leftCommentHtml;
        }
      } else {
        const diffComment = renderInlineTextDiff(
          leftField.comment ?? '',
          rightField.comment ?? '',
          'p'
        );
        leftCommentHtml = leftField.comment ? diffComment.leftHtml : undefined;
        rightCommentHtml = rightField.comment
          ? diffComment.rightHtml
          : undefined;
      }

      out.push({
        status: 'modified',
        leftField,
        rightField,
        leftTypeHtml,
        rightTypeHtml,
        leftCommentHtml,
        rightCommentHtml,
      });
    }
  }

  return {fields: out, changed};
}

function buildDeclarationDiffData(
  declA: LeanDeclarationDetails,
  declB: LeanDeclarationDetails
): LeanDeclarationDiffData {
  const docA = declA.docComment ?? '';
  const docB = declB.docComment ?? '';
  const docChanged = docA !== docB;
  let docLeftHtml: TemplateResult | undefined;
  let docRightHtml: TemplateResult | undefined;
  if (docChanged) {
    const docDiff = renderInlineTextDiff(docA, docB, 'p');
    docLeftHtml = docA ? docDiff.leftHtml : undefined;
    docRightHtml = docB ? docDiff.rightHtml : undefined;
  } else if (docA) {
    const rendered = renderMarkdownContent(docA);
    docLeftHtml = rendered;
    docRightHtml = rendered;
  }

  const sigDepthA = computeBlockCommentDepths(declA.signatureLines, 0);
  const sigDepthB = computeBlockCommentDepths(declB.signatureLines, 0);
  const signatureLines = alignLeanCodeLines(
    declA.signatureLines,
    declB.signatureLines,
    false
  );
  const signatureChanged = signatureLines.some(l => l.status !== 'unchanged');

  const inTactic = declA.isTacticProof || declB.isTacticProof;
  const bodyLines = alignLeanCodeLines(
    declA.bodyLines,
    declB.bodyLines,
    inTactic,
    {
      initialLeftCommentDepth: sigDepthA.finalDepth,
      initialRightCommentDepth: sigDepthB.finalDepth,
    }
  );
  const bodyChanged = bodyLines.some(l => l.status !== 'unchanged');

  let fields: LeanAlignedField[] | undefined;
  let fieldsChanged = false;
  if (declA.fields && declB.fields) {
    const alignedFields = alignStructureFields(declA.fields, declB.fields);
    fields = alignedFields.fields;
    fieldsChanged = alignedFields.changed;
  }

  return {
    docLeftHtml,
    docRightHtml,
    docChanged,
    signatureLines,
    signatureChanged,
    bodyLines,
    bodyChanged,
    fields,
    fieldsChanged,
  };
}

/** Render Lean Markdown docstring content safely using Lit templates via `marked`. */
export function renderMarkdownContent(markdown: string): TemplateResult {
  if (!markdown) return html``;
  const tokens = parseMarkdownBlocks(markdown);
  if (tokens.length === 0)
    return html`<p>${renderInlineMarkdown(markdown)}</p>`;
  return html`${tokens.map(tok => renderTokenToHtml(tok))}`;
}

function buildPairedRow(blockA: LeanBlock, blockB: LeanBlock): AlignedLeanRow {
  const row: AlignedLeanRow = {
    rowKey: `mod-${blockA.id}-${blockB.id}`,
    status: 'modified',
    leftBlock: blockA,
    rightBlock: blockB,
    leftStartLine: blockA.startLine,
    leftEndLine: blockA.endLine,
    rightStartLine: blockB.startLine,
    rightEndLine: blockB.endLine,
  };

  if (blockA.type === 'module_doc' && blockB.type === 'module_doc') {
    const tokensA = parseMarkdownBlocks(blockA.markdownContent ?? '');
    const tokensB = parseMarkdownBlocks(blockB.markdownContent ?? '');
    row.moduleDocRows = alignMarkdownTokens(tokensA, tokensB);
  } else if (
    blockA.type === 'section_banner' &&
    blockB.type === 'section_banner'
  ) {
    row.bannerDiff = renderInlineTextDiff(
      blockA.markdownContent ?? '',
      blockB.markdownContent ?? '',
      'h3'
    );
  } else if (blockA.type === 'imports' && blockB.type === 'imports') {
    row.lineDiff = alignLeanCodeLines(
      blockA.importLines ?? [],
      blockB.importLines ?? [],
      false
    );
  } else if (blockA.type === 'directive' && blockB.type === 'directive') {
    row.lineDiff = alignLeanCodeLines(
      blockA.directiveLines ?? [],
      blockB.directiveLines ?? [],
      false
    );
  } else if (blockA.type === 'comment' && blockB.type === 'comment') {
    row.lineDiff = alignLeanCodeLines(
      blockA.commentLines ?? [],
      blockB.commentLines ?? [],
      false,
      {forceComment: true}
    );
  } else if (
    blockA.type === 'declaration' &&
    blockB.type === 'declaration' &&
    blockA.decl &&
    blockB.decl
  ) {
    row.declDiff = buildDeclarationDiffData(blockA.decl, blockB.decl);
  }

  return row;
}

function makeUnchangedRow(
  blockA: LeanBlock,
  blockB: LeanBlock
): AlignedLeanRow {
  return {
    rowKey: `eq-${blockA.id}-${blockB.id}`,
    status: 'unchanged',
    leftBlock: blockA,
    rightBlock: blockB,
    leftStartLine: blockA.startLine,
    leftEndLine: blockA.endLine,
    rightStartLine: blockB.startLine,
    rightEndLine: blockB.endLine,
  };
}

function makeDeletedRow(blockA: LeanBlock): AlignedLeanRow {
  return {
    rowKey: `del-${blockA.id}`,
    status: 'deleted',
    leftBlock: blockA,
    leftStartLine: blockA.startLine,
    leftEndLine: blockA.endLine,
  };
}

function makeAddedRow(blockB: LeanBlock): AlignedLeanRow {
  return {
    rowKey: `add-${blockB.id}`,
    status: 'added',
    rightBlock: blockB,
    rightStartLine: blockB.startLine,
    rightEndLine: blockB.endLine,
  };
}

function getDeclarationIdentityKey(block: LeanBlock): string | null {
  if (block.type === 'declaration' && block.decl && block.decl.name) {
    return `${block.decl.kind}:${block.decl.name}`;
  }
  return null;
}

function blockPairScore(
  blockA: LeanBlock,
  blockB: LeanBlock,
  wordsA: string[],
  wordsB: string[],
  isSingletonInterval: boolean
): number {
  if (blockA.type !== blockB.type) return -1;
  // Same declaration kind + name strongly pairs even if the proof body was rewritten.
  const idA = getDeclarationIdentityKey(blockA);
  const idB = getDeclarationIdentityKey(blockB);
  if (idA && idB && idA === idB) {
    return 1.9;
  }
  // Same declaration name across kind change (e.g. lemma -> theorem)
  if (
    blockA.type === 'declaration' &&
    blockB.type === 'declaration' &&
    blockA.decl?.name &&
    blockA.decl.name === blockB.decl?.name
  ) {
    return 1.7;
  }
  const dist = computeNormalizedEditDistance(
    wordsA,
    wordsB,
    isLeanContentToken
  );
  if (dist <= DEFAULT_MAX_HOMOLOGOUS_DISTANCE) {
    return 1 + (1 - dist);
  }
  // Heavily-edited module docstrings or import blocks in the same diff interval
  // should still pair side-by-side for structured inner diffing.
  if (blockA.type === 'module_doc' || blockA.type === 'imports') {
    return 0.3 + 0.5 * (1 - dist);
  }
  if (
    isSingletonInterval &&
    (blockA.type !== 'declaration' ||
      (blockA.decl?.kind === blockB.decl?.kind &&
        !blockA.decl?.name &&
        !blockB.decl?.name))
  ) {
    return 0.01 + 0.1 * (1 - dist);
  }
  return -1;
}

/**
 * Align base (A) and revision (B) Lean 4 semantic blocks into diff rows.
 * Optimizes single-sided files and large diffs with prefix/suffix trimming,
 * declaration identity anchors, and homologous interval DP.
 */
export function alignLeanBlocks(
  blocksA: LeanBlock[],
  blocksB: LeanBlock[]
): AlignedLeanRow[] {
  const m = blocksA.length;
  const n = blocksB.length;
  if (m === 0 && n === 0) return [];
  if (m === 0) return blocksB.map(makeAddedRow);
  if (n === 0) return blocksA.map(makeDeletedRow);

  // Fast-path common prefix and suffix trimming for large proofs.
  let prefixLen = 0;
  while (
    prefixLen < m &&
    prefixLen < n &&
    blocksA[prefixLen].raw === blocksB[prefixLen].raw
  ) {
    prefixLen++;
  }
  let suffixLen = 0;
  while (
    suffixLen < m - prefixLen &&
    suffixLen < n - prefixLen &&
    blocksA[m - 1 - suffixLen].raw === blocksB[n - 1 - suffixLen].raw
  ) {
    suffixLen++;
  }

  const rows: AlignedLeanRow[] = [];
  for (let p = 0; p < prefixLen; p++) {
    rows.push(makeUnchangedRow(blocksA[p], blocksB[p]));
  }

  const midA = blocksA.slice(prefixLen, m - suffixLen);
  const midB = blocksB.slice(prefixLen, n - suffixLen);
  const mm = midA.length;
  const nn = midB.length;

  if (mm > 0 || nn > 0) {
    const anchorPairs: Array<{aIdx: number; bIdx: number}> = [];

    if (mm * nn <= 250000) {
      // Exact-match LCS DP on middle region using flat Int32Array
      const cols = nn + 1;
      const dp = new Int32Array((mm + 1) * cols);
      for (let i = 0; i < mm; i++) {
        const rawA = midA[i].raw;
        const rowOffset = i * cols;
        const nextRowOffset = (i + 1) * cols;
        for (let j = 0; j < nn; j++) {
          if (rawA === midB[j].raw) {
            dp[nextRowOffset + j + 1] = dp[rowOffset + j] + 1;
          } else {
            const left = dp[nextRowOffset + j];
            const up = dp[rowOffset + j + 1];
            dp[nextRowOffset + j + 1] = left >= up ? left : up;
          }
        }
      }
      let i = mm;
      let j = nn;
      while (i > 0 && j > 0) {
        if (midA[i - 1].raw === midB[j - 1].raw) {
          anchorPairs.push({aIdx: i - 1, bIdx: j - 1});
          i--;
          j--;
        } else if (dp[i * cols + (j - 1)] >= dp[(i - 1) * cols + j]) {
          j--;
        } else {
          i--;
        }
      }
      anchorPairs.reverse();
    } else {
      // Linear-time Patience-style anchor matching for very large diffs.
      anchorPairs.push(
        ...computeLinearExactAnchors(
          midA.map(b => b.raw),
          midB.map(b => b.raw)
        )
      );
    }

    let lastA = 0;
    let lastB = 0;

    const processInterval = (endA: number, endB: number) => {
      const unA = midA.slice(lastA, endA);
      const unB = midB.slice(lastB, endB);
      const uA = unA.length;
      const uB = unB.length;
      if (uA === 0 && uB === 0) return;
      if (uA === 0) {
        rows.push(...unB.map(makeAddedRow));
        return;
      }
      if (uB === 0) {
        rows.push(...unA.map(makeDeletedRow));
        return;
      }

      // Guard quadratic interval pairing for huge completely-rewritten sections.
      if (uA * uB > 10000) {
        const bByKey = new Map<string, number>();
        for (let b = 0; b < uB; b++) {
          const key = getDeclarationIdentityKey(unB[b]);
          if (key && !bByKey.has(key)) bByKey.set(key, b);
        }
        let bCursor = 0;
        for (let a = 0; a < uA; a++) {
          const key = getDeclarationIdentityKey(unA[a]);
          const matchedB = key ? bByKey.get(key) : undefined;
          if (matchedB !== undefined && matchedB >= bCursor) {
            while (bCursor < matchedB) {
              rows.push(makeAddedRow(unB[bCursor++]));
            }
            rows.push(buildPairedRow(unA[a], unB[matchedB]));
            bCursor = matchedB + 1;
          } else {
            rows.push(makeDeletedRow(unA[a]));
          }
        }
        while (bCursor < uB) {
          rows.push(makeAddedRow(unB[bCursor++]));
        }
        return;
      }

      const wordsA = unA.map(b => tokenizeLeanDiffWords(b.raw));
      const wordsB = unB.map(b => tokenizeLeanDiffWords(b.raw));
      const isSingleton = uA === 1 && uB === 1;

      const pairScore: number[][] = Array.from({length: uA}, (_, a) =>
        Array.from({length: uB}, (__, b) =>
          blockPairScore(unA[a], unB[b], wordsA[a], wordsB[b], isSingleton)
        )
      );

      const subDp: number[][] = Array.from({length: uA + 1}, () =>
        new Array(uB + 1).fill(0)
      );
      for (let a = 0; a < uA; a++) {
        for (let b = 0; b < uB; b++) {
          let best = Math.max(subDp[a][b + 1], subDp[a + 1][b]);
          if (pairScore[a][b] >= 0) {
            best = Math.max(best, subDp[a][b] + pairScore[a][b]);
          }
          subDp[a + 1][b + 1] = best;
        }
      }

      const intervalRows: AlignedLeanRow[] = [];
      let a = uA;
      let b = uB;
      while (a > 0 || b > 0) {
        if (
          a > 0 &&
          b > 0 &&
          pairScore[a - 1][b - 1] >= 0 &&
          Math.abs(
            subDp[a][b] - (subDp[a - 1][b - 1] + pairScore[a - 1][b - 1])
          ) < 1e-9
        ) {
          intervalRows.push(buildPairedRow(unA[a - 1], unB[b - 1]));
          a--;
          b--;
        } else if (b > 0 && (a === 0 || subDp[a][b - 1] >= subDp[a - 1][b])) {
          intervalRows.push(makeAddedRow(unB[b - 1]));
          b--;
        } else {
          intervalRows.push(makeDeletedRow(unA[a - 1]));
          a--;
        }
      }
      for (let k = intervalRows.length - 1; k >= 0; k--) {
        rows.push(intervalRows[k]);
      }
    };

    for (const anchor of anchorPairs) {
      processInterval(anchor.aIdx, anchor.bIdx);
      rows.push(makeUnchangedRow(midA[anchor.aIdx], midB[anchor.bIdx]));
      lastA = anchor.aIdx + 1;
      lastB = anchor.bIdx + 1;
    }
    processInterval(mm, nn);
  }

  for (let s = 0; s < suffixLen; s++) {
    rows.push(
      makeUnchangedRow(blocksA[m - suffixLen + s], blocksB[n - suffixLen + s])
    );
  }

  return rows;
}

function countBlocks(blocks: LeanBlock[]): LeanFileCounts {
  const counts: LeanFileCounts = {
    theorems: 0,
    definitions: 0,
    types: 0,
    instances: 0,
    axioms: 0,
    sorryCount: 0,
    admitCount: 0,
    partialCount: 0,
    unsafeCount: 0,
    nativeDecideCount: 0,
  };
  for (const block of blocks) {
    if (block.type === 'declaration' && block.decl) {
      const {kind, hygiene} = block.decl;
      if (kind === 'theorem' || kind === 'lemma' || kind === 'example') {
        counts.theorems++;
      } else if (kind === 'def' || kind === 'abbrev' || kind === 'opaque') {
        counts.definitions++;
      } else if (
        kind === 'structure' ||
        kind === 'class' ||
        kind === 'inductive' ||
        kind === 'coinductive'
      ) {
        counts.types++;
      } else if (kind === 'instance') {
        counts.instances++;
      }
      if (hygiene.isAxiom) counts.axioms++;
      if (hygiene.hasSorry) counts.sorryCount++;
      if (hygiene.hasAdmit) counts.admitCount++;
      if (hygiene.isPartial) counts.partialCount++;
      if (hygiene.isUnsafe) counts.unsafeCount++;
      if (hygiene.hasNativeDecide) counts.nativeDecideCount++;
    } else if (block.type === 'directive') {
      // Fail-closed defense-in-depth: scan ALL non-comment / non-declaration
      // code blocks (`macro`, `elab`, `initialize`, `#eval`, `attribute`,
      // fallback `code`, etc.) so soundness hazards can never hide outside a
      // recognized declaration and spoof a clean `0 sorry` badge.
      const clean = stripLeanCommentsAndStrings(block.raw);
      const ids = extractLeanIdentifiers(clean);
      if (ids.has('sorry') || ids.has('sorryAx')) counts.sorryCount++;
      if (ids.has('admit')) counts.admitCount++;
      if (
        ids.has('native_decide') ||
        ids.has('ofReduceBool') ||
        ids.has('ofReduceNat')
      ) {
        counts.nativeDecideCount++;
      }
      if (ids.has('axiom')) counts.axioms++;
      if (ids.has('unsafe')) counts.unsafeCount++;
      if (ids.has('partial')) counts.partialCount++;
    }
  }
  return counts;
}

/** Compute declaration and proof-hygiene summary across base and revision blocks. */
export function computeLeanSummary(
  blocksA: LeanBlock[],
  blocksB: LeanBlock[],
  alignedRows: AlignedLeanRow[]
): LeanDiffSummary {
  let addedDeclarations = 0;
  let deletedDeclarations = 0;
  let modifiedDeclarations = 0;
  for (const row of alignedRows) {
    if (row.status === 'added' && row.rightBlock?.type === 'declaration') {
      addedDeclarations++;
    } else if (
      row.status === 'deleted' &&
      row.leftBlock?.type === 'declaration'
    ) {
      deletedDeclarations++;
    } else if (
      row.status === 'modified' &&
      (row.leftBlock?.type === 'declaration' ||
        row.rightBlock?.type === 'declaration')
    ) {
      modifiedDeclarations++;
    }
  }
  return {
    base: countBlocks(blocksA),
    revision: countBlocks(blocksB),
    hasBase: blocksA.length > 0,
    hasRevision: blocksB.length > 0,
    addedDeclarations,
    deletedDeclarations,
    modifiedDeclarations,
  };
}

/** Attach comment threads to aligned Lean diff rows using line numbers and side. */
export function attachThreadsToLeanRows(
  rows: AlignedLeanRow[],
  threads: CommentThread[] = [],
  patchRange?: PatchRange
): {
  rowsWithThreads: AlignedLeanRowWithThreads[];
  fileLevelThreads: CommentThread[];
} {
  const {rowsWithThreads, fileLevelThreads} = attachThreadsToRows(
    rows,
    threads,
    patchRange
  );
  return {
    rowsWithThreads: rowsWithThreads as AlignedLeanRowWithThreads[],
    fileLevelThreads,
  };
}

/** Helper to format a compact 1-based line range pill label (e.g. `L95` or `L95–L111`). */
export function formatLineRange(startLine?: number, endLine?: number): string {
  if (startLine === undefined) return '';
  if (endLine === undefined || endLine <= startLine) return `L${startLine}`;
  return `L${startLine}–L${endLine}`;
}
