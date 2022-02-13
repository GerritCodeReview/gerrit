/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {FILE, GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {DiffFileMetaInfo, DiffInfo} from '../../../types/diff';
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {Side} from '../../../constants/constants';
import {LANGUAGE_MAP} from './gr-syntax-layer';
import {getAppContext} from '../../../services/app-context';
import {SyntaxLayerLine} from '../../../types/syntax-worker-api';

const CLASS_PREFIX = 'gr-diff gr-syntax gr-syntax-';

const CLASS_SAFELIST = new Set<string>([
  'attr',
  'attribute',
  'built_in',
  'comment',
  'doctag',
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
  'regexp',
  'selector-attr',
  'selector-class',
  'selector-id',
  'selector-pseudo',
  'selector-tag',
  'string',
  'tag',
  'template-tag',
  'template-variable',
  'title',
  'type',
  'variable',
]);

export class GrSyntaxLayerWorker implements DiffLayer {
  diff?: DiffInfo;

  enabled = true;

  private leftRanges: SyntaxLayerLine[] = [];

  private rightRanges: SyntaxLayerLine[] = [];

  private listeners: DiffLayerListener[] = [];

  private readonly highlightService = getAppContext().highlightService;

  init(diff?: DiffInfo) {
    this.leftRanges = [];
    this.rightRanges = [];
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

  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine) {
    console.log(`syntax layer annotate ${line.beforeNumber}`);
    if (!this.enabled) return;
    if (line.beforeNumber === FILE || line.afterNumber === FILE) return;
    if (line.beforeNumber === 'LOST' || line.afterNumber === 'LOST') return;

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
    const rangesPerLine = isLeft ? this.leftRanges : this.rightRanges;
    const ranges = rangesPerLine[lineNumber - 1]?.ranges ?? [];

    console.log(
      `syntax layer annotate ranges ${line.beforeNumber} ${ranges.length}`
    );
    for (const range of ranges) {
      if (!CLASS_SAFELIST.has(range.className)) continue;
      GrAnnotation.annotateElement(
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
   * `init()` must have been called before. There is actually no good reason for
   * init() and process() to be separated. The diff host typically allows the
   * diff builder to render first and only then calls process(), but as soon as
   * the diff is known the highlighting process can be kicked off.
   * TODO(brohlfs): Call process() directly after init().
   *
   * annotate() will only be able to apply highlighting after process() has
   * completed, but that will likely happen later. That is why layer can have
   * listeners. When process() completes, the listeners will be notified, which
   * tells the diff renderer that another call to annotate() is needed.
   */
  async process() {
    this.leftRanges = [];
    this.rightRanges = [];
    if (!this.enabled || !this.diff) return;

    const leftLanguage = this._getLanguage(this.diff.meta_a);
    const rightLanguage = this._getLanguage(this.diff.meta_b);

    let leftContent = '';
    let rightContent = '';
    for (const chunk of this.diff.content) {
      const a = [...(chunk.a ?? []), ...(chunk.ab ?? [])];
      const b = [...(chunk.b ?? []), ...(chunk.ab ?? [])];
      for (const line of a) {
        leftContent += line + '\n';
      }
      for (const line of b) {
        rightContent += line + '\n';
      }
    }

    const leftPromise = this.highlight(leftLanguage, leftContent);
    const rightPromise = this.highlight(rightLanguage, rightContent);
    this.leftRanges = await leftPromise;
    this.rightRanges = await rightPromise;
    console.log(
      `syntax layer processed ${this.leftRanges.length} ${this.rightRanges.length}`
    );
    this.notify();
  }

  async highlight(language?: string, code?: string) {
    if (!language || !code) return [];
    return this.highlightService.highlight(language, code);
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
