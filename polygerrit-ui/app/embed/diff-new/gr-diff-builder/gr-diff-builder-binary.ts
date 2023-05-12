/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrDiffBuilder} from './gr-diff-builder';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {createElementDiff} from '../gr-diff/gr-diff-utils';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {html, render} from 'lit';
import {FILE} from '../../../api/diff';

export class GrDiffBuilderBinary extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement
  ) {
    super(diff, prefs, outputEl);
  }

  override buildSectionElement(group: GrDiffGroup): HTMLElement {
    const section = createElementDiff('tbody', 'binary-diff');
    // Do not create a diff row for LOST.
    if (group.lines[0].beforeNumber !== FILE) return section;
    return super.buildSectionElement(group);
  }

  public renderBinaryDiff() {
    render(
      html`
        <tbody class="gr-diff binary-diff">
          <tr class="gr-diff">
            <td colspan="5" class="gr-diff">
              <span>Difference in binary files</span>
            </td>
          </tr>
        </tbody>
      `,
      this.outputEl
    );
  }
}
