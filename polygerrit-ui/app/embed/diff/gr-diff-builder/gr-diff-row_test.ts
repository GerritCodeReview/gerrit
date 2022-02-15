/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-diff-row';
import {GrDiffRow} from './gr-diff-row';
import {html} from 'lit';
import {fixture} from '@open-wc/testing-helpers';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrDiffLineType} from '../../../api/diff';

suite('gr-diff-row test', () => {
  let element: GrDiffRow;

  setup(async () => {
    element = await fixture<GrDiffRow>(html`<gr-diff-row></gr-diff-row>`);
    element.isVisible = true;
    element.addTableWrapperForTesting = true;
    await element.updateComplete;
  });

  test('both', async () => {
    const line = new GrDiffLine(GrDiffLineType.BOTH, 1, 1);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = line;
    await element.updateComplete;
    expect(element).lightDom.to.equal(/* HTML */ `
      <table>
        <tbody>
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
            <td class="both content gr-diff left no-intraline-info style-scope">
              <div
                aria-label="lorem ipsum"
                class="contentText gr-diff left style-scope"
                data-side="left"
              >
                lorem ipsum
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
                aria-label="lorem ipsum"
                class="contentText gr-diff right style-scope"
                data-side="right"
              >
                lorem ipsum
              </div>
              <div class="thread-group" data-side="right">
                <slot name="right-1"> </slot>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    `);
  });

  test('add', async () => {
    const line = new GrDiffLine(GrDiffLineType.ADD, 0, 1);
    line.text = 'lorem ipsum';
    element.left = new GrDiffLine(GrDiffLineType.BLANK);
    element.right = line;
    await element.updateComplete;
    expect(element).lightDom.to.equal(/* HTML */ `
      <table>
        <tbody>
          <tr
            class="diff-row gr-diff side-by-side style-scope"
            left-type="blank"
            right-type="add"
            tabindex="-1"
          >
            <td class="blame gr-diff style-scope" data-line-number="0"></td>
            <td class="gr-diff left style-scope"></td>
            <td class="blank gr-diff left no-intraline-info style-scope">
              <div
                aria-label=""
                class="contentText gr-diff left style-scope"
                data-side="left"
              ></div>
            </td>
            <td class="gr-diff lineNum right style-scope" data-value="1">
              <button
                aria-label="1 added"
                class="gr-diff lineNumButton right style-scope"
                data-value="1"
                tabindex="-1"
              >
                1
              </button>
            </td>
            <td class="add content gr-diff no-intraline-info right style-scope">
              <div
                aria-label="lorem ipsum"
                class="contentText gr-diff right style-scope"
                data-side="right"
              >
                lorem ipsum
              </div>
              <div class="thread-group" data-side="right">
                <slot name="right-1"> </slot>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    `);
  });

  test('remove', async () => {
    const line = new GrDiffLine(GrDiffLineType.REMOVE, 1, 0);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = new GrDiffLine(GrDiffLineType.BLANK);
    await element.updateComplete;
    expect(element).lightDom.to.equal(/* HTML */ `
      <table>
        <tbody>
          <tr
            class="diff-row gr-diff side-by-side style-scope"
            left-type="remove"
            right-type="blank"
            tabindex="-1"
          >
            <td class="blame gr-diff style-scope" data-line-number="1"></td>
            <td class="gr-diff left lineNum style-scope" data-value="1">
              <button
                aria-label="1 removed"
                class="gr-diff left lineNumButton style-scope"
                data-value="1"
                tabindex="-1"
              >
                1
              </button>
            </td>
            <td
              class="content gr-diff left no-intraline-info remove style-scope"
            >
              <div
                aria-label="lorem ipsum"
                class="contentText gr-diff left style-scope"
                data-side="left"
              >
                lorem ipsum
              </div>
              <div class="thread-group" data-side="left">
                <slot name="left-1"> </slot>
              </div>
            </td>
            <td class="gr-diff right style-scope"></td>
            <td class="blank gr-diff no-intraline-info right style-scope">
              <div
                aria-label=""
                class="contentText gr-diff right style-scope"
                data-side="right"
              ></div>
            </td>
          </tr>
        </tbody>
      </table>
    `);
  });
});
