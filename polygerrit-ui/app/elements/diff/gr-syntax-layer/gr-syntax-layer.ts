/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {FILE, GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {CancelablePromise, util} from '../../../scripts/util';
import {DiffFileMetaInfo, DiffInfo} from '../../../types/diff';
import {DiffLayer, DiffLayerListener, HighlightJS} from '../../../types/types';
import {GrLibLoader} from '../../shared/gr-lib-loader/gr-lib-loader';
import {HLJS_LIBRARY_CONFIG} from '../../shared/gr-lib-loader/highlightjs_config';
import {Side} from '../../../constants/constants';

const LANGUAGE_MAP = new Map<string, string>([
  ['application/dart', 'dart'],
  ['application/json', 'json'],
  ['application/x-powershell', 'powershell'],
  ['application/typescript', 'typescript'],
  ['application/xml', 'xml'],
  ['application/xquery', 'xquery'],
  ['application/x-erb', 'erb'],
  ['text/css', 'css'],
  ['text/html', 'html'],
  ['text/javascript', 'js'],
  ['text/jsx', 'jsx'],
  ['text/tsx', 'jsx'],
  ['text/x-c', 'cpp'],
  ['text/x-c++src', 'cpp'],
  ['text/x-clojure', 'clojure'],
  ['text/x-cmake', 'cmake'],
  ['text/x-coffeescript', 'coffeescript'],
  ['text/x-common-lisp', 'lisp'],
  ['text/x-crystal', 'crystal'],
  ['text/x-csharp', 'csharp'],
  ['text/x-csrc', 'cpp'],
  ['text/x-d', 'd'],
  ['text/x-diff', 'diff'],
  ['text/x-django', 'django'],
  ['text/x-dockerfile', 'dockerfile'],
  ['text/x-ebnf', 'ebnf'],
  ['text/x-elm', 'elm'],
  ['text/x-erlang', 'erlang'],
  ['text/x-fortran', 'fortran'],
  ['text/x-fsharp', 'fsharp'],
  ['text/x-gherkin', 'gherkin'],
  ['text/x-go', 'go'],
  ['text/x-groovy', 'groovy'],
  ['text/x-haml', 'haml'],
  ['text/x-handlebars', 'handlebars'],
  ['text/x-haskell', 'haskell'],
  ['text/x-haxe', 'haxe'],
  ['text/x-iecst', 'iecst'],
  ['text/x-ini', 'ini'],
  ['text/x-java', 'java'],
  ['text/x-julia', 'julia'],
  ['text/x-kotlin', 'kotlin'],
  ['text/x-latex', 'latex'],
  ['text/x-less', 'less'],
  ['text/x-lua', 'lua'],
  ['text/x-mathematica', 'mathematica'],
  ['text/x-nginx-conf', 'nginx'],
  ['text/x-nsis', 'nsis'],
  ['text/x-objectivec', 'objectivec'],
  ['text/x-ocaml', 'ocaml'],
  ['text/x-perl', 'perl'],
  ['text/x-pgsql', 'pgsql'], // postgresql
  ['text/x-php', 'php'],
  ['text/x-properties', 'properties'],
  ['text/x-protobuf', 'protobuf'],
  ['text/x-puppet', 'puppet'],
  ['text/x-python', 'python'],
  ['text/x-q', 'q'],
  ['text/x-ruby', 'ruby'],
  ['text/x-rustsrc', 'rust'],
  ['text/x-scala', 'scala'],
  ['text/x-scss', 'scss'],
  ['text/x-scheme', 'scheme'],
  ['text/x-shell', 'shell'],
  ['text/x-soy', 'soy'],
  ['text/x-spreadsheet', 'excel'],
  ['text/x-sh', 'bash'],
  ['text/x-sql', 'sql'],
  ['text/x-swift', 'swift'],
  ['text/x-systemverilog', 'sv'],
  ['text/x-tcl', 'tcl'],
  ['text/x-torque', 'torque'],
  ['text/x-twig', 'twig'],
  ['text/x-vb', 'vb'],
  ['text/x-verilog', 'v'],
  ['text/x-vhdl', 'vhdl'],
  ['text/x-yaml', 'yaml'],
  ['text/vbscript', 'vbscript'],
]);
const ASYNC_DELAY = 10;

const CLASS_SAFELIST = new Set<string>([
  'gr-diff gr-syntax gr-syntax-attr',
  'gr-diff gr-syntax gr-syntax-attribute',
  'gr-diff gr-syntax gr-syntax-built_in',
  'gr-diff gr-syntax gr-syntax-comment',
  'gr-diff gr-syntax gr-syntax-doctag',
  'gr-diff gr-syntax gr-syntax-function',
  'gr-diff gr-syntax gr-syntax-keyword',
  'gr-diff gr-syntax gr-syntax-link',
  'gr-diff gr-syntax gr-syntax-literal',
  'gr-diff gr-syntax gr-syntax-meta',
  'gr-diff gr-syntax gr-syntax-meta-keyword',
  'gr-diff gr-syntax gr-syntax-name',
  'gr-diff gr-syntax gr-syntax-number',
  'gr-diff gr-syntax gr-syntax-params',
  'gr-diff gr-syntax gr-syntax-property',
  'gr-diff gr-syntax gr-syntax-regexp',
  'gr-diff gr-syntax gr-syntax-selector-attr',
  'gr-diff gr-syntax gr-syntax-selector-class',
  'gr-diff gr-syntax gr-syntax-selector-id',
  'gr-diff gr-syntax gr-syntax-selector-pseudo',
  'gr-diff gr-syntax gr-syntax-selector-tag',
  'gr-diff gr-syntax gr-syntax-string',
  'gr-diff gr-syntax gr-syntax-tag',
  'gr-diff gr-syntax gr-syntax-template-tag',
  'gr-diff gr-syntax gr-syntax-template-variable',
  'gr-diff gr-syntax gr-syntax-title',
  'gr-diff gr-syntax gr-syntax-type',
  'gr-diff gr-syntax gr-syntax-variable',
]);

const CPP_DIRECTIVE_WITH_LT_PATTERN = /^\s*#(if|define).*</;
const CPP_WCHAR_PATTERN = /L'(\\)?.'/g;
const JAVA_PARAM_ANNOT_PATTERN = /(@[^\s]+)\(([^)]+)\)/g;
const GO_BACKSLASH_LITERAL = "'\\\\'";
const GLOBAL_LT_PATTERN = /</g;

interface SyntaxLayerRange {
  start: number;
  length: number;
  className: string;
}

interface SyntaxLayerState {
  sectionIndex: number;
  lineIndex: number;
  baseContext: unknown;
  revisionContext: unknown;
  lineNums: {left: number; right: number};
  lastNotify: {left: number; right: number};
}

export class GrSyntaxLayer implements DiffLayer {
  diff?: DiffInfo;

  enabled = true;

  private baseRanges: SyntaxLayerRange[][] = [];

  private revisionRanges: SyntaxLayerRange[][] = [];

  private baseLanguage?: string;

  private revisionLanguage?: string;

  private listeners: DiffLayerListener[] = [];

  private processHandle: number | null = null;

  private processPromise: CancelablePromise<unknown> | null = null;

  private hljs?: HighlightJS;

  private readonly libLoader = new GrLibLoader();

  init(diff?: DiffInfo) {
    this.cancel();
    this.baseRanges = [];
    this.revisionRanges = [];
    this.diff = diff;
  }

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
  }

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  /**
   * Annotation layer method to add syntax annotations to the given element
   * for the given line.
   */
  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine) {
    if (!this.enabled) return;
    if (line.beforeNumber === FILE || line.afterNumber === FILE) return;
    if (line.beforeNumber === 'LOST' || line.afterNumber === 'LOST') return;
    // Determine the side.
    let side;
    if (
      line.type === GrDiffLineType.REMOVE ||
      (line.type === GrDiffLineType.BOTH &&
        el.getAttribute('data-side') !== 'right')
    ) {
      side = 'left';
    } else if (
      line.type === GrDiffLineType.ADD ||
      el.getAttribute('data-side') !== 'left'
    ) {
      side = 'right';
    }

    // Find the relevant syntax ranges, if any.
    let ranges: SyntaxLayerRange[] = [];
    if (side === 'left' && this.baseRanges.length >= line.beforeNumber) {
      ranges = this.baseRanges[line.beforeNumber - 1] || [];
    } else if (
      side === 'right' &&
      this.revisionRanges.length >= line.afterNumber
    ) {
      ranges = this.revisionRanges[line.afterNumber - 1] || [];
    }

    // Apply the ranges to the element.
    for (const range of ranges) {
      GrAnnotation.annotateElement(
        el,
        range.start,
        range.length,
        range.className
      );
    }
  }

  _getLanguage(metaInfo: DiffFileMetaInfo) {
    // The Gerrit API provides only content-type, but for other users of
    // gr-diff it may be more convenient to specify the language directly.
    return metaInfo.language ?? LANGUAGE_MAP.get(metaInfo.content_type);
  }

  /**
   * Start processing syntax for the loaded diff and notify layer listeners
   * as syntax info comes online.
   */
  process() {
    // Cancel any still running process() calls, because they append to the
    // same baseRanges and revisionRanges fields.
    this.cancel();

    // Discard existing ranges.
    this.baseRanges = [];
    this.revisionRanges = [];

    if (!this.enabled || !this.diff?.content.length) {
      return Promise.resolve();
    }

    if (this.diff.meta_a) {
      this.baseLanguage = this._getLanguage(this.diff.meta_a);
    }
    if (this.diff.meta_b) {
      this.revisionLanguage = this._getLanguage(this.diff.meta_b);
    }
    if (!this.baseLanguage && !this.revisionLanguage) {
      return Promise.resolve();
    }

    const state: SyntaxLayerState = {
      sectionIndex: 0,
      lineIndex: 0,
      baseContext: undefined,
      revisionContext: undefined,
      lineNums: {left: 1, right: 1},
      lastNotify: {left: 1, right: 1},
    };

    const rangesCache = new Map<string, SyntaxLayerRange[]>();

    this.processPromise = util.makeCancelable(
      this._loadHLJS().then(
        () =>
          new Promise<void>(resolve => {
            const nextStep = () => {
              this.processHandle = null;
              this._processNextLine(state, rangesCache);

              // Move to the next line in the section.
              state.lineIndex++;

              // If the section has been exhausted, move to the next one.
              if (this._isSectionDone(state)) {
                state.lineIndex = 0;
                state.sectionIndex++;
              }

              // If all sections have been exhausted, finish.
              if (
                !this.diff ||
                state.sectionIndex >= this.diff.content.length
              ) {
                resolve();
                this._notify(state);
                return;
              }

              if (state.lineIndex % 100 === 0) {
                this._notify(state);
                this.processHandle = window.setTimeout(nextStep, ASYNC_DELAY);
              } else {
                nextStep.call(this);
              }
            };

            this.processHandle = window.setTimeout(nextStep, 1);
          })
      )
    );
    return this.processPromise.finally(() => {
      this.processPromise = null;
    });
  }

  /**
   * Cancel any asynchronous syntax processing jobs.
   */
  cancel() {
    if (this.processHandle !== null) {
      clearTimeout(this.processHandle);
      this.processHandle = null;
    }
    if (this.processPromise) {
      this.processPromise.cancel();
    }
  }

  /**
   * Take a string of HTML with the (potentially nested) syntax markers
   * Highlight.js emits and emit a list of text ranges and classes for the
   * markers.
   *
   * @param str The string of HTML.
   * @param rangesCache A map for caching
   * ranges for each string. A cache is read and written by this method.
   * Since diff is mostly comparing same file on two sides, there is good rate
   * of duplication at least for parts that are on left and right parts.
   * @return The list of ranges.
   */
  _rangesFromString(
    str: string,
    rangesCache: Map<string, SyntaxLayerRange[]>
  ): SyntaxLayerRange[] {
    const cached = rangesCache.get(str);
    if (cached) return cached;

    const div = document.createElement('div');
    div.innerHTML = str;
    const ranges = this._rangesFromElement(div, 0);
    rangesCache.set(str, ranges);
    return ranges;
  }

  _rangesFromElement(elem: Element, offset: number): SyntaxLayerRange[] {
    let result: SyntaxLayerRange[] = [];
    for (const node of elem.childNodes) {
      const nodeLength = GrAnnotation.getLength(node);
      // Note: HLJS may emit a span with class undefined when it thinks there
      // may be a syntax error.
      if (
        node instanceof Element &&
        node.tagName === 'SPAN' &&
        node.className !== 'undefined'
      ) {
        if (CLASS_SAFELIST.has(node.className)) {
          result.push({
            start: offset,
            length: nodeLength,
            className: node.className,
          });
        }
        if (node.children.length) {
          result = result.concat(this._rangesFromElement(node, offset));
        }
      }
      offset += nodeLength;
    }
    return result;
  }

  /**
   * For a given state, process the syntax for the next line (or pair of
   * lines).
   */
  _processNextLine(
    state: SyntaxLayerState,
    rangesCache: Map<string, SyntaxLayerRange[]>
  ) {
    if (!this.diff) return;
    if (!this.hljs) return;

    let baseLine;
    let revisionLine;
    const section = this.diff.content[state.sectionIndex];
    if (section.ab) {
      baseLine = section.ab[state.lineIndex];
      revisionLine = section.ab[state.lineIndex];
      state.lineNums.left++;
      state.lineNums.right++;
    } else {
      if (section.a && section.a.length > state.lineIndex) {
        baseLine = section.a[state.lineIndex];
        state.lineNums.left++;
      }
      if (section.b && section.b.length > state.lineIndex) {
        revisionLine = section.b[state.lineIndex];
        state.lineNums.right++;
      }
      if (section.skip) {
        state.lineNums.left += section.skip;
        state.lineNums.right += section.skip;
        for (let i = 0; i < section.skip; i++) this.revisionRanges.push([]);
      }
    }

    // To store the result of the syntax highlighter.
    let result;

    if (
      this.baseLanguage &&
      baseLine !== undefined &&
      this.hljs.getLanguage(this.baseLanguage)
    ) {
      baseLine = this._workaround(this.baseLanguage, baseLine);
      result = this.hljs.highlight(
        this.baseLanguage,
        baseLine,
        true,
        state.baseContext
      );
      this.baseRanges.push(this._rangesFromString(result.value, rangesCache));
      state.baseContext = result.top;
    }

    if (
      this.revisionLanguage &&
      revisionLine !== undefined &&
      this.hljs.getLanguage(this.revisionLanguage)
    ) {
      revisionLine = this._workaround(this.revisionLanguage, revisionLine);
      result = this.hljs.highlight(
        this.revisionLanguage,
        revisionLine,
        true,
        state.revisionContext
      );
      this.revisionRanges.push(
        this._rangesFromString(result.value, rangesCache)
      );
      state.revisionContext = result.top;
    }
  }

  /**
   * Ad hoc fixes for HLJS parsing bugs. Rewrite lines of code in constrained
   * cases before sending them into HLJS so that they parse correctly.
   *
   * Important notes:
   * * These tests should be as constrained as possible to avoid interfering
   * with code it shouldn't AND to avoid executing regexes as much as
   * possible.
   * * These tests should document the issue clearly enough that the test can
   * be confidently removed when the issue is solved in HLJS.
   * * These tests should rewrite the line of code to have the same number of
   * characters. This method rewrites the string that gets parsed, but NOT
   * the string that gets displayed and highlighted. Thus, the positions
   * must be consistent.
   *
   * @param language The name of the HLJS language plugin in use.
   * @param line The line of code to potentially rewrite.
   * @return A potentially-rewritten line of code.
   */
  _workaround(language: string, line: string) {
    if (language === 'cpp') {
      /**
       * Prevent confusing < and << operators for the start of a meta string
       * by converting them to a different operator.
       * {@see Issue 4864}
       * {@see https://github.com/isagalaev/highlight.js/issues/1341}
       */
      if (CPP_DIRECTIVE_WITH_LT_PATTERN.test(line)) {
        line = line.replace(GLOBAL_LT_PATTERN, '|');
      }

      /**
       * Rewrite CPP wchar_t characters literals to wchar_t string literals
       * because HLJS only understands the string form.
       * {@see Issue 5242}
       * {#see https://github.com/isagalaev/highlight.js/issues/1412}
       */
      if (CPP_WCHAR_PATTERN.test(line)) {
        line = line.replace(CPP_WCHAR_PATTERN, 'L"$1."');
      }

      return line;
    }

    /**
     * Prevent confusing the closing paren of a parameterized Java annotation
     * being applied to a formal argument as the closing paren of the argument
     * list. Rewrite the parens as spaces.
     * {@see Issue 4776}
     * {@see https://github.com/isagalaev/highlight.js/issues/1324}
     */
    if (language === 'java' && JAVA_PARAM_ANNOT_PATTERN.test(line)) {
      return line.replace(JAVA_PARAM_ANNOT_PATTERN, '$1 $2 ');
    }

    /**
     * HLJS misunderstands backslash character literals in Go.
     * {@see Issue 5007}
     * {#see https://github.com/isagalaev/highlight.js/issues/1411}
     */
    if (language === 'go' && line.includes(GO_BACKSLASH_LITERAL)) {
      return line.replace(GO_BACKSLASH_LITERAL, '"\\\\"');
    }

    return line;
  }

  /**
   * Tells whether the state has exhausted its current section.
   */
  _isSectionDone(state: SyntaxLayerState) {
    if (!this.diff) return true;
    const section = this.diff.content[state.sectionIndex];
    if (section.ab) {
      return state.lineIndex >= section.ab.length;
    } else {
      return (
        (!section.a || state.lineIndex >= section.a.length) &&
        (!section.b || state.lineIndex >= section.b.length)
      );
    }
  }

  /**
   * For a given state, notify layer listeners of any processed line ranges
   * that have not yet been notified.
   */
  _notify(state: SyntaxLayerState) {
    if (state.lineNums.left - state.lastNotify.left) {
      this._notifyRange(state.lastNotify.left, state.lineNums.left, Side.LEFT);
      state.lastNotify.left = state.lineNums.left;
    }
    if (state.lineNums.right - state.lastNotify.right) {
      this._notifyRange(
        state.lastNotify.right,
        state.lineNums.right,
        Side.RIGHT
      );
      state.lastNotify.right = state.lineNums.right;
    }
  }

  _notifyRange(start: number, end: number, side: Side) {
    for (const listener of this.listeners) {
      listener(start, end, side);
    }
  }

  _loadHLJS() {
    return this.libLoader.getLibrary(HLJS_LIBRARY_CONFIG).then(hljs => {
      this.hljs = hljs as HighlightJS;
    });
  }
}
