/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrDiffBuilderUnified} from './gr-diff-builder-unified';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {queryAndAssert} from '../../../utils/common-util';
import {createElementDiff} from '../gr-diff/gr-diff-utils';

export class GrDiffBuilderBinary extends GrDiffBuilderUnified {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement
  ) {
    super(diff, prefs, outputEl);
  }

  override buildSectionElement(): HTMLElement {
    const section = createElementDiff('tbody', 'binary-diff');
    const line = new GrDiffLine(GrDiffLineType.BOTH, 'FILE', 'FILE');
    const fileRow = this.createRow(line);
    const contentTd = queryAndAssert(fileRow, 'td.both.file')!;
    contentTd.textContent = ' Difference in binary files';
    section.appendChild(fileRow);
    return section;
  }
}
