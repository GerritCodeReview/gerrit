/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrDiffBuilderLit} from './gr-diff-builder-lit';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {createElementDiff} from '../gr-diff/gr-diff-utils';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {html, render} from 'lit';
import {BinaryDiffBuilder} from './gr-diff-builder';

export class GrDiffBuilderBinary
  extends GrDiffBuilderLit
  implements BinaryDiffBuilder
{
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
