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
(function(window, GrDiffBuilder) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderUnified) { return; }

  function GrDiffBuilderUnified(diff, prefs, outputEl, layers) {
    GrDiffBuilder.call(this, diff, prefs, outputEl, layers);
  }
  GrDiffBuilderUnified.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderUnified.prototype.constructor = GrDiffBuilderUnified;

  GrDiffBuilderUnified.prototype.buildSectionElement = function(group) {
    const sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    if (this._isTotal(group)) {
      sectionEl.classList.add('total');
    }
    if (group.dueToRebase) {
      sectionEl.classList.add('dueToRebase');
    }
    if (group.ignoredWhitespaceOnly) {
      sectionEl.classList.add('ignoredWhitespaceOnly');
    }

    for (let i = 0; i < group.lines.length; ++i) {
      const line = group.lines[i];
      // If only whitespace has changed and the settings ask for whitespace to
      // be ignored, only render the right-side line in unified diff mode.
      if (group.ignoredWhitespaceOnly && line.type == GrDiffLine.Type.REMOVE) {
        continue;
      }
      sectionEl.appendChild(this._createRow(sectionEl, line));
    }
    return sectionEl;
  };

  GrDiffBuilderUnified.prototype.addColumns = function(outputEl, fontSize) {
    const width = fontSize * 4;
    const colgroup = document.createElement('colgroup');

    // Add the blame column.
    let col = this._createElement('col', 'blame');
    colgroup.appendChild(col);

    // Add left-side line number.
    col = document.createElement('col');
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
    const row = this._createElement('tr', line.type);
    row.classList.add('diff-row', 'unified');
    row.tabIndex = -1;
    row.appendChild(this._createBlameCell(line));

    let lineNumberEl = this._createLineEl(line, line.beforeNumber,
        GrDiffLine.Type.REMOVE);
    lineNumberEl.classList.add('left');
    row.appendChild(lineNumberEl);
    lineNumberEl = this._createLineEl(line, line.afterNumber,
        GrDiffLine.Type.ADD);
    lineNumberEl.classList.add('right');
    row.appendChild(lineNumberEl);

    const action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      const textEl = this._createTextEl(lineNumberEl, line);
      row.appendChild(textEl);
    }
    return row;
  };

  GrDiffBuilderUnified.prototype._getNextContentOnSide = function(
      content, side) {
    let tr = content.parentElement.parentElement;
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
