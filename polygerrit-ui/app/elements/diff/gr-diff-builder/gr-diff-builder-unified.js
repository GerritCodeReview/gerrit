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
  if (window.GrDiffBuilderUnified) { return; }

  function GrDiffBuilderUnified(diff, comments, prefs, outputEl, layers) {
    GrDiffBuilder.call(this, diff, comments, prefs, outputEl, layers);
  }
  GrDiffBuilderUnified.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderUnified.prototype.constructor = GrDiffBuilderUnified;

  GrDiffBuilderUnified.prototype.buildSectionElement = function(group) {
    var sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    if (this._isTotal(group)) {
      sectionEl.classList.add('total');
    }

    for (var i = 0; i < group.lines.length; ++i) {
      sectionEl.appendChild(this._createRow(sectionEl, group.lines[i]));
    }
    return sectionEl;
  };

  GrDiffBuilderUnified.prototype.addColumns = function(outputEl, fontSize) {
    var width = fontSize * 4;
    var colgroup = document.createElement('colgroup');

    // Add left-side line number.
    var col = document.createElement('col');
    col.setAttribute('width', width);
    colgroup.appendChild(col);

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width);
    colgroup.appendChild(col);

    // Add the content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  };

  GrDiffBuilderUnified.prototype._createRow = function(section, line) {
    var row = this._createElement('tr', line.type);
    var lineEl = this._createLineEl(line, line.beforeNumber,
        GrDiffLine.Type.REMOVE);
    lineEl.classList.add('left');
    row.appendChild(lineEl);
    lineEl = this._createLineEl(line, line.afterNumber,
        GrDiffLine.Type.ADD);
    lineEl.classList.add('right');
    row.appendChild(lineEl);
    row.classList.add('diff-row', 'unified');

    var action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      var textEl = this._createTextEl(line);
      var threadGroupEl = this._commentThreadGroupForLine(line);
      if (threadGroupEl) {
        textEl.appendChild(threadGroupEl);
      }
      row.appendChild(textEl);
    }
    return row;
  };

  GrDiffBuilderUnified.prototype._getNextContentOnSide = function(
      content, side) {
    var tr = content.parentElement.parentElement;
    while (tr = tr.nextSibling) {
      if (tr.classList.contains('both') || (
          (side === 'left' && tr.classList.contains('remove')) ||
          (side === 'right' && tr.classList.contains('add')))) {
        return tr.querySelector('.contentText');
      }
    }
    return null;
  };

  window.GrDiffBuilderUnified = GrDiffBuilderUnified;
})(window, GrDiffBuilder);
