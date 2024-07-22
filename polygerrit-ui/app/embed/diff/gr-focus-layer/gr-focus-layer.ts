/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffRangesToFocus, GrDiffLine, Side} from '../../../api/diff';
import {DiffLayer, DiffLayerListener} from '../../../types/types';

// A range of lines in a diff.
export type Range = {
  start: number;
  end: number;
};

export class GrFocusLayer implements DiffLayer {
  private diffRangesToFocus?: DiffRangesToFocus;

  /**
   * Diff Ranges which were unfocused(colors are saturated) in previous call.
   */
  private previousUnfocusedRanges?: DiffRangesToFocus;

  /**
   * Has any line been annotated already in the lifetime of this layer?
   * If not, then `setRanges()` does not have to call `notify()` and thus
   * trigger re-rendering of the affected diff rows.
   */
  // visible for testing
  annotated = false;

  private listeners: DiffLayerListener[] = [];

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  setRanges(diffRangesToFocus?: DiffRangesToFocus) {
    if (!this.previousUnfocusedRanges && !diffRangesToFocus) return;
    this.diffRangesToFocus = diffRangesToFocus;

    // If ranges are set before any diff row was rendered, then great, no need
    // to notify and re-render.
    if (this.annotated) {
      this.notify({
        left: [
          ...(this.previousUnfocusedRanges?.left ?? []),
          ...(diffRangesToFocus?.left ?? []),
        ],
        right: [
          ...(this.previousUnfocusedRanges?.right ?? []),
          ...(diffRangesToFocus?.right ?? []),
        ],
      });
    }
    this.previousUnfocusedRanges = undefined;
  }

  private notify(ranges: DiffRangesToFocus) {
    for (const r of ranges.left) {
      for (const l of this.listeners) l(r.start, r.end, Side.LEFT);
    }
    for (const r of ranges.right) {
      for (const l of this.listeners) l(r.start, r.end, Side.RIGHT);
    }
  }

  /**
   * Layer method to add is-out-of-focus-range to a textElement
   * if line is out of focus.
   *
   * @param textEl The gr-text element for this line.
   * @param lineNumberEl The <td> element with the line number.
   * @param _line Not used for this layer. (unused parameter)
   * @param side The side of the diff.
   */
  annotate(
    textEl: HTMLElement,
    lineNumberEl: HTMLElement,
    _line: GrDiffLine,
    side: Side
  ) {
    if (!lineNumberEl || !textEl || !this.diffRangesToFocus) {
      return;
    }
    let elementLineNumber = -1;
    const dataValue = lineNumberEl.getAttribute('data-value');
    if (dataValue) {
      elementLineNumber = Number(dataValue);
    }
    if (!elementLineNumber || elementLineNumber < 1) return;

    let focusedRanges: Range[] = [];
    if (side === Side.LEFT) {
      focusedRanges = this.diffRangesToFocus.left;
    } else if (side === Side.RIGHT) {
      focusedRanges = this.diffRangesToFocus.right;
    }
    // TODO(anuragpathak): Optimize this using the same approach as gr-coverage-layer.ts
    if (
      !focusedRanges.some(
        range =>
          elementLineNumber >= range.start && elementLineNumber <= range.end
      )
    ) {
      textEl.classList.add('is-out-of-focus-range');
      this.updateUnfocusedRanges(elementLineNumber, side);
    }
  }

  private updateUnfocusedRanges(lineNumber: number, side: Side) {
    this.previousUnfocusedRanges = {
      left:
        side === Side.LEFT
          ? this.addToRange(lineNumber, this.previousUnfocusedRanges?.left)
          : this.previousUnfocusedRanges?.left ?? [],
      right:
        side === Side.RIGHT
          ? this.addToRange(lineNumber, this.previousUnfocusedRanges?.right)
          : this.previousUnfocusedRanges?.right ?? [],
    };
  }

  private addToRange(lineNumber: number, ranges?: Range[]) {
    const previousRange: Range[] = [];
    if (ranges) {
      previousRange.push(...ranges);
    }
    let lastEntryInRange = previousRange.pop();
    if (lastEntryInRange) {
      if (lastEntryInRange.end + 1 === lineNumber) {
        lastEntryInRange = {start: lastEntryInRange.start, end: lineNumber};
        previousRange.push(lastEntryInRange);
      } else {
        previousRange.push(lastEntryInRange, {
          start: lineNumber,
          end: lineNumber,
        });
      }
    } else {
      previousRange.push({
        start: lineNumber,
        end: lineNumber,
      });
    }
    return previousRange;
  }
}
