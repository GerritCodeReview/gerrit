/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-section';
import {GrDiffSection} from './gr-diff-section';
import {fixture, html, assert} from '@open-wc/testing';
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
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <table>
          <tbody class="both gr-diff section">
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row gr-diff side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff" data-line-number="1"></td>
              <td class="gr-diff left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff left no-intraline-info sign"></td>
              <td class="both content gr-diff left no-intraline-info">
                <div
                  class="contentText gr-diff"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text> </gr-diff-text>
                </div>
                <div class="thread-group" data-side="left">
                  <slot name="left-1"> </slot>
                </div>
              </td>
              <td class="gr-diff lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff no-intraline-info right sign"></td>
              <td class="both content gr-diff no-intraline-info right">
                <div
                  class="contentText gr-diff"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text> </gr-diff-text>
                </div>
                <div class="thread-group" data-side="right">
                  <slot name="right-1"> </slot>
                </div>
              </td>
            </tr>
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row gr-diff side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff" data-line-number="1"></td>
              <td class="gr-diff left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff left no-intraline-info sign"></td>
              <td class="both content gr-diff left no-intraline-info">
                <div
                  class="contentText gr-diff"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text> </gr-diff-text>
                </div>
                <div class="thread-group" data-side="left">
                  <slot name="left-1"> </slot>
                </div>
              </td>
              <td class="gr-diff lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff no-intraline-info right sign"></td>
              <td class="both content gr-diff no-intraline-info right">
                <div
                  class="contentText gr-diff"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text> </gr-diff-text>
                </div>
                <div class="thread-group" data-side="right">
                  <slot name="right-1"> </slot>
                </div>
              </td>
            </tr>
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row gr-diff side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff" data-line-number="1"></td>
              <td class="gr-diff left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff left no-intraline-info sign"></td>
              <td class="both content gr-diff left no-intraline-info">
                <div
                  class="contentText gr-diff"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text> </gr-diff-text>
                </div>
                <div class="thread-group" data-side="left">
                  <slot name="left-1"> </slot>
                </div>
              </td>
              <td class="gr-diff lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff no-intraline-info right sign"></td>
              <td class="both content gr-diff no-intraline-info right">
                <div
                  class="contentText gr-diff"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text> </gr-diff-text>
                </div>
                <div class="thread-group" data-side="right">
                  <slot name="right-1"> </slot>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      `
    );
  });
});
