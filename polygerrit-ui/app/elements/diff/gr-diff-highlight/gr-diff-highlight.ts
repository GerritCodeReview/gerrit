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
import '../../../styles/shared-styles';
import '../gr-selection-action-box/gr-selection-action-box';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-highlight_html';
import {GrAnnotation} from './gr-annotation';
import {normalize} from './gr-range-normalizer';
import {strToClassName} from '../../../utils/dom-util';
import {customElement, property} from '@polymer/decorators';
import {Side} from '../../../constants/constants';
import {CommentRange} from '../../../types/common';
import {GrSelectionActionBox} from '../gr-selection-action-box/gr-selection-action-box';
import {GrDiffBuilderElement} from '../gr-diff-builder/gr-diff-builder-element';
import {FILE} from '../gr-diff/gr-diff-line';
import {
  getLineElByChild,
  getLineNumberByChild,
  getRange,
  getSide,
  getSideByLineEl,
} from '../gr-diff/gr-diff-utils';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {queryAndAssert} from '../../../utils/common-util';

interface SidedRange {
  side: Side;
  range: CommentRange;
}

interface NormalizedPosition {
  node: Node | null;
  side: Side;
  line: number;
  column: number;
}

interface NormalizedRange {
  start: NormalizedPosition | null;
  end: NormalizedPosition | null;
}

// TODO(TS): Replace by GrCommentThread once that is converted.
interface CommentThreadElement extends HTMLElement {
  rootId: string;
}

@customElement('gr-diff-highlight')
export class GrDiffHighlight extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array, notify: true})
  commentRanges: SidedRange[] = [];

  @property({type: Boolean})
  loggedIn?: boolean;

  @property({type: Object})
  _cachedDiffBuilder?: GrDiffBuilderElement;

  @property({type: Object, notify: true})
  selectedRange?: SidedRange;

  private selectionChangeTask?: DelayedTask;

  constructor() {
    super();
    this.addEventListener('comment-thread-mouseleave', e =>
      this._handleCommentThreadMouseleave(e)
    );
    this.addEventListener('comment-thread-mouseenter', e =>
      this._handleCommentThreadMouseenter(e)
    );
    this.addEventListener('create-comment-requested', e =>
      this._handleRangeCommentRequest(e)
    );
  }

  override disconnectedCallback() {
    this.selectionChangeTask?.cancel();
    super.disconnectedCallback();
  }

  get diffBuilder() {
    if (!this._cachedDiffBuilder) {
      this._cachedDiffBuilder = this.querySelector(
        'gr-diff-builder'
      ) as GrDiffBuilderElement;
    }
    return this._cachedDiffBuilder;
  }

  /**
   * Determines side/line/range for a DOM selection and shows a tooltip.
   *
   * With native shadow DOM, gr-diff-highlight cannot access a selection that
   * references the DOM elements making up the diff because they are in the
   * shadow DOM the gr-diff element. For this reason, we listen to the
   * selectionchange event and retrieve the selection in gr-diff, and then
   * call this method to process the Selection.
   *
   * @param selection A DOM Selection living in the shadow DOM of
   * the diff element.
   * @param isMouseUp If true, this is called due to a mouseup
   * event, in which case we might want to immediately create a comment,
   * because isMouseUp === true combined with an existing selection must
   * mean that this is the end of a double-click.
   */
  handleSelectionChange(
    selection: Selection | Range | null,
    isMouseUp: boolean
  ) {
    if (selection === null) return;
    // Debounce is not just nice for waiting until the selection has settled,
    // it is also vital for being able to click on the action box before it is
    // removed.
    // If you wait longer than 50 ms, then you don't properly catch a very
    // quick 'c' press after the selection change. If you wait less than 10
    // ms, then you will have about 50 _handleSelection calls when doing a
    // simple drag for select.
    this.selectionChangeTask = debounce(
      this.selectionChangeTask,
      () => this._handleSelection(selection, isMouseUp),
      10
    );
  }

  _getThreadEl(e: Event): CommentThreadElement | null {
    const path = (dom(e) as EventApi).path || [];
    for (const pathEl of path) {
      if (
        pathEl instanceof HTMLElement &&
        pathEl.classList.contains('comment-thread')
      ) {
        return pathEl as CommentThreadElement;
      }
    }
    return null;
  }

  _toggleRangeElHighlight(
    threadEl: CommentThreadElement,
    highlightRange = false
  ) {
    // We don't want to re-create the line just for highlighting the range which
    // is creating annoying bugs: @see Issue 12934
    // As gr-ranged-comment-layer now does not notify the layer re-render and
    // lack of access to the thread or the lineEl from the ranged-comment-layer,
    // need to update range class for styles here.
    let curNode: HTMLElement | null = threadEl.assignedSlot;
    while (curNode) {
      if (curNode.nodeName === 'TABLE') break;
      curNode = curNode.parentElement;
    }
    if (curNode?.querySelectorAll) {
      if (highlightRange) {
        const rangeNodes = curNode.querySelectorAll(
          `.range.${strToClassName(threadEl.rootId)}`
        );
        rangeNodes.forEach(rangeNode => {
          rangeNode.classList.add('rangeHoverHighlight');
        });
        const hintNode = threadEl.parentElement?.querySelector(
          `gr-ranged-comment-hint[threadElRootId="${threadEl.rootId}"]`
        );
        if (hintNode) {
          hintNode.shadowRoot
            ?.querySelectorAll('.rangeHighlight')
            .forEach(highlightNode =>
              highlightNode.classList.add('rangeHoverHighlight')
            );
        }
      } else {
        const rangeNodes = curNode.querySelectorAll(
          `.rangeHoverHighlight.${strToClassName(threadEl.rootId)}`
        );
        rangeNodes.forEach(rangeNode => {
          rangeNode.classList.remove('rangeHoverHighlight');
        });
        const hintNode = threadEl.parentElement?.querySelector(
          `gr-ranged-comment-hint[threadElRootId="${threadEl.rootId}"]`
        );
        if (hintNode) {
          hintNode.shadowRoot
            ?.querySelectorAll('.rangeHoverHighlight')
            .forEach(highlightNode =>
              highlightNode.classList.remove('rangeHoverHighlight')
            );
        }
      }
    }
  }

  _handleCommentThreadMouseenter(e: Event) {
    const threadEl = this._getThreadEl(e)!;
    const index = this._indexForThreadEl(threadEl);

    if (index !== undefined) {
      this.set(['commentRanges', index, 'hovering'], true);
    }

    this._toggleRangeElHighlight(threadEl, /* highlightRange= */ true);
  }

  _handleCommentThreadMouseleave(e: Event) {
    const threadEl = this._getThreadEl(e)!;
    const index = this._indexForThreadEl(threadEl);

    if (index !== undefined) {
      this.set(['commentRanges', index, 'hovering'], false);
    }

    this._toggleRangeElHighlight(threadEl, /* highlightRange= */ false);
  }

  _indexForThreadEl(threadEl: HTMLElement) {
    const side = getSide(threadEl);
    const range = getRange(threadEl);
    if (!side || !range) return undefined;
    return this._indexOfCommentRange(side, range);
  }

  _indexOfCommentRange(side: Side, range: CommentRange) {
    function rangesEqual(a: CommentRange, b: CommentRange) {
      if (!a && !b) {
        return true;
      }
      if (!a || !b) {
        return false;
      }
      return (
        a.start_line === b.start_line &&
        a.start_character === b.start_character &&
        a.end_line === b.end_line &&
        a.end_character === b.end_character
      );
    }

    return this.commentRanges.findIndex(
      commentRange =>
        commentRange.side === side && rangesEqual(commentRange.range, range)
    );
  }

  /**
   * Get current normalized selection.
   * Merges multiple ranges, accounts for triple click, accounts for
   * syntax highligh, convert native DOM Range objects to Gerrit concepts
   * (line, side, etc).
   */
  _getNormalizedRange(selection: Selection | Range) {
    /* On Safari the ShadowRoot.getSelection() isn't there and the only thing
       we can get is a single Range */
    if (selection instanceof Range) {
      return this._normalizeRange(selection);
    }
    const rangeCount = selection.rangeCount;
    if (rangeCount === 0) {
      return null;
    } else if (rangeCount === 1) {
      return this._normalizeRange(selection.getRangeAt(0));
    } else {
      const startRange = this._normalizeRange(selection.getRangeAt(0));
      const endRange = this._normalizeRange(
        selection.getRangeAt(rangeCount - 1)
      );
      return {
        start: startRange.start,
        end: endRange.end,
      };
    }
  }

  /**
   * Normalize a specific DOM Range.
   *
   * @return fixed normalized range
   */
  _normalizeRange(domRange: Range): NormalizedRange {
    const range = normalize(domRange);
    return this._fixTripleClickSelection(
      {
        start: this._normalizeSelectionSide(
          range.startContainer,
          range.startOffset
        ),
        end: this._normalizeSelectionSide(range.endContainer, range.endOffset),
      },
      domRange
    );
  }

  /**
   * Adjust triple click selection for the whole line.
   * A triple click always results in:
   * - start.column == end.column == 0
   * - end.line == start.line + 1
   *
   * @param range Normalized range, ie column/line numbers
   * @param domRange DOM Range object
   * @return fixed normalized range
   */
  _fixTripleClickSelection(range: NormalizedRange, domRange: Range) {
    if (!range.start) {
      // Selection outside of current diff.
      return range;
    }
    const start = range.start;
    const end = range.end;
    // Happens when triple click in side-by-side mode with other side empty.
    const endsAtOtherEmptySide =
      !end &&
      domRange.endOffset === 0 &&
      domRange.endContainer instanceof HTMLElement &&
      domRange.endContainer.nodeName === 'TD' &&
      (domRange.endContainer.classList.contains('left') ||
        domRange.endContainer.classList.contains('right'));
    const endsAtBeginningOfNextLine =
      end &&
      start.column === 0 &&
      end.column === 0 &&
      end.line === start.line + 1;
    const content = domRange.cloneContents().querySelector('.contentText');
    const lineLength = (content && this._getLength(content)) || 0;
    if (lineLength && (endsAtBeginningOfNextLine || endsAtOtherEmptySide)) {
      // Move the selection to the end of the previous line.
      range.end = {
        node: start.node,
        column: lineLength,
        side: start.side,
        line: start.line,
      };
    }
    return range;
  }

  /**
   * Convert DOM Range selection to concrete numbers (line, column, side).
   * Moves range end if it's not inside td.content.
   * Returns null if selection end is not valid (outside of diff).
   *
   * @param node td.content child
   * @param offset offset within node
   */
  _normalizeSelectionSide(
    node: Node | null,
    offset: number
  ): NormalizedPosition | null {
    let column;
    if (!node || !this.contains(node)) return null;
    const lineEl = getLineElByChild(node);
    if (!lineEl) return null;
    const side = getSideByLineEl(lineEl);
    if (!side) return null;
    const line = getLineNumberByChild(lineEl);
    if (!line || line === FILE || line === 'LOST') return null;
    const contentTd = this.diffBuilder.getContentTdByLineEl(lineEl);
    if (!contentTd) return null;
    const contentText = contentTd.querySelector('.contentText');
    if (!contentTd.contains(node)) {
      node = contentText;
      column = 0;
    } else {
      const thread = contentTd.querySelector('.comment-thread');
      if (thread?.contains(node)) {
        column = this._getLength(contentText);
        node = contentText;
      } else {
        column = this._convertOffsetToColumn(node, offset);
      }
    }

    return {
      node,
      side,
      line,
      column,
    };
  }

  /**
   * The only line in which add a comment tooltip is cut off is the first
   * line. Even if there is a collapsed section, The first visible line is
   * in the position where the second line would have been, if not for the
   * collapsed section, so don't need to worry about this case for
   * positioning the tooltip.
   */
  _positionActionBox(
    actionBox: GrSelectionActionBox,
    startLine: number,
    range: Text | Element | Range
  ) {
    if (startLine > 1) {
      actionBox.positionBelow = false;
      actionBox.placeAbove(range);
      return;
    }
    actionBox.positionBelow = true;
    actionBox.placeBelow(range);
  }

  _isRangeValid(range: NormalizedRange | null) {
    if (!range || !range.start || !range.start.node || !range.end) {
      return false;
    }
    const start = range.start;
    const end = range.end;
    return !(
      start.side !== end.side ||
      end.line < start.line ||
      (start.line === end.line && start.column === end.column)
    );
  }

  _handleSelection(selection: Selection | Range, isMouseUp: boolean) {
    /* On Safari, the selection events may return a null range that should
       be ignored */
    if (!selection) {
      return;
    }
    const normalizedRange = this._getNormalizedRange(selection);
    if (!this._isRangeValid(normalizedRange)) {
      this._removeActionBox();
      return;
    }
    /* On Safari the ShadowRoot.getSelection() isn't there and the only thing
       we can get is a single Range */
    const domRange =
      selection instanceof Range ? selection : selection.getRangeAt(0);
    const start = normalizedRange!.start!;
    const end = normalizedRange!.end!;

    // TODO (viktard): Drop empty first and last lines from selection.

    // If the selection is from the end of one line to the start of the next
    // line, then this must have been a double-click, or you have started
    // dragging. Showing the action box is bad in the former case and not very
    // useful in the latter, so never do that.
    // If this was a mouse-up event, we create a comment immediately if
    // the selection is from the end of a line to the start of the next line.
    // In a perfect world we would only do this for double-click, but it is
    // extremely rare that a user would drag from the end of one line to the
    // start of the next and release the mouse, so we don't bother.
    // TODO(brohlfs): This does not work, if the double-click is before a new
    // diff chunk (start will be equal to end), and neither before an "expand
    // the diff context" block (end line will match the first line of the new
    // section and thus be greater than start line + 1).
    if (start.line === end.line - 1 && end.column === 0) {
      // Rather than trying to find the line contents (for comparing
      // start.column with the content length), we just check if the selection
      // is empty to see that it's at the end of a line.
      const content = domRange.cloneContents().querySelector('.contentText');
      if (isMouseUp && this._getLength(content) === 0) {
        this._fireCreateRangeComment(start.side, {
          start_line: start.line,
          start_character: 0,
          end_line: start.line,
          end_character: start.column,
        });
      }
      return;
    }

    let actionBox = this.shadowRoot!.querySelector(
      'gr-selection-action-box'
    ) as GrSelectionActionBox | null;
    if (!actionBox) {
      actionBox = document.createElement('gr-selection-action-box');
      this.root!.insertBefore(actionBox, this.root!.firstElementChild);
    }
    this.selectedRange = {
      range: {
        start_line: start.line,
        start_character: start.column,
        end_line: end.line,
        end_character: end.column,
      },
      side: start.side,
    };
    if (start.line === end.line) {
      this._positionActionBox(actionBox, start.line, domRange);
    } else if (start.node instanceof Text) {
      if (start.column) {
        this._positionActionBox(
          actionBox,
          start.line,
          start.node.splitText(start.column)
        );
      }
      start.node.parentElement!.normalize(); // Undo splitText from above.
    } else if (
      start.node instanceof HTMLElement &&
      start.node.classList.contains('content') &&
      (start.node.firstChild instanceof Element ||
        start.node.firstChild instanceof Text)
    ) {
      this._positionActionBox(actionBox, start.line, start.node.firstChild);
    } else if (start.node instanceof Element || start.node instanceof Text) {
      this._positionActionBox(actionBox, start.line, start.node);
    } else {
      console.warn('Failed to position comment action box.');
      this._removeActionBox();
    }
  }

  _fireCreateRangeComment(side: Side, range: CommentRange) {
    this.dispatchEvent(
      new CustomEvent('create-range-comment', {
        detail: {side, range},
        composed: true,
        bubbles: true,
      })
    );
    this._removeActionBox();
  }

  _handleRangeCommentRequest(e: Event) {
    e.stopPropagation();
    if (!this.selectedRange) {
      throw Error('Selected Range is needed for new range comment!');
    }
    const {side, range} = this.selectedRange;
    this._fireCreateRangeComment(side, range);
  }

  _removeActionBox() {
    this.selectedRange = undefined;
    const actionBox = this.shadowRoot!.querySelector('gr-selection-action-box');
    if (actionBox) {
      this.root!.removeChild(actionBox);
    }
  }

  _convertOffsetToColumn(el: Node, offset: number) {
    if (el instanceof Element && el.classList.contains('content')) {
      return offset;
    }
    while (
      el.previousSibling ||
      !el.parentElement?.classList.contains('content')
    ) {
      if (el.previousSibling) {
        el = el.previousSibling;
        offset += this._getLength(el);
      } else {
        el = el.parentElement!;
      }
    }
    return offset;
  }

  /**
   * Get length of a node. If the node is a content node, then only give the
   * length of its .contentText child.
   *
   * @param node this is sometimes passed as null.
   */
  _getLength(node: Node | null): number {
    if (node === null) return 0;
    if (node instanceof Element && node.classList.contains('content')) {
      return this._getLength(queryAndAssert(node, '.contentText'));
    } else {
      return GrAnnotation.getLength(node);
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-highlight': GrDiffHighlight;
  }
}
