/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
      if (group.moveDetails.changed) {
        sectionEl.classList.add('changed');
      }
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

    // Before Chrome 102, Chrome was able to compute a11y label from children
    // content. Now Chrome 102 and Firefox are not computing a11y label because
    // tr is not expected to have aria label. Adding aria role button is
    // pushing browser to compute aria even for tr. This can be removed, once
    // browsers will again compute a11y label even for tr when it is focused.
    // TODO: Remove when Chrome 102 is out of date for 1 year.
    if (line.beforeNumber !== 'FILE' && line.beforeNumber !== 'LOST') {
      row.setAttribute(
        'aria-labelledby',
        [
          line.beforeNumber ? `left-button-${line.beforeNumber}` : '',
          line.afterNumber ? `right-button-${line.afterNumber}` : '',
          side === Side.LEFT && line.beforeNumber
            ? `left-content-${line.beforeNumber}`
            : '',
          side === Side.RIGHT && line.afterNumber
            ? `right-content-${line.afterNumber}`
            : '',
        ]
          .join(' ')
          .trim()
      );
    }
    row.appendChild(this.createTextEl(lineNumberEl, line, side));
    return row;
  }

  getNextContentOnSide(content: HTMLElement, side: Side): HTMLElement | null {
    let tr: HTMLElement = content.parentElement!.parentElement!;
    while ((tr = tr.nextSibling as HTMLElement)) {
      // Note that this does not work when there is a "common" chunk in the
      // diff (different content only because of whitespace). Such chunks are
      // rendered with class "add", so these rows will be skipped for the
      // 'left' side.
      // TODO: Fix this when writing a Lit component for unified diff.
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
