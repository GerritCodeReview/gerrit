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
    this.type = type;

    /** @type{!Array<!GrDiffLine>} */
    this.lines = [];
    /** @type{!Array<!GrDiffLine>} */
    this.adds = [];
    /** @type{!Array<!GrDiffLine>} */
    this.removes = [];

    /** @type{boolean|undefined} */
    this.dueToRebase = undefined;
    /**
     * True means all changes in this line are whitespace changes that should
     * not be highlighted as changed as per the user settings.
     * @type{boolean|undefined}
     */
    this.ignoredWhitespaceOnly = undefined;

    /** Both start and end line are inclusive. */
    this.lineRange = {
      left: {start: null, end: null},
      right: {start: null, end: null},
    };

    if (opt_lines) {
      opt_lines.forEach(this.addLine, this);
    }
  }

  GrDiffGroup.hideInContextControl = function(groups, hiddenStart, hiddenEnd) {
    let before = [];
    let hidden = groups;
    let after = [];

    const numHidden = hiddenEnd - hiddenStart;

    // Only collapse if there is more than 1 line to be hidden.
    if (numHidden > 1) {
      if (hiddenStart) {
        [before, hidden] = GrDiffGroup.splitCommonGroups(hidden, hiddenStart);
      }
      if (hiddenEnd) {
        [hidden, after] = GrDiffGroup.splitCommonGroups(
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

  GrDiffGroup.splitCommonGroups = function(groups, split) {
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
  },


  GrDiffGroup.prototype.cloneWithLines = function(lines) {
    const group = new GrDiffGroup(this.type, lines);
    group.dueToRebase = this.dueToRebase;
    group.ignoredWhitespaceOnly = this.ignoredWhitespaceOnly;
    return group;
  };

  GrDiffGroup.prototype.element = null;

  GrDiffGroup.Type = {
    /** Unchanged context. */
    BOTH: 'both',

    /** A widget used to show more context. */
    CONTEXT_CONTROL: 'contextControl',

    /** Added, removed or modified chunk. */
    DELTA: 'delta',
  };

  /**
   * @param {!GrDiffLine} line
   */
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
