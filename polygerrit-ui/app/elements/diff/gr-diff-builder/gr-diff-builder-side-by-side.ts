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

import {GrDiffBuilder} from './gr-diff-builder';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/common';
import {GrDiffLine, LineNumber} from '../gr-diff/gr-diff-line';
import {Side} from '../../../constants/constants';

export class GrDiffBuilderSideBySide extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    // TODO(TS): Replace any by a layer interface.
    readonly layers: any[] = [],
    useNewContextControls: boolean = false
  ) {
    super(diff, prefs, outputEl, layers, useNewContextControls);
  }

  _getMoveControlsConfig() {
    return {
      numberOfCells: 4,
      movedOutIndex: 1,
      movedInIndex: 3,
    };
  }

  buildSectionElement(group: GrDiffGroup) {
    const sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    if (this._isTotal(group)) {
      sectionEl.classList.add('total');
    }
    if (group.dueToRebase) {
      sectionEl.classList.add('dueToRebase');
    }
    if (group.dueToMove) {
      sectionEl.classList.add('dueToMove');
      sectionEl.appendChild(this._buildMoveControls(group));
    }
    if (group.ignoredWhitespaceOnly) {
      sectionEl.classList.add('ignoredWhitespaceOnly');
    }
    if (group.type === GrDiffGroupType.CONTEXT_CONTROL) {
      if (this.useNewContextControls) {
        const contextControlRows = this._createContextRows(
          sectionEl,
          group.contextGroups
        );
        for (const contextControlRow of contextControlRows) {
          sectionEl.appendChild(contextControlRow);
        }
      } else {
        sectionEl.appendChild(
          this._createContextRow(sectionEl, group.contextGroups)
        );
      }
      return sectionEl;
    }

    const pairs = group.getSideBySidePairs();
    for (let i = 0; i < pairs.length; i++) {
      sectionEl.appendChild(this._createRow(pairs[i].left, pairs[i].right));
    }
    return sectionEl;
  }

  addColumns(outputEl: HTMLElement, fontSize: number): void {
    const width = fontSize * 4;
    const colgroup = document.createElement('colgroup');

    // Add the blame column.
    let col = this._createElement('col', 'blame');
    colgroup.appendChild(col);

    // Add left-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width.toString());
    colgroup.appendChild(col);

    // Add left-side content.
    colgroup.appendChild(document.createElement('col'));

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width.toString());
    colgroup.appendChild(col);

    // Add right-side content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  }

  _createRow(leftLine: GrDiffLine, rightLine: GrDiffLine) {
    const row = this._createElement('tr');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', leftLine.type);
    row.setAttribute('right-type', rightLine.type);
    row.tabIndex = -1;

    row.appendChild(this._createBlameCell(leftLine.beforeNumber));

    this._appendPair(row, leftLine, leftLine.beforeNumber, Side.LEFT);
    this._appendPair(row, rightLine, rightLine.afterNumber, Side.RIGHT);
    return row;
  }

  _appendPair(
    row: HTMLElement,
    line: GrDiffLine,
    lineNumber: LineNumber,
    side: Side
  ) {
    const lineNumberEl = this._createLineEl(line, lineNumber, line.type, side);
    row.appendChild(lineNumberEl);
    row.appendChild(this._createTextEl(lineNumberEl, line, side));
  }

  _createContextRow(section: HTMLElement, contextGroups: GrDiffGroup[]) {
    const row = this._createElement('tr');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', GrDiffGroupType.CONTEXT_CONTROL);
    row.setAttribute('right-type', GrDiffGroupType.CONTEXT_CONTROL);
    row.tabIndex = -1;

    row.appendChild(this._createBlameCell(0));
    row.appendChild(this._createElement('td', 'contextLineNum'));
    row.appendChild(this._createContextControl(section, contextGroups));
    row.appendChild(this._createElement('td', 'contextLineNum'));
    row.appendChild(this._createContextControl(section, contextGroups));
    return row;
  }

  _createContextRows(section: HTMLElement, contextGroups: GrDiffGroup[]) {
    const {element, hasAbove, hasBelow} = this._createNewContextControl(
      section,
      contextGroups
    );

    const rows = [];

    if (hasAbove) {
      const rowAbove = this._createContextControlBackgroundRow();
      rowAbove.classList.add('above');
      rows.push(rowAbove);
    }

    const rowDivider = this._createElement('tr', 'contextDivider');
    rowDivider.classList.add('diff-row', 'side-by-side');
    rowDivider.setAttribute('left-type', GrDiffGroupType.CONTEXT_CONTROL);
    rowDivider.setAttribute('right-type', GrDiffGroupType.CONTEXT_CONTROL);
    rowDivider.tabIndex = -1;

    rowDivider.appendChild(element);
    if (!(hasAbove && hasBelow)) {
      rowDivider.classList.add('collapsed');
    }

    rows.push(rowDivider);

    if (hasBelow) {
      const rowBelow = this._createContextControlBackgroundRow();
      rowBelow.classList.add('below');
      rows.push(rowBelow);
    }

    return rows;
  }

  _createContextControlBackgroundRow() {
    const row = this._createElement('tr', 'contextBackground');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', GrDiffGroupType.CONTEXT_CONTROL);
    row.setAttribute('right-type', GrDiffGroupType.CONTEXT_CONTROL);
    row.tabIndex = -1;

    row.appendChild(this._createBlameCell(0));
    row.appendChild(this._createElement('td', 'contextLineNum'));
    row.appendChild(this._createElement('td'));
    row.appendChild(this._createElement('td', 'contextLineNum'));
    row.appendChild(this._createElement('td'));

    return row;
  }

  _getNextContentOnSide(content: HTMLElement, side: Side): HTMLElement | null {
    let tr: HTMLElement = content.parentElement!.parentElement!;
    while ((tr = tr.nextSibling as HTMLElement)) {
      const nextContent = tr.querySelector(
        'td.content .contentText[data-side="' + side + '"]'
      );
      if (nextContent) return nextContent as HTMLElement;
    }
    return null;
  }
}
