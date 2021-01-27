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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-ranged-comment-layer_html';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {strToClassName} from '../../../utils/dom-util';
import {customElement, property, observe} from '@polymer/decorators';
import {Side} from '../../../constants/constants';
import {
  PolymerDeepPropertyChange,
  PolymerSpliceChange,
} from '@polymer/polymer/interfaces';
import {CommentRange} from '../../../types/common';
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {isLongCommentRange} from '../gr-diff/gr-diff-utils';

/**
 * Enhanced CommentRange by UI state. Interface for incoming ranges set from the
 * outside.
 *
 * TODO(TS): Unify with what is used in gr-diff when these objects are created.
 */
export interface CommentRangeLayer {
  side: Side;
  range: CommentRange;
  hovering: boolean;
  rootId: string;
}

/**
 * This class breaks down all comment ranges into individual line segment
 * highlights.
 */
interface CommentRangeLineLayer {
  hovering: boolean;
  longRange: boolean;
  rootId: string;
  start: number;
  end: number;
}

type LinesMap = {
  [line in number]: CommentRangeLineLayer[];
};

type RangesMap = {
  [side in Side]: LinesMap;
};

// Polymer 1 adds # before array's key, while Polymer 2 doesn't
const HOVER_PATH_PATTERN = /^(commentRanges\.#?\d+)\.hovering$/;

const RANGE_BASE_ONLY = 'style-scope gr-diff range';
const RANGE_HIGHLIGHT = 'style-scope gr-diff range rangeHighlight';
const HOVER_HIGHLIGHT = 'style-scope gr-diff range rangeHoverHighlight';

@customElement('gr-ranged-comment-layer')
export class GrRangedCommentLayer
  extends GestureEventListeners(LegacyElementMixin(PolymerElement))
  implements DiffLayer {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the range in a range comment was malformed and had to be
   * normalized.
   *
   * It's `detail` has a `lineNum` and `side` parameter.
   *
   * @event normalize-range
   */

  @property({type: Array})
  commentRanges: CommentRangeLayer[] = [];

  @property({type: Array})
  _listeners: DiffLayerListener[] = [];

  @property({type: Object})
  _rangesMap: RangesMap = {left: {}, right: {}};

  @property({type: Boolean})
  ignoreLongRange = false;

  get styleModuleName() {
    return 'gr-ranged-comment-styles';
  }

  /**
   * Layer method to add annotations to a line.
   *
   * @param el The DIV.contentText element to apply the annotation to.
   */
  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine) {
    let ranges: CommentRangeLineLayer[] = [];
    if (
      line.type === GrDiffLineType.REMOVE ||
      (line.type === GrDiffLineType.BOTH &&
        el.getAttribute('data-side') !== 'right')
    ) {
      ranges = ranges.concat(this._getRangesForLine(line, Side.LEFT));
    }
    if (
      line.type === GrDiffLineType.ADD ||
      (line.type === GrDiffLineType.BOTH &&
        el.getAttribute('data-side') !== 'left')
    ) {
      ranges = ranges.concat(this._getRangesForLine(line, Side.RIGHT));
    }

    for (const range of ranges) {
      GrAnnotation.annotateElement(
        el,
        range.start,
        range.end - range.start,
        (range.hovering
          ? HOVER_HIGHLIGHT
          : range.longRange && !this.ignoreLongRange
          ? RANGE_BASE_ONLY
          : RANGE_HIGHLIGHT) + ` ${strToClassName(range.rootId)}`
      );
    }
  }

  /**
   * Register a listener for layer updates.
   */
  addListener(listener: DiffLayerListener) {
    this._listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this._listeners = this._listeners.filter(f => f !== listener);
  }

  /**
   * Notify Layer listeners of changes to annotations.
   */
  _notifyUpdateRange(start: number, end: number, side: Side) {
    for (const listener of this._listeners) {
      listener(start, end, side);
    }
  }

  /**
   * Handle change in the ranges by updating the ranges maps and by
   * emitting appropriate update notifications.
   */
  @observe('commentRanges.*')
  _handleCommentRangesChange(
    record: PolymerDeepPropertyChange<
      CommentRangeLayer[],
      PolymerSpliceChange<CommentRangeLayer[]>
    >
  ) {
    if (!record) return;

    // If the entire set of comments was changed.
    if (record.path === 'commentRanges') {
      const value = record.value as CommentRangeLayer[];
      this._rangesMap = {left: {}, right: {}};
      for (const {side, range, rootId, hovering} of value) {
        const longRange = isLongCommentRange(range);
        this._updateRangesMap({
          side,
          range,
          hovering,
          operation: (forLine, start, end, hovering) => {
            forLine.push({start, end, hovering, rootId, longRange});
          },
        });
      }
    }

    // If the change only changed the `hovering` property of a comment.
    const match = record.path.match(HOVER_PATH_PATTERN);
    if (match) {
      // The #number indicates the key of that item in the array
      // not the index, especially in polymer 1.
      const {side, range, hovering, rootId} = this.get(match[1]);

      this._updateRangesMap({
        side,
        range,
        hovering,
        skipLayerUpdate: true,
        operation: (forLine, start, end, hovering) => {
          const index = forLine.findIndex(
            lineRange => lineRange.start === start && lineRange.end === end
          );
          forLine[index].hovering = hovering;
          forLine[index].rootId = rootId;
        },
      });
    }

    // If comments were spliced in or out.
    if (record.path === 'commentRanges.splices') {
      const value = record.value as PolymerSpliceChange<CommentRangeLayer[]>;
      for (const indexSplice of value.indexSplices) {
        const removed = indexSplice.removed;
        for (const {side, range, hovering, rootId} of removed) {
          this._updateRangesMap({
            side,
            range,
            hovering,
            operation: (forLine, start, end) => {
              const index = forLine.findIndex(
                lineRange =>
                  lineRange.start === start &&
                  lineRange.end === end &&
                  rootId === lineRange.rootId
              );
              forLine.splice(index, 1);
            },
          });
        }
        const added = indexSplice.object.slice(
          indexSplice.index,
          indexSplice.index + indexSplice.addedCount
        );
        for (const {side, range, hovering, rootId} of added) {
          const longRange = isLongCommentRange(range);
          this._updateRangesMap({
            side,
            range,
            hovering,
            operation: (forLine, start, end, hovering) => {
              forLine.push({start, end, hovering, rootId, longRange});
            },
          });
        }
      }
    }
  }

  _updateRangesMap(options: {
    side: Side;
    range: CommentRange;
    hovering: boolean;
    operation: (
      forLine: CommentRangeLineLayer[],
      start: number,
      end: number,
      hovering: boolean
    ) => void;
    skipLayerUpdate?: boolean;
  }) {
    const {side, range, hovering, operation, skipLayerUpdate} = options;
    const forSide = this._rangesMap[side] || (this._rangesMap[side] = {});
    for (let line = range.start_line; line <= range.end_line; line++) {
      const forLine = forSide[line] || (forSide[line] = []);
      const start = line === range.start_line ? range.start_character : 0;
      const end = line === range.end_line ? range.end_character : -1;
      operation(forLine, start, end, hovering);
    }
    if (!skipLayerUpdate) {
      this._notifyUpdateRange(range.start_line, range.end_line, side);
    }
  }

  _getRangesForLine(line: GrDiffLine, side: Side) {
    const lineNum = side === Side.LEFT ? line.beforeNumber : line.afterNumber;
    const ranges: CommentRangeLineLayer[] =
      this.get(['_rangesMap', side, lineNum]) || [];
    return (
      ranges
        .map(range => {
          // Make a copy, so that the normalization below does not mess with
          // our map.
          range = {...range};
          range.end = range.end === -1 ? line.text.length : range.end;

          // Normalize invalid ranges where the start is after the end but the
          // start still makes sense. Set the end to the end of the line.
          // @see Issue 5744
          if (range.start! >= range.end! && range.start! < line.text.length) {
            range.end = line.text.length;
            this.dispatchEvent(
              new CustomEvent('normalize-range', {
                bubbles: true,
                composed: true,
                detail: {lineNum, side},
              })
            );
          }

          return range;
        })
        // Sort the ranges so that hovering highlights are on top.
        .sort((a, b) => (a.hovering && !b.hovering ? 1 : 0))
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-ranged-comment-layer': GrRangedCommentLayer;
  }
}
