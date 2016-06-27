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
(function(window, GrDiffBuilder) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderSideBySide) { return; }

  function GrDiffBuilderSideBySide(diff, comments, prefs, outputEl) {
    GrDiffBuilder.call(this, diff, comments, prefs, outputEl);
  }
  GrDiffBuilderSideBySide.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderSideBySide.prototype.constructor = GrDiffBuilderSideBySide;

  GrDiffBuilderSideBySide.prototype.buildSectionElement = function(group) {
    var sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    var pairs = group.getSideBySidePairs();
    for (var i = 0; i < pairs.length; i++) {
      sectionEl.appendChild(this._createRow(sectionEl, pairs[i].left,
          pairs[i].right));
    }
    return sectionEl;
  };

  GrDiffBuilderSideBySide.prototype._createRow = function(section, leftLine,
      rightLine) {
    var row = this._createElement('tr');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', leftLine.type);
    row.setAttribute('right-type', rightLine.type);

    this._appendPair(section, row, leftLine, leftLine.beforeNumber,
        GrDiffBuilder.Side.LEFT);
    this._appendPair(section, row, rightLine, rightLine.afterNumber,
        GrDiffBuilder.Side.RIGHT);
    return row;
  };

  GrDiffBuilderSideBySide.prototype._appendPair = function(section, row, line,
      lineNumber, side) {
    var lineEl = this._createLineEl(line, lineNumber, line.type, side);
    lineEl.classList.add(side);
    row.appendChild(lineEl);
    var action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      var textEl = this._createTextEl(line);
      var threadEl = this._commentThreadForLine(line, side);
      if (threadEl) {
        textEl.appendChild(threadEl);
      }
      row.appendChild(textEl);
    }
  };

  window.GrDiffBuilderSideBySide = GrDiffBuilderSideBySide;
})(window, GrDiffBuilder);
