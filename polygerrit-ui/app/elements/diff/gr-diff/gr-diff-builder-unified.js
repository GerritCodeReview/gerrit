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

  function GrDiffBuilderUnified(diff, comments, prefs, outputEl) {
    GrDiffBuilder.call(this, diff, comments, prefs, outputEl);
  }
  GrDiffBuilderUnified.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderUnified.prototype.constructor = GrDiffBuilderUnified;

  GrDiffBuilderUnified.prototype.emitGroup = function(group,
      opt_beforeSection) {
    var sectionEl = this._createElement('tbody', 'section');

    for (var i = 0; i < group.lines.length; ++i) {
      sectionEl.appendChild(this._createRow(sectionEl, group.lines[i]));
    }
    this._outputEl.insertBefore(sectionEl, opt_beforeSection);
  };

  GrDiffBuilderUnified.prototype._createRow = function(section, line) {
    var row = this._createElement('tr', line.type);
    row.appendChild(this._createLineEl(line, line.beforeNumber,
        GrDiffLine.Type.REMOVE));
    row.appendChild(this._createLineEl(line, line.afterNumber,
        GrDiffLine.Type.ADD));

    var action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      var textEl = this._createTextEl(line);
      var threadEl = this._commentThreadForLine(line);
      if (threadEl) {
        textEl.appendChild(threadEl);
      }
      row.appendChild(textEl);
    }
    return row;
  };

  window.GrDiffBuilderUnified = GrDiffBuilderUnified;
})(window, GrDiffBuilder);
