/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffRangesToFocus, GrDiffLine, Side} from '../../../api/diff';
import {DiffLayer, DiffLayerListener} from '../../../types/types';

// Ranges are considered half-open: [start, end)
export type Range = {
  start: number;
  end: number;
};

export class GrFocusLayer implements DiffLayer {
  private diffRangesToFocus?: DiffRangesToFocus;

  /**
   * Diff Ranges which were unfocussed(colors are saturated) in previous call.
   */
  private previousUnfocussedRanges?: DiffRangesToFocus;

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
    if (!this.previousUnfocussedRanges && !diffRangesToFocus) return;
    this.diffRangesToFocus = diffRangesToFocus;

    // If ranges are set before any diff row was rendered, then great, no need
    // to notify and re-render.
    if (this.annotated) {
      this.notify({
        left: [
          ...(this.previousUnfocussedRanges?.left ?? []),
          ...(diffRangesToFocus?.left ?? []),
        ],
        right: [
          ...(this.previousUnfocussedRanges?.right ?? []),
          ...(diffRangesToFocus?.right ?? []),
        ],
      });
    }
    this.previousUnfocussedRanges = undefined;
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
    let elementLineNumber;
    const dataValue = lineNumberEl.getAttribute('data-value');
    if (dataValue) {
      elementLineNumber = Number(dataValue);
    }
    if (!elementLineNumber || elementLineNumber < 1) return;

    let ranges: Range[] = [];
    if (side === Side.LEFT) {
      ranges = this.diffRangesToFocus.left;
    } else if (side === Side.RIGHT) {
      ranges = this.diffRangesToFocus.right;
    }
    if (
      ranges.find(
        range =>
          elementLineNumber >= range.start && elementLineNumber <= range.end
      ) === undefined
    ) {
      textEl.classList.add('is-out-of-focus-range');
      this.updateUnfocussedRanges(elementLineNumber, side);
    }
  }

  private updateUnfocussedRanges(lineNumber: number, side: Side) {
    if (side === Side.LEFT) {
      this.previousUnfocussedRanges = {
        left: this.addToRange(lineNumber, this.previousUnfocussedRanges?.left),
        right: this.previousUnfocussedRanges?.right ?? [],
      };
    } else if (side === Side.RIGHT) {
      this.previousUnfocussedRanges = {
        left: this.previousUnfocussedRanges?.left ?? [],
        right: this.addToRange(
          lineNumber,
          this.previousUnfocussedRanges?.right
        ),
      };
    }
  }

  private addToRange(lineNumber: number, ranges?: Range[]) {
    const previousRange: Range[] = ranges ?? [];
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
