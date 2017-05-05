// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the 'License');
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an 'AS IS' BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window, GrDiffLine) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffGroup) { return; }

  function GrDiffGroup(type, opt_lines) {
    this.type = type;
    this.lines = [];
    this.adds = [];
    this.removes = [];
    this.dueToRebase = undefined;

    this.lineRange = {
      left: {start: null, end: null},
      right: {start: null, end: null},
    };

    if (opt_lines) {
      opt_lines.forEach(this.addLine, this);
    }
  }

  GrDiffGroup.prototype.element = null;

  GrDiffGroup.Type = {
    BOTH: 'both',
    CONTEXT_CONTROL: 'contextControl',
    DELTA: 'delta',
  };

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
