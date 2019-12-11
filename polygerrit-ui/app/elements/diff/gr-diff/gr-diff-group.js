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
(function(window, GrDiffLine) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffGroup) { return; }

  /**
   * A chunk of the diff that should be rendered together.
   *
   * @param {!GrDiffGroup.Type} type
   * @param {!Array<!GrDiffLine>=} opt_lines
   */
  function GrDiffGroup(type, opt_lines) {
    /** @type {!GrDiffGroup.Type} */
    this.type = type;

    /** @type {boolean} */
    this.dueToRebase = false;

    /**
     * True means all changes in this line are whitespace changes that should
     * not be highlighted as changed as per the user settings.
     *
     * @type{boolean}
     */
    this.ignoredWhitespaceOnly = false;

    /**
     * True means it should not be collapsed (because it was in the URL, or
     * there is a comment on that line)
     */
    this.keyLocation = false;

    /** @type {?HTMLElement} */
    this.element = null;

    /** @type {!Array<!GrDiffLine>} */
    this.lines = [];
    /** @type {!Array<!GrDiffLine>} */
    this.adds = [];
    /** @type {!Array<!GrDiffLine>} */
    this.removes = [];

    /** Both start and end line are inclusive. */
    this.lineRange = {
      left: {start: null, end: null},
      right: {start: null, end: null},
    };

    if (opt_lines) {
      opt_lines.forEach(this.addLine, this);
    }
  }

  /** @enum {string} */
  GrDiffGroup.Type = {
    /** Unchanged context. */
    BOTH: 'both',

    /** A widget used to show more context. */
    CONTEXT_CONTROL: 'contextControl',

    /** Added, removed or modified chunk. */
    DELTA: 'delta',
  };

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
   * @param {!Array<!GrDiffGroup>} groups Common groups, ordered by their line
   *     ranges.
   * @param {number} hiddenStart The first element to be hidden, as a
   *     non-negative line number offset relative to the first group's start
   *     line, left and right respectively.
   * @param {number} hiddenEnd The first visible element after the hidden range,
   *     as a non-negative line number offset relative to the first group's
   *     start line, left and right respectively.
   * @return {!Array<!GrDiffGroup>}
   */
  GrDiffGroup.hideInContextControl = function(groups, hiddenStart, hiddenEnd) {
    if (groups.length === 0) return [];
    // Clamp hiddenStart and hiddenEnd - inspired by e.g. substring
    hiddenStart = Math.max(hiddenStart, 0);
    hiddenEnd = Math.max(hiddenEnd, hiddenStart);

    let before = [];
    let hidden = groups;
    let after = [];

    const numHidden = hiddenEnd - hiddenStart;

    // Only collapse if there is more than 1 line to be hidden.
    if (numHidden > 1) {
      if (hiddenStart) {
        [before, hidden] = GrDiffGroup._splitCommonGroups(hidden, hiddenStart);
      }
      if (hiddenEnd) {
        [hidden, after] = GrDiffGroup._splitCommonGroups(
            hidden, hiddenEnd - hiddenStart);
      }
    } else {
      [hidden, after] = [[], hidden];
    }

    const result = [...before];
    if (hidden.length) {
      const ctxLine = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
      ctxLine.contextGroups = hidden;
      const ctxGroup = new GrDiffGroup(
          GrDiffGroup.Type.CONTEXT_CONTROL, [ctxLine]);
      result.push(ctxGroup);
    }
    result.push(...after);
    return result;
  };

  /**
   * Splits a list of common groups into two lists of groups.
   *
   * Groups where all lines are before or all lines are after the split will be
   * retained as is and put into the first or second list respectively. Groups
   * with some lines before and some lines after the split will be split into
   * two groups, which will be put into the first and second list.
   *
   * @param {!Array<!GrDiffGroup>} groups
   * @param {number} split A line number offset relative to the first group's
   *     start line at which the groups should be split.
   * @return {!Array<!Array<!GrDiffGroup>>} The outer array has 2 elements, the
   *   list of groups before and the list of groups after the split.
   */
  GrDiffGroup._splitCommonGroups = function(groups, split) {
    if (groups.length === 0) return [[], []];
    const leftSplit = groups[0].lineRange.left.start + split;
    const rightSplit = groups[0].lineRange.right.start + split;

    const beforeGroups = [];
    const afterGroups = [];
    for (const group of groups) {
      if (group.lineRange.left.end < leftSplit ||
          group.lineRange.right.end < rightSplit) {
        beforeGroups.push(group);
        continue;
      }
      if (leftSplit <= group.lineRange.left.start ||
          rightSplit <= group.lineRange.right.start) {
        afterGroups.push(group);
        continue;
      }

      const before = [];
      const after = [];
      for (const line of group.lines) {
        if ((line.beforeNumber && line.beforeNumber < leftSplit) ||
            (line.afterNumber && line.afterNumber < rightSplit)) {
          before.push(line);
        } else {
          after.push(line);
        }
      }

      if (before.length) {
        beforeGroups.push(before.length === group.lines.length ?
          group : group.cloneWithLines(before));
      }
      if (after.length) {
        afterGroups.push(after.length === group.lines.length ?
          group : group.cloneWithLines(after));
      }
    }
    return [beforeGroups, afterGroups];
  };

  /**
   * Creates a new group with the same properties but different lines.
   *
   * The element property is not copied, because the original element is still a
   * rendering of the old lines, so that would not make sense.
   *
   * @param {!Array<!GrDiffLine>} lines
   * @return {!GrDiffGroup}
   */
  GrDiffGroup.prototype.cloneWithLines = function(lines) {
    const group = new GrDiffGroup(this.type, lines);
    group.dueToRebase = this.dueToRebase;
    group.ignoredWhitespaceOnly = this.ignoredWhitespaceOnly;
    return group;
  };

  /** @param {!GrDiffLine} line */
  GrDiffGroup.prototype.addLine = function(line) {
    this.lines.push(line);

    const notDelta = (this.type === GrDiffGroup.Type.BOTH ||
        this.type === GrDiffGroup.Type.CONTEXT_CONTROL);
    if (notDelta && (line.type === GrDiffLine.Type.ADD ||
        line.type === GrDiffLine.Type.REMOVE)) {
      throw Error('Cannot add delta line to a non-delta group.');
    }

    if (line.type === GrDiffLine.Type.ADD) {
      this.adds.push(line);
    } else if (line.type === GrDiffLine.Type.REMOVE) {
      this.removes.push(line);
    }
    this._updateRange(line);
  };

  /** @return {!Array<{left: GrDiffLine, right: GrDiffLine}>} */
  GrDiffGroup.prototype.getSideBySidePairs = function() {
    if (this.type === GrDiffGroup.Type.BOTH ||
        this.type === GrDiffGroup.Type.CONTEXT_CONTROL) {
      return this.lines.map(line => {
        return {
          left: line,
          right: line,
        };
      });
    }

    const pairs = [];
    let i = 0;
    let j = 0;
    while (i < this.removes.length || j < this.adds.length) {
      pairs.push({
        left: this.removes[i] || GrDiffLine.BLANK_LINE,
        right: this.adds[j] || GrDiffLine.BLANK_LINE,
      });
      i++;
      j++;
    }
    return pairs;
  };

  GrDiffGroup.prototype._updateRange = function(line) {
    if (line.beforeNumber === 'FILE' || line.afterNumber === 'FILE') { return; }

    if (line.type === GrDiffLine.Type.ADD ||
        line.type === GrDiffLine.Type.BOTH) {
      if (this.lineRange.right.start === null ||
          line.afterNumber < this.lineRange.right.start) {
        this.lineRange.right.start = line.afterNumber;
      }
      if (this.lineRange.right.end === null ||
          line.afterNumber > this.lineRange.right.end) {
        this.lineRange.right.end = line.afterNumber;
      }
    }

    if (line.type === GrDiffLine.Type.REMOVE ||
        line.type === GrDiffLine.Type.BOTH) {
      if (this.lineRange.left.start === null ||
          line.beforeNumber < this.lineRange.left.start) {
        this.lineRange.left.start = line.beforeNumber;
      }
      if (this.lineRange.left.end === null ||
          line.beforeNumber > this.lineRange.left.end) {
        this.lineRange.left.end = line.beforeNumber;
      }
    }
  };

  window.GrDiffGroup = GrDiffGroup;
})(window, GrDiffLine);
