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
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {DiffViewMode, Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';
import {RenderPreferences} from '../../../api/diff';
import {createElementDiff} from '../gr-diff/gr-diff-utils';
import {GrDiffBuilderLegacy} from './gr-diff-builder-legacy';

export class GrDiffBuilderUnified extends GrDiffBuilderLegacy {
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
      numberOfCells: 3,
      movedOutIndex: 2,
      movedInIndex: 2,
      lineNumberCols: [0, 1],
    };
  }

  // visible for testing
  override buildSectionElement(group: GrDiffGroup): HTMLElement {
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
      this.createContextControls(sectionEl, group, DiffViewMode.UNIFIED);
      return sectionEl;
    }

    for (let i = 0; i < group.lines.length; ++i) {
      const line = group.lines[i];
      // If only whitespace has changed and the settings ask for whitespace to
      // be ignored, only render the right-side line in unified diff mode.
      if (group.ignoredWhitespaceOnly && line.type === GrDiffLineType.REMOVE) {
        continue;
      }
      sectionEl.appendChild(this.createRow(line));
    }
    return sectionEl;
  }

  override addColumns(outputEl: HTMLElement, lineNumberWidth: number): void {
    const colgroup = document.createElement('colgroup');

    // Add the blame column.
    let col = createElementDiff('col', 'blame');
    colgroup.appendChild(col);

    // Add left-side line number.
    col = document.createElement('col');
    col.setAttribute('width', lineNumberWidth.toString());
    colgroup.appendChild(col);

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', lineNumberWidth.toString());
    colgroup.appendChild(col);

    // Add the content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  }

  protected createRow(line: GrDiffLine) {
    const row = createElementDiff('tr', line.type);
    row.classList.add('diff-row', 'unified');
    // TabIndex makes screen reader read a row when navigating with j/k
    row.tabIndex = -1;
    row.appendChild(this.createBlameCell(line.beforeNumber));
    let lineNumberEl = this.createLineEl(
      line,
      line.beforeNumber,
      GrDiffLineType.REMOVE,
      Side.LEFT
    );
    row.appendChild(lineNumberEl);
    lineNumberEl = this.createLineEl(
      line,
      line.afterNumber,
      GrDiffLineType.ADD,
      Side.RIGHT
    );
    row.appendChild(lineNumberEl);
    let side = undefined;
    if (line.type === GrDiffLineType.ADD || line.type === GrDiffLineType.BOTH) {
      side = Side.RIGHT;
    }
    if (line.type === GrDiffLineType.REMOVE) {
      side = Side.LEFT;
    }
    row.appendChild(this.createTextEl(lineNumberEl, line, side));
    return row;
  }

  getNextContentOnSide(content: HTMLElement, side: Side): HTMLElement | null {
    let tr: HTMLElement = content.parentElement!.parentElement!;
    while ((tr = tr.nextSibling as HTMLElement)) {
      if (
        tr.classList.contains('both') ||
        (side === 'left' && tr.classList.contains('remove')) ||
        (side === 'right' && tr.classList.contains('add'))
      ) {
        return tr.querySelector('.contentText');
      }
    }
    return null;
  }
}
