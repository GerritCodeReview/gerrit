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
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffLine, GrDiffLineType, LineNumber} from '../gr-diff/gr-diff-line';
import {DiffViewMode, Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';
import {RenderPreferences} from '../../../api/diff';
import {createElementDiff} from '../gr-diff/gr-diff-utils';
import {GrDiffBuilderLegacy} from './gr-diff-builder-legacy';

export class GrDiffBuilderSideBySide extends GrDiffBuilderLegacy {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    super(diff, prefs, outputEl, layers, renderPrefs);
  }

  protected override getMoveControlsConfig() {
    return {
      numberOfCells: 6,
      movedOutIndex: 2,
      movedInIndex: 5,
      lineNumberCols: [0, 3],
      signCols: {left: 1, right: 4},
    };
  }

  // visible for testing
  override buildSectionElement(group: GrDiffGroup) {
    const sectionEl = createElementDiff('tbody', 'section');
    sectionEl.classList.add(group.type);
    if (group.isTotal()) {
      sectionEl.classList.add('total');
    }
    if (group.dueToRebase) {
      sectionEl.classList.add('dueToRebase');
    }
    if (group.moveDetails) {
      sectionEl.classList.add('dueToMove');
      sectionEl.appendChild(this.buildMoveControls(group));
    }
    if (group.ignoredWhitespaceOnly) {
      sectionEl.classList.add('ignoredWhitespaceOnly');
    }
    if (group.type === GrDiffGroupType.CONTEXT_CONTROL) {
      this.createContextControls(sectionEl, group, DiffViewMode.SIDE_BY_SIDE);
      return sectionEl;
    }

    const pairs = group.getSideBySidePairs();
    for (let i = 0; i < pairs.length; i++) {
      sectionEl.appendChild(this.createRow(pairs[i].left, pairs[i].right));
    }
    return sectionEl;
  }

  override addColumns(outputEl: HTMLElement, lineNumberWidth: number): void {
    const colgroup = document.createElement('colgroup');

    // Add the blame column.
    let col = createElementDiff('col', 'blame');
    colgroup.appendChild(col);

    // Add left-side line number.
    col = createElementDiff('col', 'left');
    col.setAttribute('width', lineNumberWidth.toString());
    colgroup.appendChild(col);

    colgroup.appendChild(createElementDiff('col', 'sign left'));

    // Add left-side content.
    colgroup.appendChild(createElementDiff('col', 'left'));

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', lineNumberWidth.toString());
    colgroup.appendChild(col);

    colgroup.appendChild(createElementDiff('col', 'sign right'));

    // Add right-side content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  }

  private createRow(leftLine: GrDiffLine, rightLine: GrDiffLine) {
    const row = createElementDiff('tr');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', leftLine.type);
    row.setAttribute('right-type', rightLine.type);
    // TabIndex makes screen reader read a row when navigating with j/k
    row.tabIndex = -1;

    row.appendChild(this.createBlameCell(leftLine.beforeNumber));

    this.appendPair(row, leftLine, leftLine.beforeNumber, Side.LEFT);
    this.appendPair(row, rightLine, rightLine.afterNumber, Side.RIGHT);
    return row;
  }

  private appendPair(
    row: HTMLElement,
    line: GrDiffLine,
    lineNumber: LineNumber,
    side: Side
  ) {
    const lineNumberEl = this.createLineEl(line, lineNumber, line.type, side);
    row.appendChild(lineNumberEl);
    row.appendChild(this.createSignEl(line, side));
    row.appendChild(this.createTextEl(lineNumberEl, line, side));
  }

  private createSignEl(line: GrDiffLine, side: Side): HTMLElement {
    const td = createElementDiff('td', 'sign');
    td.classList.add(side);
    if (line.type === GrDiffLineType.BLANK) {
      td.classList.add('blank');
    } else if (line.type === GrDiffLineType.ADD && side === Side.RIGHT) {
      td.classList.add('add');
      td.innerText = '+';
    } else if (line.type === GrDiffLineType.REMOVE && side === Side.LEFT) {
      td.classList.add('remove');
      td.innerText = '-';
    }
    if (!line.hasIntralineInfo) {
      td.classList.add('no-intraline-info');
    }
    return td;
  }

  // visible for testing
  override getNextContentOnSide(
    content: HTMLElement,
    side: Side
  ): HTMLElement | null {
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
