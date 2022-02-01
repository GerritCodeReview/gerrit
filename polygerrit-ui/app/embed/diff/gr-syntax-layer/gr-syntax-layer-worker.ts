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
import {SyntaxLayerRange} from '../../../types/worker-api';

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

  private baseRanges: SyntaxLayerRange[][] = [];

  private revisionRanges: SyntaxLayerRange[][] = [];

  private listeners: DiffLayerListener[] = [];

  private readonly highlightService = getAppContext().highlightService;

  init(diff?: DiffInfo) {
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

  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine) {
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
    const rangesPerLine = isLeft ? this.baseRanges : this.revisionRanges;
    const ranges = rangesPerLine[lineNumber - 1] ?? [];

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

  async process() {
    this.baseRanges = [];
    this.revisionRanges = [];
    if (!this.enabled || !this.diff) return;

    const baseLanguage = this._getLanguage(this.diff.meta_a);
    const revisionLanguage = this._getLanguage(this.diff.meta_b);

    let baseContent = '';
    let revisionContent = '';
    for (const dc of this.diff.content) {
      const a = [...(dc.a ?? []), ...(dc.ab ?? [])];
      const b = [...(dc.b ?? []), ...(dc.ab ?? [])];
      for (const line of a) {
        baseContent += line + '\n';
      }
      for (const line of b) {
        revisionContent += line + '\n';
      }
    }

    const basePromise = this.highlight(baseLanguage, baseContent);
    const revisionPromise = this.highlight(revisionLanguage, revisionContent);
    this.baseRanges = await basePromise;
    this.revisionRanges = await revisionPromise;
    this.notify();
  }

  async highlight(language?: string, code?: string) {
    if (!language || !code) return [];
    return this.highlightService.highlight(language, code);
  }

  notify() {
    const baseLines = this.baseRanges?.length;
    const revisionLines = this.revisionRanges?.length;
    for (const listener of this.listeners) {
      if (baseLines > 0) listener(1, baseLines, Side.LEFT);
      if (revisionLines > 0) listener(1, revisionLines, Side.LEFT);
    }
  }
}
