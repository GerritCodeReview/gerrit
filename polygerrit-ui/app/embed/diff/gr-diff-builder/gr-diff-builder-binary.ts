/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrDiffBuilderUnified} from './gr-diff-builder-unified';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {createElementDiff} from '../gr-diff/gr-diff-utils';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrDiffLineType} from '../../../api/diff';
import {queryAndAssert} from '../../../utils/common-util';
import {html, render} from 'lit';

export class GrDiffBuilderBinary extends GrDiffBuilderUnified {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement
  ) {
    super(diff, prefs, outputEl);
  }

  override buildSectionElement(group: GrDiffGroup): HTMLElement {
    const section = createElementDiff('tbody', 'binary-diff');
    // Do not create a diff row for 'LOST'.
    if (group.lines[0].beforeNumber !== 'FILE') return section;

    const line = new GrDiffLine(GrDiffLineType.BOTH, 'FILE', 'FILE');
    const fileRow = this.createRow(line);
    const contentTd = queryAndAssert<HTMLTableCellElement>(
      fileRow,
      'td.both.file'
    )!;
    const div = document.createElement('div');
    render(html`<span>Difference in binary files</span>`, div);
    contentTd.insertBefore(div, contentTd.firstChild);
    section.appendChild(fileRow);
    return section;
  }
}
