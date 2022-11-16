/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Side} from '../../../api/diff';
import {
  CoverageRange,
  CoverageType,
  DiffLayer,
  DiffLayerListener,
} from '../../../types/types';
import {createArrayFromTo, unique} from '../../../utils/common-util';

const TOOLTIP_MAP = new Map([
  [CoverageType.COVERED, 'Covered by tests.'],
  [CoverageType.NOT_COVERED, 'Not covered by tests.'],
  [CoverageType.PARTIALLY_COVERED, 'Partially covered by tests.'],
  [CoverageType.NOT_INSTRUMENTED, 'Not instrumented by any tests.'],
]);

export function rangesToLines(covRanges: CoverageRange[]) {
  return covRanges
    .flatMap(r =>
      createArrayFromTo(r.code_range.start_line, r.code_range.end_line)
    )
    .sort()
    .filter(unique);
}

type Range = {from: number; to: number};

export function linesToRanges(lines: number[]): Range[] {
  const ranges: Range[] = [];
  let from: number | undefined = undefined;
  let to: number | undefined = undefined;
  for (const line of lines) {
    if (to !== undefined && line === to + 1) {
      to = line;
      continue;
    }
    if (from !== undefined && to !== undefined) ranges.push({from, to});
    from = line;
    to = line;
  }
  if (from !== undefined && to !== undefined) ranges.push({from, to});
  return ranges;
}

export class GrCoverageLayer implements DiffLayer {
  /**
   * Must be sorted by code_range.start_line.
   * Must only contain ranges that match the side.
   */
  private coverageRanges: CoverageRange[] = [];

  /**
   * We keep track of the line number from the previous annotate() call,
   * and also of the index of the coverage range that had matched.
   * annotate() calls are coming in with increasing line numbers and
   * coverage ranges are sorted by line number. So this is a very simple
   * and efficient way for finding the coverage range that matches a given
   * line number.
   */
  private lastLineNumber = 0;

  /**
   * See `lastLineNumber` comment.
   */
  private index = 0;

  /**
   * Has any line been annotated already in the lifetime of this layer?
   * If not, then `setRanges()` does not have to call `notify()` and thus
   * trigger re-rendering of the affected diff rows.
   */
  // visible for testing
  annotated = false;

  private listeners: DiffLayerListener[] = [];

  constructor(private readonly side: Side) {}

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  /**
   * Must be sorted by code_range.start_line.
   * Must only contain ranges that match the side.
   */
  setRanges(ranges: CoverageRange[]) {
    const oldRanges = this.coverageRanges;
    if (oldRanges.length === 0 && ranges.length === 0) return;
    this.coverageRanges = ranges;

    // If ranges are set before any diff row was rendered, then great, no need
    // to notify and re-render.
    if (this.annotated) this.notify([...oldRanges, ...ranges]);
  }

  /**
   * Notify listeners (should be just gr-diff triggering a re-render).
   *
   * We are optimizing the notification calls by converting the coverange ranges
   * to just an array of sorted unique lines, and then creating {from, to} pairs
   * from them.
   */
  private notify(ranges: CoverageRange[]) {
    const lines = rangesToLines(ranges);
    const notifyRanges = linesToRanges(lines);
    for (const r of notifyRanges) {
      for (const l of this.listeners) l(r.from, r.to, this.side);
    }
  }

  /**
   * Layer method to add annotations to a line.
   *
   * @param _el Not used for this layer. (unused parameter)
   * @param lineNumberEl The <td> element with the line number.
   * @param line Not used for this layer.
   */
  annotate(_el: HTMLElement, lineNumberEl: HTMLElement) {
    if (
      !this.side ||
      !lineNumberEl ||
      !lineNumberEl.classList.contains(this.side)
    ) {
      return;
    }
    let elementLineNumber;
    const dataValue = lineNumberEl.getAttribute('data-value');
    if (dataValue) {
      elementLineNumber = Number(dataValue);
    }
    if (!elementLineNumber || elementLineNumber < 1) return;

    // If the line number is smaller than before, then we have to reset our
    // algorithm and start searching the coverage ranges from the beginning.
    // That happens for example when you expand diff sections.
    if (elementLineNumber < this.lastLineNumber) {
      this.index = 0;
    }
    this.lastLineNumber = elementLineNumber;
    this.annotated = true;

    // We simply loop through all the coverage ranges until we find one that
    // matches the line number.
    while (this.index < this.coverageRanges.length) {
      const coverageRange = this.coverageRanges[this.index];

      // If the line number has moved past the current coverage range, then
      // try the next coverage range.
      if (this.lastLineNumber > coverageRange.code_range.end_line) {
        this.index++;
        continue;
      }

      // If the line number has not reached the next coverage range (and the
      // range before also did not match), then this line has not been
      // instrumented. Nothing to do for this line.
      if (this.lastLineNumber < coverageRange.code_range.start_line) {
        return;
      }

      // The line number is within the current coverage range. Style it!
      lineNumberEl.classList.add(coverageRange.type);
      lineNumberEl.title = TOOLTIP_MAP.get(coverageRange.type) || '';
      return;
    }
  }
}
