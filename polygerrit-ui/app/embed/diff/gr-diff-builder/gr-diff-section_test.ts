/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-diff-section';
import {GrDiffSection} from './gr-diff-section';
import {html} from 'lit';
import {fixture} from '@open-wc/testing-helpers';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrDiffLineType} from '../../../api/diff';

suite('gr-diff-section test', () => {
  let element: GrDiffSection;

  setup(async () => {
    element = await fixture<GrDiffSection>(
      html`<gr-diff-section></gr-diff-section>`
    );
    element.addTableWrapperForTesting = true;
    element.isVisible = true;
    await element.updateComplete;
  });

  test('3 normal unchanged rows', async () => {
    const lines = [
      new GrDiffLine(GrDiffLineType.BOTH, 1, 1),
      new GrDiffLine(GrDiffLineType.BOTH, 1, 1),
      new GrDiffLine(GrDiffLineType.BOTH, 1, 1),
    ];
    lines[0].text = 'asdf';
    lines[1].text = 'qwer';
    lines[2].text = 'zxcv';
    const group = new GrDiffGroup({type: GrDiffGroupType.BOTH, lines});
    element.group = group;
    await element.updateComplete;
    expect(element).dom.to.equal(/* HTML */ `
      <gr-diff-section>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <table>
          <tbody class="both gr-diff section style-scope">
            <tr
              class="diff-row gr-diff side-by-side style-scope"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff style-scope" data-line-number="1"></td>
              <td class="gr-diff left lineNum style-scope" data-value="1">
                <button
                  class="gr-diff left lineNumButton style-scope"
                  data-value="1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td
                class="both content gr-diff left no-intraline-info style-scope"
              >
                <div
                  aria-label="asdf"
                  class="contentText gr-diff left style-scope"
                  data-side="left"
                >
                  asdf
                </div>
                <div class="thread-group" data-side="left">
                  <slot name="left-1"> </slot>
                </div>
              </td>
              <td class="gr-diff lineNum right style-scope" data-value="1">
                <button
                  class="gr-diff lineNumButton right style-scope"
                  data-value="1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td
                class="both content gr-diff no-intraline-info right style-scope"
              >
                <div
                  aria-label="asdf"
                  class="contentText gr-diff right style-scope"
                  data-side="right"
                >
                  asdf
                </div>
                <div class="thread-group" data-side="right">
                  <slot name="right-1"> </slot>
                </div>
              </td>
            </tr>
            <tr
              class="diff-row gr-diff side-by-side style-scope"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff style-scope" data-line-number="1"></td>
              <td class="gr-diff left lineNum style-scope" data-value="1">
                <button
                  class="gr-diff left lineNumButton style-scope"
                  data-value="1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td
                class="both content gr-diff left no-intraline-info style-scope"
              >
                <div
                  aria-label="qwer"
                  class="contentText gr-diff left style-scope"
                  data-side="left"
                >
                  qwer
                </div>
                <div class="thread-group" data-side="left">
                  <slot name="left-1"> </slot>
                </div>
              </td>
              <td class="gr-diff lineNum right style-scope" data-value="1">
                <button
                  class="gr-diff lineNumButton right style-scope"
                  data-value="1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td
                class="both content gr-diff no-intraline-info right style-scope"
              >
                <div
                  aria-label="qwer"
                  class="contentText gr-diff right style-scope"
                  data-side="right"
                >
                  qwer
                </div>
                <div class="thread-group" data-side="right">
                  <slot name="right-1"> </slot>
                </div>
              </td>
            </tr>
            <tr
              class="diff-row gr-diff side-by-side style-scope"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff style-scope" data-line-number="1"></td>
              <td class="gr-diff left lineNum style-scope" data-value="1">
                <button
                  class="gr-diff left lineNumButton style-scope"
                  data-value="1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td
                class="both content gr-diff left no-intraline-info style-scope"
              >
                <div
                  aria-label="zxcv"
                  class="contentText gr-diff left style-scope"
                  data-side="left"
                >
                  zxcv
                </div>
                <div class="thread-group" data-side="left">
                  <slot name="left-1"> </slot>
                </div>
              </td>
              <td class="gr-diff lineNum right style-scope" data-value="1">
                <button
                  class="gr-diff lineNumButton right style-scope"
                  data-value="1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td
                class="both content gr-diff no-intraline-info right style-scope"
              >
                <div
                  aria-label="zxcv"
                  class="contentText gr-diff right style-scope"
                  data-side="right"
                >
                  zxcv
                </div>
                <div class="thread-group" data-side="right">
                  <slot name="right-1"> </slot>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </gr-diff-section>
    `);
  });
});
