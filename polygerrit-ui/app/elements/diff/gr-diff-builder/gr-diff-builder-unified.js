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
import {GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {GrDiffBuilder} from './gr-diff-builder.js';
import {GrDiffGroupType} from '../gr-diff/gr-diff-group.js';

export function GrDiffBuilderUnified(diff, prefs, outputEl, layers) {
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
  if (group.type === GrDiffGroupType.CONTEXT_CONTROL) {
    sectionEl.appendChild(
        this._createContextRow(sectionEl, group.contextGroups));
    return sectionEl;
  }

  for (let i = 0; i < group.lines.length; ++i) {
    const line = group.lines[i];
    // If only whitespace has changed and the settings ask for whitespace to
    // be ignored, only render the right-side line in unified diff mode.
    if (group.ignoredWhitespaceOnly && line.type == GrDiffLineType.REMOVE) {
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
  row.appendChild(this._createBlameCell(line.beforeNumber));
  let lineNumberEl = this._createLineEl(line, line.beforeNumber,
      GrDiffLineType.REMOVE, 'left');
  row.appendChild(lineNumberEl);
  lineNumberEl = this._createLineEl(line, line.afterNumber,
      GrDiffLineType.ADD, 'right');
  row.appendChild(lineNumberEl);
  row.appendChild(this._createTextEl(lineNumberEl, line));
  return row;
};

GrDiffBuilderUnified.prototype._createContextRow = function(section,
    contextGroups) {
  const row = this._createElement('tr', GrDiffGroupType.CONTEXT_CONTROL);
  row.classList.add('diff-row', 'unified');
  row.tabIndex = -1;
  row.appendChild(this._createBlameCell(0));
  row.appendChild(this._createElement('td', 'contextLineNum'));
  row.appendChild(this._createElement('td', 'contextLineNum'));
  row.appendChild(this._createContextControl(section, contextGroups));
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
