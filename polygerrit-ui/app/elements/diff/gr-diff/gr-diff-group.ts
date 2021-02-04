/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {BLANK_LINE, GrDiffLine, GrDiffLineType} from './gr-diff-line';
import {LineRange} from '../../../api/diff';

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
 * If the hidden range is 1 line or less, nothing is hidden and no context
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
  groups: GrDiffGroup[],
  hiddenStart: number,
  hiddenEnd: number
): GrDiffGroup[] {
  if (groups.length === 0) return [];
  // Clamp hiddenStart and hiddenEnd - inspired by e.g. substring
  hiddenStart = Math.max(hiddenStart, 0);
  hiddenEnd = Math.max(hiddenEnd, hiddenStart);

  let before: GrDiffGroup[] = [];
  let hidden = groups;
  let after: GrDiffGroup[] = [];

  const numHidden = hiddenEnd - hiddenStart;

  // Showing a context control row for less than 4 lines does not make much,
  // because then that row would consume as much space as the collapsed code.
  if (numHidden > 3) {
    if (hiddenStart) {
      [before, hidden] = _splitCommonGroups(hidden, hiddenStart);
    }
    if (hiddenEnd) {
      let beforeLength = 0;
      if (before.length > 0) {
        const beforeStart = before[0].lineRange.left.start_line;
        const beforeEnd = before[before.length - 1].lineRange.left.end_line;
        beforeLength = beforeEnd - beforeStart + 1;
      }
      [hidden, after] = _splitCommonGroups(hidden, hiddenEnd - beforeLength);
    }
  } else {
    [hidden, after] = [[], hidden];
  }

  const result = [...before];
  if (hidden.length) {
    const ctxGroup = new GrDiffGroup(GrDiffGroupType.CONTEXT_CONTROL, []);
    ctxGroup.contextGroups = hidden;
    result.push(ctxGroup);
  }
  result.push(...after);
  return result;
}

/**
 * Splits a group in two, defined by leftSplit and rightSplit. Primarily to be
 * used in function _splitCommonGroups
 * Groups with some lines before and some lines after the split will be split
 * into two groups, which will be put into the first and second list.
 *
 * @param group The group to be split in two
 * @param leftSplit The line number relative to the split on the left side
 * @param rightSplit The line number relative to the split on the right side
 * @return two new groups, one before the split and another after it
 */
function _splitGroupInTwo(
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
function _splitCommonGroups(
  groups: GrDiffGroup[],
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
      const {beforeSplit, afterSplit} = _splitGroupInTwo(
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

/**
 * A chunk of the diff that should be rendered together.
 *
 * @constructor
 * @param {!GrDiffGroupType} type
 * @param {!Array<!GrDiffLine>=} opt_lines
 */
export class GrDiffGroup {
  constructor(readonly type: GrDiffGroupType, lines: GrDiffLine[] = []) {
    lines.forEach((line: GrDiffLine) => this.addLine(line));
  }

  dueToRebase = false;

  /**
   * True means all changes in this line are whitespace changes that should
   * not be highlighted as changed as per the user settings.
   */
  ignoredWhitespaceOnly = false;

  /**
   * True means it should not be collapsed (because it was in the URL, or
   * there is a comment on that line)
   */
  keyLocation = false;

  portedCommentsWithoutRange = false;

  element?: HTMLElement;

  lines: GrDiffLine[] = [];

  adds: GrDiffLine[] = [];

  removes: GrDiffLine[] = [];

  contextGroups: GrDiffGroup[] = [];

  skip?: number;

  /** Both start and end line are inclusive. */
  lineRange = {
    left: {start_line: 0, end_line: 0} as LineRange,
    right: {start_line: 0, end_line: 0} as LineRange,
  };

  moveDetails?: {
    changed: boolean;
    range?: {
      start: number;
      end: number;
    };
  };

  /**
   * Creates a new group with the same properties but different lines.
   *
   * The element property is not copied, because the original element is still a
   * rendering of the old lines, so that would not make sense.
   */
  cloneWithLines(lines: GrDiffLine[]): GrDiffGroup {
    const group = new GrDiffGroup(this.type, lines);
    group.dueToRebase = this.dueToRebase;
    group.ignoredWhitespaceOnly = this.ignoredWhitespaceOnly;
    return group;
  }

  addLine(line: GrDiffLine) {
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
    this._updateRange(line);
  }

  getSideBySidePairs(): GrDiffLinePair[] {
    if (
      this.type === GrDiffGroupType.BOTH ||
      this.type === GrDiffGroupType.CONTEXT_CONTROL
    ) {
      return this.lines.map(line => {
        return {
          left: line,
          right: line,
        };
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

  _updateRange(line: GrDiffLine) {
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
}
