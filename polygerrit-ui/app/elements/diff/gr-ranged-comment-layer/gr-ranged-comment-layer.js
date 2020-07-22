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
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-ranged-comment-layer_html.js';
import {GrDiffLine} from '../gr-diff/gr-diff-line.js';
import {strToClassName} from '../../../utils/dom-util.js';

// Polymer 1 adds # before array's key, while Polymer 2 doesn't
const HOVER_PATH_PATTERN = /^(commentRanges\.#?\d+)\.hovering$/;

const RANGE_HIGHLIGHT = 'style-scope gr-diff range';
const HOVER_HIGHLIGHT = 'style-scope gr-diff rangeHighlight';

/** @extends PolymerElement */
class GrRangedCommentLayer extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-ranged-comment-layer'; }
  /**
   * Fired when the range in a range comment was malformed and had to be
   * normalized.
   *
   * It's `detail` has a `lineNum` and `side` parameter.
   *
   * @event normalize-range
   */

  static get properties() {
    return {
    /** @type {!Array<!Gerrit.HoveredRange>} */
      commentRanges: Array,
      _listeners: {
        type: Array,
        value() { return []; },
      },
      _rangesMap: {
        type: Object,
        value() { return {left: {}, right: {}}; },
      },
    };
  }

  static get observers() {
    return [
      '_handleCommentRangesChange(commentRanges.*)',
    ];
  }

  get styleModuleName() {
    return 'gr-ranged-comment-styles';
  }

  /**
   * Layer method to add annotations to a line.
   *
   * @param {!HTMLElement} el The DIV.contentText element to apply the
   *     annotation to.
   * @param {!HTMLElement} lineNumberEl
   * @param {!Object} line The line object. (GrDiffLine)
   */
  annotate(el, lineNumberEl, line) {
    let ranges = [];
    if (line.type === GrDiffLine.Type.REMOVE || (
      line.type === GrDiffLine.Type.BOTH &&
        el.getAttribute('data-side') !== 'right')) {
      ranges = ranges.concat(this._getRangesForLine(line, 'left'));
    }
    if (line.type === GrDiffLine.Type.ADD || (
      line.type === GrDiffLine.Type.BOTH &&
        el.getAttribute('data-side') !== 'left')) {
      ranges = ranges.concat(this._getRangesForLine(line, 'right'));
    }

    for (const range of ranges) {
      GrAnnotation.annotateElement(el, range.start,
          range.end - range.start,
          (range.hovering ? HOVER_HIGHLIGHT : RANGE_HIGHLIGHT) +
          ` ${strToClassName(range.rootId)}`);
    }
  }

  /**
   * Register a listener for layer updates.
   *
   * @param {function(number, number, string)} fn The update handler function.
   *     Should accept as arguments the line numbers for the start and end of
   *     the update and the side as a string.
   */
  addListener(fn) {
    this._listeners.push(fn);
  }

  removeListener(fn) {
    this._listeners = this._listeners.filter(f => f != fn);
  }

  /**
   * Notify Layer listeners of changes to annotations.
   *
   * @param {number} start The line where the update starts.
   * @param {number} end The line where the update ends.
   * @param {string} side The side of the update. ('left' or 'right')
   */
  _notifyUpdateRange(start, end, side) {
    for (const listener of this._listeners) {
      listener(start, end, side);
    }
  }

  /**
   * Handle change in the ranges by updating the ranges maps and by
   * emitting appropriate update notifications.
   *
   * @param {Object} record The change record.
   */
  _handleCommentRangesChange(record) {
    if (!record) return;

    // If the entire set of comments was changed.
    if (record.path === 'commentRanges') {
      this._rangesMap = {left: {}, right: {}};
      for (const {side, range, rootId, hovering} of record.value) {
        this._updateRangesMap({
          side, range, hovering,
          operation: (forLine, start, end, hovering) => {
            forLine.push({start, end, hovering, rootId});
          }});
      }
    }

    // If the change only changed the `hovering` property of a comment.
    const match = record.path.match(HOVER_PATH_PATTERN);
    if (match) {
      // The #number indicates the key of that item in the array
      // not the index, especially in polymer 1.
      const {side, range, hovering, rootId} = this.get(match[1]);

      this._updateRangesMap({
        side, range, hovering, skipLayerUpdate: true,
        operation: (forLine, start, end, hovering) => {
          const index = forLine.findIndex(lineRange =>
            lineRange.start === start && lineRange.end === end);
          forLine[index].hovering = hovering;
          forLine[index].rootId = rootId;
        }});
    }

    // If comments were spliced in or out.
    if (record.path === 'commentRanges.splices') {
      for (const indexSplice of record.value.indexSplices) {
        const removed = indexSplice.removed;
        for (const {side, range, hovering, rootId} of removed) {
          this._updateRangesMap({
            side, range, hovering, operation: (forLine, start, end) => {
              const index = forLine.findIndex(lineRange =>
                lineRange.start === start && lineRange.end === end &&
                rootId === lineRange.rootId);
              forLine.splice(index, 1);
            }});
        }
        const added = indexSplice.object.slice(
            indexSplice.index, indexSplice.index + indexSplice.addedCount);
        for (const {side, range, hovering, rootId} of added) {
          this._updateRangesMap({
            side, range, hovering,
            operation: (forLine, start, end, hovering) => {
              forLine.push({start, end, hovering, rootId});
            }});
        }
      }
    }
  }

  /**
   * @param {!Object} options
   * @property {!string} options.side
   * @property {boolean} options.hovering
   * @property {boolean} options.skipLayerUpdate
   * @property {!Function} options.operation
   * @property {!{
   *  start_character: number,
   *  start_line: number,
   *  end_line: number,
   *  end_character: number}} options.range
   */
  _updateRangesMap(options) {
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

  _getRangesForLine(line, side) {
    const lineNum = side === 'left' ? line.beforeNumber : line.afterNumber;
    const ranges = this.get(['_rangesMap', side, lineNum]) || [];
    return ranges
        .map(range => {
          // Make a copy, so that the normalization below does not mess with
          // our map.
          range = {...range};
          range.end = range.end === -1 ? line.text.length : range.end;

          // Normalize invalid ranges where the start is after the end but the
          // start still makes sense. Set the end to the end of the line.
          // @see Issue 5744
          if (range.start >= range.end && range.start < line.text.length) {
            range.end = line.text.length;
            this.dispatchEvent(new CustomEvent('normalize-range', {
              bubbles: true,
              composed: true,
              detail: {lineNum, side},
            }));
          }

          return range;
        })
        // Sort the ranges so that hovering highlights are on top.
        .sort((a, b) => (a.hovering && !b.hovering ? 1 : 0));
  }
}

customElements.define(GrRangedCommentLayer.is, GrRangedCommentLayer);
