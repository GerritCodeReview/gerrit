/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
      if (group.moveDetails.changed) {
        sectionEl.classList.add('changed');
      }
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
    col = createElementDiff('col', 'right');
    col.setAttribute('width', lineNumberWidth.toString());
    colgroup.appendChild(col);

    colgroup.appendChild(createElementDiff('col', 'sign right'));

    // Add right-side content.
    colgroup.appendChild(createElementDiff('col', 'right'));

    outputEl.appendChild(colgroup);
  }

  private createRow(leftLine: GrDiffLine, rightLine: GrDiffLine) {
    const row = createElementDiff('tr');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', leftLine.type);
    row.setAttribute('right-type', rightLine.type);
    // TabIndex makes screen reader read a row when navigating with j/k
    row.tabIndex = -1;
    // Before Chrome 102, Chrome was able to compute a11y label from children
    // content. Now Chrome 102 and Firefox are not computing a11y label because
    // tr is not expected to have aria label. Adding aria role button is
    // pushing browser to compute aria even for tr. This can be removed, once
    // browsers will again compute a11y label even for tr when it is focused.
    // TODO: Remove when Chrome 102 is out of date for 1 year.
    row.setAttribute(
      'aria-labelledby',
      [
        leftLine.beforeNumber ? `left-button-${leftLine.beforeNumber}` : '',
        leftLine.beforeNumber ? `left-content-${leftLine.beforeNumber}` : '',
        rightLine.afterNumber ? `right-button-${rightLine.afterNumber}` : '',
        rightLine.afterNumber ? `right-content-${rightLine.afterNumber}` : '',
      ]
        .join(' ')
        .trim()
    );

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
