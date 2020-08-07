/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import {GrDiffBuilderUnified} from './gr-diff-builder-unified';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/common';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';

export class GrDiffBuilderBinary extends GrDiffBuilderUnified {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement
  ) {
    super(diff, prefs, outputEl);
  }

  buildSectionElement(): HTMLElement {
    const section = this._createElement('tbody', 'binary-diff');
    const line = new GrDiffLine(GrDiffLineType.BOTH, 'FILE', 'FILE');
    const fileRow = this._createRow(line);
    const contentTd = fileRow.querySelector('td.both.file')!;
    contentTd.textContent = ' Difference in binary files';
    section.appendChild(fileRow);
    return section;
  }
}
