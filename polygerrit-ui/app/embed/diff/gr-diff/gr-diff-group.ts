/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BLANK_LINE, GrDiffLine, GrDiffLineType} from './gr-diff-line';
import {LineRange, Side} from '../../../api/diff';
import {LineNumber} from './gr-diff-line';
import {assertIsDefined, assert} from '../../../utils/common-util';
import {untilRendered} from '../../../utils/dom-util';

export enum GrDiffGroupType {
  /** Unchanged context. */
  BOTH = 'both',

  /** A widget used to show more context. */
  CONTEXT_CONTROL = 'contextControl',

  /** Added, removed or modified chunk. */
  DELTA = 'delta',
}

export interface GrDiffLinePair {
  left: GrDiffLine;
  right: GrDiffLine;
}

/**
 * Hides lines in the given range behind a context control group.
 *
 * Groups that would be partially visible are split into their visible and
 * hidden parts, respectively.
 * The groups need to be "common groups", meaning they have to have either
 * originated from an `ab` chunk, or from an `a`+`b` chunk with
 * `common: true`.
 *
 * If the hidden range is 3 lines or less, nothing is hidden and no context
 * control group is created.
 *
 * @param groups Common groups, ordered by their line ranges.
 * @param hiddenStart The first element to be hidden, as a
 *     non-negative line number offset relative to the first group's start
 *     line, left and right respectively.
 * @param hiddenEnd The first visible element after the hidden range,
 *     as a non-negative line number offset relative to the first group's
 *     start line, left and right respectively.
 */
export function hideInContextControl(
  groups: readonly GrDiffGroup[],
  hiddenStart: number,
  hiddenEnd: number
): GrDiffGroup[] {
  if (groups.length === 0) return [];
  // Clamp hiddenStart and hiddenEnd - inspired by e.g. substring
  hiddenStart = Math.max(hiddenStart, 0);
  hiddenEnd = Math.max(hiddenEnd, hiddenStart);

  let before: GrDiffGroup[] = [];
  let hidden = groups;
  let after: readonly GrDiffGroup[] = [];

  const numHidden = hiddenEnd - hiddenStart;

  // Showing a context control row for less than 4 lines does not make much,
  // because then that row would consume as much space as the collapsed code.
  if (numHidden > 3) {
    if (hiddenStart) {
      [before, hidden] = splitCommonGroups(hidden, hiddenStart);
    }
    if (hiddenEnd) {
      let beforeLength = 0;
      if (before.length > 0) {
        const beforeStart = before[0].lineRange.left.start_line;
        const beforeEnd = before[before.length - 1].lineRange.left.end_line;
        beforeLength = beforeEnd - beforeStart + 1;
      }
      [hidden, after] = splitCommonGroups(hidden, hiddenEnd - beforeLength);
    }
  } else {
    [hidden, after] = [[], hidden];
  }

  const result = [...before];
  if (hidden.length) {
    result.push(
      new GrDiffGroup({
        type: GrDiffGroupType.CONTEXT_CONTROL,
        contextGroups: [...hidden],
      })
    );
  }
  result.push(...after);
  return result;
}

/**
 * Splits a group in two, defined by leftSplit and rightSplit. Primarily to be
 * used in function splitCommonGroups
 * Groups with some lines before and some lines after the split will be split
 * into two groups, which will be put into the first and second list.
 *
 * @param group The group to be split in two
 * @param leftSplit The line number relative to the split on the left side
 * @param rightSplit The line number relative to the split on the right side
 * @return two new groups, one before the split and another after it
 */
function splitGroupInTwo(
  group: GrDiffGroup,
  leftSplit: number,
  rightSplit: number
) {
  let beforeSplit: GrDiffGroup | undefined;
  let afterSplit: GrDiffGroup | undefined;
  // split line is in the middle of a group, we need to break the group
  // in lines before and after the split.
  if (group.skip) {
    // Currently we assume skip chunks "refuse" to be split. Expanding this
    // group will in the future mean load more data - and therefore we want to
    // fire an event when user wants to do it.
    const closerToStartThanEnd =
      leftSplit - group.lineRange.left.start_line <
      group.lineRange.right.end_line - leftSplit;
    if (closerToStartThanEnd) {
      afterSplit = group;
    } else {
      beforeSplit = group;
    }
  } else {
    const before = [];
    const after = [];
    for (const line of group.lines) {
      if (
        (line.beforeNumber && line.beforeNumber < leftSplit) ||
        (line.afterNumber && line.afterNumber < rightSplit)
      ) {
        before.push(line);
      } else {
        after.push(line);
      }
    }
    if (before.length) {
      beforeSplit =
        before.length === group.lines.length
          ? group
          : group.cloneWithLines(before);
    }
    if (after.length) {
      afterSplit =
        after.length === group.lines.length
          ? group
          : group.cloneWithLines(after);
    }
  }
  return {beforeSplit, afterSplit};
}

/**
 * Splits a list of common groups into two lists of groups.
 *
 * Groups where all lines are before or all lines are after the split will be
 * retained as is and put into the first or second list respectively. Groups
 * with some lines before and some lines after the split will be split into
 * two groups, which will be put into the first and second list.
 *
 * @param split A line number offset relative to the first group's
 *     start line at which the groups should be split.
 * @return The outer array has 2 elements, the
 *   list of groups before and the list of groups after the split.
 */
function splitCommonGroups(
  groups: readonly GrDiffGroup[],
  split: number
): GrDiffGroup[][] {
  if (groups.length === 0) return [[], []];
  const leftSplit = groups[0].lineRange.left.start_line + split;
  const rightSplit = groups[0].lineRange.right.start_line + split;

  const beforeGroups = [];
  const afterGroups = [];
  for (const group of groups) {
    const isCompletelyBefore =
      group.lineRange.left.end_line < leftSplit ||
      group.lineRange.right.end_line < rightSplit;
    const isCompletelyAfter =
      leftSplit <= group.lineRange.left.start_line ||
      rightSplit <= group.lineRange.right.start_line;
    if (isCompletelyBefore) {
      beforeGroups.push(group);
    } else if (isCompletelyAfter) {
      afterGroups.push(group);
    } else {
      const {beforeSplit, afterSplit} = splitGroupInTwo(
        group,
        leftSplit,
        rightSplit
      );
      if (beforeSplit) {
        beforeGroups.push(beforeSplit);
      }
      if (afterSplit) {
        afterGroups.push(afterSplit);
      }
    }
  }
  return [beforeGroups, afterGroups];
}

export interface GrMoveDetails {
  changed: boolean;
  range?: {
    start: number;
    end: number;
  };
}

/** A chunk of the diff that should be rendered together. */
export class GrDiffGroup {
  constructor(
    options:
      | {
          type: GrDiffGroupType.BOTH | GrDiffGroupType.DELTA;
          lines?: GrDiffLine[];
          skip?: undefined;
          moveDetails?: GrMoveDetails;
          dueToRebase?: boolean;
          ignoredWhitespaceOnly?: boolean;
          keyLocation?: boolean;
        }
      | {
          type: GrDiffGroupType.BOTH | GrDiffGroupType.DELTA;
          lines?: undefined;
          skip: number;
          offsetLeft: number;
          offsetRight: number;
          moveDetails?: GrMoveDetails;
          dueToRebase?: boolean;
          ignoredWhitespaceOnly?: boolean;
          keyLocation?: boolean;
        }
      | {
          type: GrDiffGroupType.CONTEXT_CONTROL;
          contextGroups: GrDiffGroup[];
        }
  ) {
    this.type = options.type;
    switch (options.type) {
      case GrDiffGroupType.BOTH:
      case GrDiffGroupType.DELTA: {
        this.moveDetails = options.moveDetails;
        this.dueToRebase = options.dueToRebase ?? false;
        this.ignoredWhitespaceOnly = options.ignoredWhitespaceOnly ?? false;
        this.keyLocation = options.keyLocation ?? false;
        if (options.skip && options.lines) {
          throw new Error('Cannot set skip and lines');
        }
        this.skip = options.skip;
        if (options.skip !== undefined) {
          this.lineRange = {
            left: {
              start_line: options.offsetLeft,
              end_line: options.offsetLeft + options.skip - 1,
            },
            right: {
              start_line: options.offsetRight,
              end_line: options.offsetRight + options.skip - 1,
            },
          };
        } else {
          assertIsDefined(options.lines);
          assert(options.lines.length > 0, 'diff group must have lines');
          for (const line of options.lines) {
            this.addLine(line);
          }
        }
        break;
      }
      case GrDiffGroupType.CONTEXT_CONTROL: {
        this.contextGroups = options.contextGroups;
        if (this.contextGroups.length > 0) {
          const firstGroup = this.contextGroups[0];
          const lastGroup = this.contextGroups[this.contextGroups.length - 1];
          this.lineRange = {
            left: {
              start_line: firstGroup.lineRange.left.start_line,
              end_line: lastGroup.lineRange.left.end_line,
            },
            right: {
              start_line: firstGroup.lineRange.right.start_line,
              end_line: lastGroup.lineRange.right.end_line,
            },
          };
        }
        break;
      }
      default:
        throw new Error(`Unknown group type: ${this.type}`);
    }
  }

  readonly type: GrDiffGroupType;

  readonly dueToRebase: boolean = false;

  /**
   * True means all changes in this line are whitespace changes that should
   * not be highlighted as changed as per the user settings.
   */
  readonly ignoredWhitespaceOnly: boolean = false;

  /**
   * True means it should not be collapsed (because it was in the URL, or
   * there is a comment on that line)
   */
  readonly keyLocation: boolean = false;

  /**
   * Once rendered the diff builder sets this to the diff section element.
   */
  element?: HTMLElement;

  readonly lines: GrDiffLine[] = [];

  readonly adds: GrDiffLine[] = [];

  readonly removes: GrDiffLine[] = [];

  readonly contextGroups: GrDiffGroup[] = [];

  readonly skip?: number;

  /** Both start and end line are inclusive. */
  readonly lineRange: {[side in Side]: LineRange} = {
    [Side.LEFT]: {start_line: 0, end_line: 0},
    [Side.RIGHT]: {start_line: 0, end_line: 0},
  };

  readonly moveDetails?: GrMoveDetails;

  /**
   * Creates a new group with the same properties but different lines.
   *
   * The element property is not copied, because the original element is still a
   * rendering of the old lines, so that would not make sense.
   */
  cloneWithLines(lines: GrDiffLine[]): GrDiffGroup {
    if (
      this.type !== GrDiffGroupType.BOTH &&
      this.type !== GrDiffGroupType.DELTA
    ) {
      throw new Error('Cannot clone context group with lines');
    }
    const group = new GrDiffGroup({
      type: this.type,
      lines,
      dueToRebase: this.dueToRebase,
      ignoredWhitespaceOnly: this.ignoredWhitespaceOnly,
    });
    return group;
  }

  private addLine(line: GrDiffLine) {
    this.lines.push(line);

    const notDelta =
      this.type === GrDiffGroupType.BOTH ||
      this.type === GrDiffGroupType.CONTEXT_CONTROL;
    if (
      notDelta &&
      (line.type === GrDiffLineType.ADD || line.type === GrDiffLineType.REMOVE)
    ) {
      throw Error('Cannot add delta line to a non-delta group.');
    }

    if (line.type === GrDiffLineType.ADD) {
      this.adds.push(line);
    } else if (line.type === GrDiffLineType.REMOVE) {
      this.removes.push(line);
    }
    this._updateRangeWithNewLine(line);
  }

  getSideBySidePairs(): GrDiffLinePair[] {
    if (
      this.type === GrDiffGroupType.BOTH ||
      this.type === GrDiffGroupType.CONTEXT_CONTROL
    ) {
      return this.lines.map(line => {
        return {left: line, right: line};
      });
    }

    const pairs: GrDiffLinePair[] = [];
    let i = 0;
    let j = 0;
    while (i < this.removes.length || j < this.adds.length) {
      pairs.push({
        left: this.removes[i] || BLANK_LINE,
        right: this.adds[j] || BLANK_LINE,
      });
      i++;
      j++;
    }
    return pairs;
  }

  getUnifiedPairs(): GrDiffLinePair[] {
    return this.lines.map(line => {
      if (line.type === GrDiffLineType.ADD)
        return {left: BLANK_LINE, right: line};
      if (line.type === GrDiffLineType.REMOVE)
        return {left: line, right: BLANK_LINE};
      return {left: line, right: line};
    });
  }

  /** Returns true if it is, or contains, a skip group. */
  hasSkipGroup() {
    return !!this.skip || this.contextGroups?.some(g => !!g.skip);
  }

  containsLine(side: Side, line: LineNumber) {
    if (line === 'FILE' || line === 'LOST') {
      // For FILE and LOST, beforeNumber and afterNumber are the same
      return this.lines[0]?.beforeNumber === line;
    }
    const lineRange = this.lineRange[side];
    return lineRange.start_line <= line && line <= lineRange.end_line;
  }

  private _updateRangeWithNewLine(line: GrDiffLine) {
    if (
      line.beforeNumber === 'FILE' ||
      line.afterNumber === 'FILE' ||
      line.beforeNumber === 'LOST' ||
      line.afterNumber === 'LOST'
    ) {
      return;
    }

    if (line.type === GrDiffLineType.ADD || line.type === GrDiffLineType.BOTH) {
      if (
        this.lineRange.right.start_line === 0 ||
        line.afterNumber < this.lineRange.right.start_line
      ) {
        this.lineRange.right.start_line = line.afterNumber;
      }
      if (line.afterNumber > this.lineRange.right.end_line) {
        this.lineRange.right.end_line = line.afterNumber;
      }
    }

    if (
      line.type === GrDiffLineType.REMOVE ||
      line.type === GrDiffLineType.BOTH
    ) {
      if (
        this.lineRange.left.start_line === 0 ||
        line.beforeNumber < this.lineRange.left.start_line
      ) {
        this.lineRange.left.start_line = line.beforeNumber;
      }
      if (line.beforeNumber > this.lineRange.left.end_line) {
        this.lineRange.left.end_line = line.beforeNumber;
      }
    }
  }

  async waitUntilRendered() {
    const lineNumber = this.lines[0]?.beforeNumber;
    // The LOST or FILE lines may be hidden and thus never resolve an
    // untilRendered() promise.
    if (
      this.skip ||
      lineNumber === 'LOST' ||
      lineNumber === 'FILE' ||
      this.type === GrDiffGroupType.CONTEXT_CONTROL
    ) {
      return Promise.resolve();
    }
    assertIsDefined(this.element);
    // This is a temporary hack while migration to lit based diff rendering:
    // Elements with 'display: contents;' do not have a height, so they
    // won't work as intended with `untilRendered()`.
    const watchEl =
      this.element.tagName === 'GR-DIFF-SECTION'
        ? this.element.firstElementChild
        : this.element;
    assertIsDefined(watchEl);
    await untilRendered(watchEl as HTMLElement);
  }

  /**
   * Determines whether the group is either totally an addition or totally
   * a removal.
   */
  isTotal(): boolean {
    return (
      this.type === GrDiffGroupType.DELTA &&
      (!this.adds.length || !this.removes.length) &&
      !(!this.adds.length && !this.removes.length)
    );
  }
}
