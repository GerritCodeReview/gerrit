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
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffLine, LineNumber} from '../gr-diff/gr-diff-line';
import {DiffViewMode, Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';

export class GrDiffBuilderSideBySide extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    readonly layers: DiffLayer[] = []
  ) {
    super(diff, prefs, outputEl, layers);
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
    if (group.moveDetails) {
      sectionEl.classList.add('dueToMove');
      sectionEl.appendChild(this._buildMoveControls(group));
    }
    if (group.ignoredWhitespaceOnly) {
      sectionEl.classList.add('ignoredWhitespaceOnly');
    }
    if (group.type === GrDiffGroupType.CONTEXT_CONTROL) {
      this._createContextControls(
        sectionEl,
        group.contextGroups,
        DiffViewMode.SIDE_BY_SIDE
      );
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
    // TabIndex makes screen reader read a row when navigating with j/k
    row.tabIndex = -1;

    if (leftLine.beforeNumber !== 'FILE')
      leftLine.beforeNumber += this.lineOffset - 1;
    if (leftLine.afterNumber !== 'FILE')
      leftLine.afterNumber += this.lineOffset - 1;
    if (rightLine.beforeNumber !== 'FILE')
      rightLine.beforeNumber += this.lineOffset - 1;
    if (rightLine.afterNumber !== 'FILE')
      rightLine.afterNumber += this.lineOffset - 1;

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
