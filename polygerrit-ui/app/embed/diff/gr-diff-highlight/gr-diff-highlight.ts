/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../gr-selection-action-box/gr-selection-action-box';
import {getLength} from './gr-annotation';
import {normalize} from './gr-range-normalizer';
import {strToClassName} from '../../../utils/dom-util';
import {Side} from '../../../constants/constants';
import {CommentRange} from '../../../types/common';
import {GrSelectionActionBox} from '../gr-selection-action-box/gr-selection-action-box';
import {
  getLineElByChild,
  getLineNumberByChild,
  getSideByLineEl,
  GrDiffCommentThread,
} from '../gr-diff/gr-diff-utils';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {assertIsDefined, queryAndAssert} from '../../../utils/common-util';
import {DiffModel} from '../gr-diff-model/gr-diff-model';

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

/**
 * The methods that we actually want to call on the builder. We don't want a
 * fully blown dependency on GrDiffBuilderElement.
 */
export interface DiffBuilderInterface {
  getContentTdByLineEl(lineEl?: Element): Element | undefined;
  diffModel: DiffModel;
}

/**
 * Handles showing, positioning and interacting with <gr-selection-action-box>.
 *
 * Toggles a css class for highlighting comment ranges when the mouse leaves or
 * enters a comment thread element.
 */
export class GrDiffHighlight {
  selectedRange?: SidedRange;

  private diffBuilder?: DiffBuilderInterface;

  private diffTable?: HTMLElement;

  private selectionChangeTask?: DelayedTask;

  init(diffTable: HTMLElement, diffBuilder: DiffBuilderInterface) {
    this.cleanup();

    this.diffTable = diffTable;
    this.diffBuilder = diffBuilder;

    diffTable.addEventListener(
      'comment-thread-mouseleave',
      this.handleCommentThreadMouseleave
    );
    diffTable.addEventListener(
      'comment-thread-mouseenter',
      this.handleCommentThreadMouseenter
    );
    diffTable.addEventListener(
      'create-comment-requested',
      this.handleRangeCommentRequest
    );
  }

  cleanup() {
    this.selectionChangeTask?.cancel();
    if (this.diffTable) {
      this.diffTable.removeEventListener(
        'comment-thread-mouseleave',
        this.handleCommentThreadMouseleave
      );
      this.diffTable.removeEventListener(
        'comment-thread-mouseenter',
        this.handleCommentThreadMouseenter
      );
      this.diffTable.removeEventListener(
        'create-comment-requested',
        this.handleRangeCommentRequest
      );
    }
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
    // ms, then you will have about 50 handleSelection() calls when doing a
    // simple drag for select.
    this.selectionChangeTask = debounce(
      this.selectionChangeTask,
      () => this.handleSelection(selection, isMouseUp),
      10
    );
  }

  private toggleRangeElHighlight(
    comment: GrDiffCommentThread,
    highlightRange = false
  ) {
    const rootId = comment?.rootId;
    if (!rootId) return;
    if (!this.diffTable) return;
    const highlightClass = highlightRange ? 'range' : 'rangeHoverHighlight';
    const selector = `.${highlightClass}.${strToClassName(rootId)}`;
    const rangeNodes = this.diffTable.querySelectorAll(selector);
    for (const rangeNode of rangeNodes) {
      rangeNode.classList.toggle('rangeHoverHighlight', highlightRange);
    }
  }

  private handleCommentThreadMouseenter = (
    e: CustomEvent<GrDiffCommentThread>
  ) => {
    this.toggleRangeElHighlight(e.detail, /* highlightRange= */ true);
  };

  private handleCommentThreadMouseleave = (
    e: CustomEvent<GrDiffCommentThread>
  ) => {
    this.toggleRangeElHighlight(e.detail, /* highlightRange= */ false);
  };

  /**
   * Get current normalized selection.
   * Merges multiple ranges, accounts for triple click, accounts for
   * syntax highligh, convert native DOM Range objects to Gerrit concepts
   * (line, side, etc).
   */
  private getNormalizedRange(selection: Selection | Range) {
    /* On Safari the ShadowRoot.getSelection() isn't there and the only thing
       we can get is a single Range */
    if (selection instanceof Range) {
      return this.normalizeRange(selection);
    }
    const rangeCount = selection.rangeCount;
    if (rangeCount === 0) {
      return null;
    } else if (rangeCount === 1) {
      return this.normalizeRange(selection.getRangeAt(0));
    } else {
      const startRange = this.normalizeRange(selection.getRangeAt(0));
      const endRange = this.normalizeRange(
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
  private normalizeRange(domRange: Range): NormalizedRange {
    const range = normalize(domRange);
    return this.fixTripleClickSelection(
      {
        start: this.normalizeSelectionSide(
          range.startContainer,
          range.startOffset
        ),
        end: this.normalizeSelectionSide(range.endContainer, range.endOffset),
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
  private fixTripleClickSelection(range: NormalizedRange, domRange: Range) {
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
    const lineLength = (content && this.getLength(content)) || 0;
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
  private normalizeSelectionSide(
    node: Node | null,
    offset: number
  ): NormalizedPosition | null {
    let column;
    if (!this.diffTable) return null;
    if (!this.diffBuilder) return null;
    if (!node || !this.diffTable.contains(node)) return null;
    const lineEl = getLineElByChild(node);
    if (!lineEl) return null;
    const side = getSideByLineEl(lineEl);
    if (!side) return null;
    const line = getLineNumberByChild(lineEl);
    if (typeof line !== 'number') return null;
    const contentTd = this.diffBuilder.getContentTdByLineEl(lineEl);
    if (!contentTd) return null;
    const contentText = contentTd.querySelector('.contentText');
    if (!contentTd.contains(node)) {
      node = contentText;
      column = 0;
    } else {
      const thread = contentTd.querySelector('.comment-thread');
      if (thread?.contains(node)) {
        column = this.getLength(contentText);
        node = contentText;
      } else {
        column = this.convertOffsetToColumn(node, offset);
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
  // visible for testing
  positionActionBox(
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

  private isRangeValid(range: NormalizedRange | null) {
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

  // visible for testing
  handleSelection(selection: Selection | Range, isMouseUp: boolean) {
    /* On Safari, the selection events may return a null range that should
       be ignored */
    if (!selection) return;
    if (!this.diffTable) return;

    const normalizedRange = this.getNormalizedRange(selection);
    if (!this.isRangeValid(normalizedRange)) {
      this.removeActionBox();
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
      if (isMouseUp && this.getLength(content) === 0) {
        this.createRangeComment(start.side, {
          start_line: start.line,
          start_character: 0,
          end_line: start.line,
          end_character: start.column,
        });
      }
      return;
    }

    let actionBox = this.diffTable.querySelector('gr-selection-action-box');
    if (!actionBox) {
      actionBox = document.createElement('gr-selection-action-box');
      this.diffTable.appendChild(actionBox);
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
      this.positionActionBox(actionBox, start.line, domRange);
    } else if (start.node instanceof Text) {
      if (start.column) {
        this.positionActionBox(
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
      this.positionActionBox(actionBox, start.line, start.node.firstChild);
    } else if (start.node instanceof Element || start.node instanceof Text) {
      this.positionActionBox(actionBox, start.line, start.node);
    } else {
      console.warn('Failed to position comment action box.');
      this.removeActionBox();
    }
  }

  private createRangeComment(side: Side, range: CommentRange) {
    assertIsDefined(this.diffBuilder, 'diffBuilder');
    this.diffBuilder?.diffModel.createComment(range.end_line, side, range);
    this.removeActionBox();
  }

  private handleRangeCommentRequest = (e: Event) => {
    e.stopPropagation();
    assertIsDefined(this.selectedRange, 'selectedRange');
    const {side, range} = this.selectedRange;
    this.createRangeComment(side, range);
  };

  // visible for testing
  removeActionBox() {
    this.selectedRange = undefined;
    const actionBox = this.diffTable?.querySelector('gr-selection-action-box');
    if (actionBox) actionBox.remove();
  }

  private convertOffsetToColumn(el: Node, offset: number) {
    if (el instanceof Element && el.classList.contains('content')) {
      return offset;
    }
    while (
      el.previousSibling ||
      !el.parentElement?.classList.contains('content')
    ) {
      if (el.previousSibling) {
        el = el.previousSibling;
        offset += this.getLength(el);
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
  // visible for testing
  getLength(node: Node | null): number {
    if (node === null) return 0;
    if (node instanceof Element && node.classList.contains('content')) {
      return this.getLength(queryAndAssert(node, '.contentText'));
    } else {
      return getLength(node);
    }
  }
}
