/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {annotateElement} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {DiffFileMetaInfo, DiffInfo} from '../../../types/diff';
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {Side} from '../../../constants/constants';
import {SyntaxLayerLine} from '../../../types/syntax-worker-api';
import {CancelablePromise, makeCancelable} from '../../../utils/async-util';
import {HighlightService} from '../../../services/highlight/highlight-service';
import {Provider} from '../../../models/dependency';
import {GrDiffLineType} from '../../../api/diff';
import {assert} from '../../../utils/common-util';

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
  ['text/x-gfm', 'markdown'],
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
  ['text/x-markdown', 'markdown'],
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

const CLASS_PREFIX = 'gr-diff gr-syntax gr-syntax-';

const CLASS_SAFELIST = new Set<string>([
  'attr',
  'attribute',
  'built_in',
  'bullet',
  'code',
  'comment',
  'doctag',
  'emphasis',
  'formula',
  'function',
  'keyword',
  'link',
  'literal',
  'meta',
  'meta-keyword',
  'name',
  'number',
  'params',
  'property',
  'quote',
  'regexp',
  'section',
  'selector-attr',
  'selector-class',
  'selector-id',
  'selector-pseudo',
  'selector-tag',
  'string',
  'strong',
  'tag',
  'template-tag',
  'template-variable',
  'title',
  'title function_',
  'type',
  'variable',
  'variable language_',
]);

export class GrSyntaxLayerWorker implements DiffLayer {
  diff?: DiffInfo;

  enabled = true;

  // private, but visible for testing
  leftRanges: SyntaxLayerLine[] = [];

  // private, but visible for testing
  rightRanges: SyntaxLayerLine[] = [];

  /**
   * We are keeping a reference around to the async computation, such that we
   * can cancel it, if needed.
   */
  private leftPromise?: CancelablePromise<SyntaxLayerLine[]>;

  /**
   * We are keeping a reference around to the async computation, such that we
   * can cancel it, if needed.
   */
  private rightPromise?: CancelablePromise<SyntaxLayerLine[]>;

  private listeners: DiffLayerListener[] = [];

  constructor(
    private readonly getHighlightService: Provider<HighlightService>
  ) {}

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
  }

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine) {
    if (!this.enabled) return;
    if (typeof line.beforeNumber !== 'number') return;
    if (typeof line.afterNumber !== 'number') return;

    let side: Side | undefined;
    if (
      line.type === GrDiffLineType.REMOVE ||
      (line.type === GrDiffLineType.BOTH &&
        el.getAttribute('data-side') !== Side.RIGHT)
    ) {
      side = Side.LEFT;
    } else if (
      line.type === GrDiffLineType.ADD ||
      el.getAttribute('data-side') !== Side.LEFT
    ) {
      side = Side.RIGHT;
    }
    if (!side) return;

    const isLeft = side === Side.LEFT;
    const lineNumber = isLeft ? line.beforeNumber : line.afterNumber;
    assert(typeof lineNumber === 'number', 'lineNumber must be a number');
    const rangesPerLine = isLeft ? this.leftRanges : this.rightRanges;
    const ranges = rangesPerLine[lineNumber - 1]?.ranges ?? [];

    for (const range of ranges) {
      if (!CLASS_SAFELIST.has(range.className)) continue;
      if (range.length === 0) continue;
      annotateElement(
        el,
        range.start,
        range.length,
        CLASS_PREFIX + range.className
      );
    }
  }

  _getLanguage(metaInfo?: DiffFileMetaInfo) {
    if (!metaInfo) return undefined;
    // The Gerrit API provides only content-type, but for other users of
    // gr-diff it may be more convenient to specify the language directly.
    return metaInfo.language ?? LANGUAGE_MAP.get(metaInfo.content_type);
  }

  /**
   * Computes SyntaxLayerLines asynchronously, which can then later be applied,
   * when the annotate() method of the layer API is called.
   *
   * For larger files this is an expensive operation, but is offloaded to a web
   * worker. We are using the HighlightJS lib for doing the actual highlighting.
   *
   * annotate() will only be able to apply highlighting after process() has
   * completed, but that will likely happen later. That is why layer can have
   * listeners. When process() completes, the listeners will be notified, which
   * tells the diff renderer that another call to annotate() is needed.
   */
  async process(diff: DiffInfo) {
    this.diff = diff;
    this.leftRanges = [];
    this.rightRanges = [];
    if (this.leftPromise) this.leftPromise.cancel();
    if (this.rightPromise) this.rightPromise.cancel();
    this.leftPromise = undefined;
    this.rightPromise = undefined;
    if (!this.enabled || !this.diff) return;

    const leftLanguage = this._getLanguage(this.diff.meta_a);
    const rightLanguage = this._getLanguage(this.diff.meta_b);

    let leftContent = '';
    let rightContent = '';
    for (const chunk of this.diff.content) {
      const a = [...(chunk.a ?? []), ...(chunk.ab ?? [])];
      for (const line of a) {
        leftContent += line + '\n';
      }
      const b = [...(chunk.b ?? []), ...(chunk.ab ?? [])];
      for (const line of b) {
        rightContent += line + '\n';
      }
      const skip = chunk.skip ?? 0;
      if (skip > 0) {
        leftContent += '\n'.repeat(skip);
        rightContent += '\n'.repeat(skip);
      }
    }
    leftContent = leftContent.trimEnd();
    rightContent = rightContent.trimEnd();

    try {
      this.leftPromise = this.highlight(leftLanguage, leftContent);
      this.rightPromise = this.highlight(rightLanguage, rightContent);
      this.leftRanges = await this.leftPromise;
      this.rightRanges = await this.rightPromise;
      this.notify();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } catch (err: any) {
      // One source of "error" can promise cancelation.
      this.leftRanges = [];
      this.rightRanges = [];
    }
  }

  highlight(
    language?: string,
    code?: string
  ): CancelablePromise<SyntaxLayerLine[]> {
    const hlPromise = this.getHighlightService().highlight(language, code);
    return makeCancelable(hlPromise);
  }

  notify() {
    // We don't want to notify for lines that don't have any SyntaxLayerRange.
    // So for both sides we are looking for the first and the last occurrence
    // of a line with at least one SyntaxLayerRange.
    const leftRangesReversed = [...this.leftRanges].reverse();
    const leftStart = this.leftRanges.findIndex(line => line.ranges.length > 0);
    const leftEnd =
      this.leftRanges.length -
      1 -
      leftRangesReversed.findIndex(line => line.ranges.length > 0);

    const rightRangesReversed = [...this.rightRanges].reverse();
    const rightStart = this.rightRanges.findIndex(
      line => line.ranges.length > 0
    );
    const rightEnd =
      this.rightRanges.length -
      1 -
      rightRangesReversed.findIndex(line => line.ranges.length > 0);

    for (const listener of this.listeners) {
      if (leftStart > -1) listener(leftStart + 1, leftEnd + 1, Side.LEFT);
      if (rightStart > -1) listener(rightStart + 1, rightEnd + 1, Side.RIGHT);
    }
  }
}
