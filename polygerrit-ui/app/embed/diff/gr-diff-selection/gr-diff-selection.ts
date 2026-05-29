/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import {normalize} from '../gr-diff-highlight/gr-range-normalizer';
import {
  descendedFromClass,
  parentWithClass,
  querySelectorAll,
} from '../../../utils/dom-util';
import {DiffInfo} from '../../../types/diff';
import {Side, TextRange} from '../../../constants/constants';
import {
  getLineElByChild,
  getSide,
  getSideByLineEl,
  isThreadEl,
} from '../gr-diff/gr-diff-utils';
import {getContentFromDiff, getDiffLines} from '../../../utils/diff-util';
import {fire} from '../../../utils/event-util';

/**
 * Possible CSS classes indicating the state of selection. Dynamically added/
 * removed based on where the user clicks within the diff.
 */
const SelectionClass = {
  COMMENT: 'selected-comment',
  LEFT: 'selected-left',
  RIGHT: 'selected-right',
  BLAME: 'selected-blame',
};

function selectionClassForSide(side?: Side) {
  return side === Side.LEFT ? SelectionClass.LEFT : SelectionClass.RIGHT;
}

export class GrDiffSelection {
  // visible for testing
  diff?: DiffInfo;

  // visible for testing
  diffTable?: HTMLElement;

  init(diff: DiffInfo, diffTable: HTMLElement) {
    this.cleanup();
    this.diff = diff;
    this.diffTable = diffTable;
    this.diffTable.classList.add(SelectionClass.RIGHT);
    this.diffTable.addEventListener('copy', this.handleCopy);
    this.diffTable.addEventListener('mousedown', this.handleDown);
  }

  cleanup() {
    if (!this.diffTable) return;
    this.diffTable.removeEventListener('copy', this.handleCopy);
    this.diffTable.removeEventListener('mousedown', this.handleDown);
  }

  handleDown = (e: Event) => {
    const target = e.target;
    if (!(target instanceof Element)) return;

    const commentEl = parentWithClass(target, 'comment-thread', this.diffTable);
    if (commentEl && isThreadEl(commentEl)) {
      this.setClasses([
        SelectionClass.COMMENT,
        selectionClassForSide(getSide(commentEl)),
      ]);
      return;
    }

    const blameSelected = descendedFromClass(target, 'blame', this.diffTable);
    if (blameSelected) {
      this.setClasses([SelectionClass.BLAME]);
      return;
    }

    // This works for both, the content and the line number cells.
    const lineEl = getLineElByChild(target);
    if (lineEl) {
      this.setClasses([selectionClassForSide(getSideByLineEl(lineEl))]);
      return;
    }
  };

  /**
   * Set the provided list of classes on the element, to the exclusion of all
   * other SelectionClass values.
   */
  setClasses(targetClasses: string[]) {
    if (!this.diffTable) return;
    // Remove any selection classes that do not belong.
    for (const className of Object.values(SelectionClass)) {
      if (!targetClasses.includes(className)) {
        this.diffTable.classList.remove(className);
      }
    }
    // Add new selection classes iff they are not already present.
    for (const targetClass of targetClasses) {
      if (!this.diffTable.classList.contains(targetClass)) {
        this.diffTable.classList.add(targetClass);
      }
    }
  }

  handleCopy = (e: ClipboardEvent) => {
    const target = e.composedPath()[0];
    if (!(target instanceof Element)) return;
    if (target instanceof HTMLTextAreaElement) return;
    if (!descendedFromClass(target, 'diff-row', this.diffTable)) return;
    if (!this.diffTable) return;
    if (this.diffTable.classList.contains(SelectionClass.COMMENT)) return;

    const lineEl = getLineElByChild(target);
    if (!lineEl) return;
    const side = getSideByLineEl(lineEl);
    const text = this.getSelectedText(side);
    if (text && e.clipboardData) {
      e.clipboardData.setData('Text', text);
      e.preventDefault();
      const selectionInfo = this.getSelectionInfo(side);
      if (selectionInfo) {
        fire(this.diffTable, 'copy-info', {
          side,
          range: selectionInfo,
          length: text.length,
        });
      }
    }
  };

  getSelection() {
    const diffHosts = querySelectorAll(document.body, 'gr-diff');
    if (!diffHosts.length) return document.getSelection();

    const curDiffHost = diffHosts.find(diffHost => {
      if (!diffHost?.shadowRoot?.getSelection) return false;
      const selection = diffHost.shadowRoot.getSelection();
      // Pick the one with valid selection:
      // https://developer.mozilla.org/en-US/docs/Web/API/Selection/type
      return selection && selection.type !== 'None';
    });

    return curDiffHost?.shadowRoot?.getSelection
      ? curDiffHost.shadowRoot.getSelection()
      : document.getSelection();
  }

  /**
   * Get the text of the current selection. If commentSelected is
   * true, it returns only the text of comments within the selection.
   * Otherwise it returns the text of the selected diff region.
   *
   * @param side The side that is selected.
   * @param commentSelected Whether or not a comment is selected.
   * @return The selected text.
   */
  getSelectedText(side: Side) {
    if (!this.diff) return '';
    const selectionInfo = this.getSelectionInfo(side);
    if (!selectionInfo) return '';

    return getContentFromDiff(
      this.diff,
      selectionInfo.start_line,
      selectionInfo.start_column,
      selectionInfo.end_line,
      selectionInfo.end_column,
      side
    );
  }

  private getSelectionInfo(side: Side): TextRange | undefined {
    if (!this.diff) return undefined;
    const sel = this.getSelection();
    if (!sel || sel.rangeCount !== 1) {
      return undefined; // No multi-select support yet.
    }
    const range = normalize(sel.getRangeAt(0));
    const startLineEl = getLineElByChild(range.startContainer);
    if (!startLineEl) return;
    const endLineEl = getLineElByChild(range.endContainer);
    // Happens when triple click in side-by-side mode with other side empty.
    const endsAtOtherEmptySide =
      !endLineEl &&
      range.endOffset === 0 &&
      range.endContainer.nodeName === 'TD' &&
      range.endContainer instanceof HTMLTableCellElement &&
      (range.endContainer.classList.contains('left') ||
        range.endContainer.classList.contains('right'));
    const startLineDataValue = startLineEl.getAttribute('data-value');
    if (!startLineDataValue) return;
    const startLineNum = Number(startLineDataValue);
    let endLineNum;
    if (endsAtOtherEmptySide) {
      endLineNum = startLineNum + 1;
    } else if (endLineEl) {
      const endLineDataValue = endLineEl.getAttribute('data-value');
      if (endLineDataValue) endLineNum = Number(endLineDataValue);
    }
    // If endLineNum is still undefined, it means that the selection ends at the
    // end of the file.
    if (endLineNum === undefined) {
      endLineNum = getDiffLines(this.diff, side).length;
    }
    return {
      start_line: startLineNum,
      end_line: endLineNum,
      start_column: range.startOffset,
      end_column: range.endOffset,
    };
  }
}
