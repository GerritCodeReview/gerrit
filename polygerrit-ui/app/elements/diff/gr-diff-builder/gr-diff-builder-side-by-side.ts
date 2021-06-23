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
import {RenderPreferences} from '../../../api/diff';
import {GrDiffRow} from './gr-diff-row';
import './gr-diff-row';

export class GrDiffBuilderSideBySide extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    readonly layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    super(diff, prefs, outputEl, layers, renderPrefs);
  }

  _getMoveControlsConfig() {
    return {
      numberOfCells: 4,
      movedOutIndex: 1,
      movedInIndex: 3,
    };
  }

  buildSectionElement(group: GrDiffGroup) {
    const sectionEl = document.createElement('tbody');
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
    col = this._createElement('col', 'left');
    col.setAttribute('width', width.toString());
    colgroup.appendChild(col);

    // Add left-side content.
    colgroup.appendChild(this._createElement('col', 'left'));

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width.toString());
    colgroup.appendChild(col);

    // Add right-side content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  }

  _createRow(_1: GrDiffLine, _2: GrDiffLine) {
    const row = document.createElement('gr-diff-row') as GrDiffRow;
    // row.leftText = leftLine.text;
    // row.leftNumber = leftLine.beforeNumber;
    // row.rightText = rightLine.text;
    // row.rightNumber = rightLine.beforeNumber;
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
