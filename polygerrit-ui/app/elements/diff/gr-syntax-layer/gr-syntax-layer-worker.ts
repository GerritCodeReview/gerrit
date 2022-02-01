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

interface SyntaxLayerRange {
  start: number;
  length: number;
  className: string;
}

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
      ranges = this.baseRanges[line.beforeNumber - 1] ?? [];
    }
    if (side === 'right' && this.revisionRanges.length >= line.afterNumber) {
      ranges = this.revisionRanges[line.afterNumber - 1] ?? [];
    }

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

  _getLanguage(metaInfo: DiffFileMetaInfo) {
    // The Gerrit API provides only content-type, but for other users of
    // gr-diff it may be more convenient to specify the language directly.
    return metaInfo.language ?? LANGUAGE_MAP.get(metaInfo.content_type);
  }

  /**
   * Processing syntax for the loaded diff and notify layer listeners as syntax
   * info comes online.
   */
  process() {
    this.baseRanges = [];
    this.revisionRanges = [];
    if (!this.enabled || !this.diff) return Promise.resolve();

    let baseLanguage: string | undefined = undefined;
    if (this.diff.meta_a) {
      baseLanguage = this._getLanguage(this.diff.meta_a);
    }
    let revisionLanguage: string | undefined = undefined;
    if (this.diff.meta_b) {
      revisionLanguage = this._getLanguage(this.diff.meta_b);
    }

    let base = '';
    let revi = '';
    for (const dc of this.diff.content) {
      const a = [...(dc.a ?? []), ...(dc.ab ?? [])];
      const b = [...(dc.b ?? []), ...(dc.ab ?? [])];
      for (const aline of a) {
        base += aline + '\n';
      }
      for (const bline of b) {
        revi += bline + '\n';
      }
    }

    const promises = [];
    if (baseLanguage && base) {
      promises.push(
        this.highlightService.highlight(baseLanguage, base).then(ranges => {
          // console.log(`worker base result ${JSON.stringify(ranges)}`);
          this.baseRanges = ranges;
          this.notifyRange(1, this.baseRanges?.length - 1, Side.LEFT);
        })
      );
    }
    if (revisionLanguage && revi) {
      promises.push(
        this.highlightService.highlight(revisionLanguage, revi).then(ranges => {
          // console.log(`worker revi result ${JSON.stringify(ranges)}`);
          this.revisionRanges = ranges;
          this.notifyRange(1, this.revisionRanges?.length - 1, Side.RIGHT);
        })
      );
    }
    return Promise.all(promises);
  }

  notifyRange(start: number, end: number, side: Side) {
    for (const listener of this.listeners) {
      listener(start, end, side);
    }
  }
}
