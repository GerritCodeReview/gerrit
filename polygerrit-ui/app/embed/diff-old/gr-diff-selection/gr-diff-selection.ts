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
import {Side} from '../../../constants/constants';
import {
  getLineElByChild,
  getSide,
  getSideByLineEl,
  isThreadEl,
} from '../gr-diff/gr-diff-utils';

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

interface LinesCache {
  left: string[] | null;
  right: string[] | null;
}

function getNewCache(): LinesCache {
  return {left: null, right: null};
}

export class GrDiffSelection {
  // visible for testing
  diff?: DiffInfo;

  // visible for testing
  diffTable?: HTMLElement;

  // visible for testing
  linesCache: LinesCache = getNewCache();

  init(diff: DiffInfo, diffTable: HTMLElement) {
    this.cleanup();
    this.diff = diff;
    this.diffTable = diffTable;
    this.diffTable.classList.add(SelectionClass.RIGHT);
    this.diffTable.addEventListener('copy', this.handleCopy);
    this.diffTable.addEventListener('mousedown', this.handleDown);
    this.linesCache = getNewCache();
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
    const sel = this.getSelection();
    if (!sel || sel.rangeCount !== 1) {
      return ''; // No multi-select support yet.
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

    return this.getRangeFromDiff(
      startLineNum,
      range.startOffset,
      endLineNum,
      range.endOffset,
      side
    );
  }

  /**
   * Query the diff object for the selected lines.
   */
  getRangeFromDiff(
    startLineNum: number,
    startOffset: number,
    endLineNum: number | undefined,
    endOffset: number,
    side: Side
  ) {
    const skipChunk = this.diff?.content.find(chunk => chunk.skip);
    if (skipChunk) {
      startLineNum -= skipChunk.skip!;
      if (endLineNum) endLineNum -= skipChunk.skip!;
    }
    const lines = this.getDiffLines(side).slice(startLineNum - 1, endLineNum);
    if (lines.length) {
      lines[lines.length - 1] = lines[lines.length - 1].substring(0, endOffset);
      lines[0] = lines[0].substring(startOffset);
    }
    return lines.join('\n');
  }

  /**
   * Query the diff object for the lines from a particular side.
   *
   * @param side The side that is currently selected.
   * @return An array of strings indexed by line number.
   */
  getDiffLines(side: Side): string[] {
    if (this.linesCache[side]) {
      return this.linesCache[side]!;
    }
    if (!this.diff) return [];
    let lines: string[] = [];
    for (const chunk of this.diff.content) {
      if (chunk.ab) {
        lines = lines.concat(chunk.ab);
      } else if (side === Side.LEFT && chunk.a) {
        lines = lines.concat(chunk.a);
      } else if (side === Side.RIGHT && chunk.b) {
        lines = lines.concat(chunk.b);
      }
    }
    this.linesCache[side] = lines;
    return lines;
  }
}
