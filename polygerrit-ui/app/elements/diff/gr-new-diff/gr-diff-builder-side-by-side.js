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

  function GrDiffBuilderSideBySide(diff, prefs, outputEl) {
    GrDiffBuilder.call(this, diff, prefs, outputEl);
  }
  GrDiffBuilderSideBySide.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderSideBySide.prototype.constructor = GrDiffBuilderSideBySide;

  GrDiffBuilderSideBySide.prototype.emitGroup = function(group,
      opt_beforeSection) {
    var sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    var pairs = group.getSideBySidePairs();
    for (var i = 0; i < pairs.length; i++) {
      sectionEl.appendChild(this._createRow(sectionEl, pairs[i].left,
          pairs[i].right));
    }
    this._outputEl.insertBefore(sectionEl, opt_beforeSection);
  },

  GrDiffBuilderSideBySide.prototype._createRow = function(section, leftLine,
      rightLine) {
    var row = this._createElement('tr');
    this._createPair(section, row, leftLine, leftLine.beforeNumber);
    this._createPair(section, row, rightLine, rightLine.afterNumber);
    return row;
  };

  GrDiffBuilderSideBySide.prototype._createPair = function(section, row, line,
      lineNumber) {
    if (line.type === GrDiffLine.Type.BLANK) {
      row.appendChild(this._createBlankSideEl());
      return row;
    }

    row.appendChild(this._createLineEl(line, lineNumber, line.type));
    var action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      row.appendChild(this._createTextEl(line));
    }
    return row;
  };

  window.GrDiffBuilderSideBySide = GrDiffBuilderSideBySide;
})(window, GrDiffBuilder);
