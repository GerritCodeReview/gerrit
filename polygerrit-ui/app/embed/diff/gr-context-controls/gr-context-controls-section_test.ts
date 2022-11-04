/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-context-controls-section';
import {GrContextControlsSection} from './gr-context-controls-section';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-context-controls-section test', () => {
  let element: GrContextControlsSection;

  setup(async () => {
    element = await fixture<GrContextControlsSection>(
      html`<gr-context-controls-section></gr-context-controls-section>`
    );
    element.addTableWrapperForTesting = true;
    await element.updateComplete;
  });

  test('render: normal with showAbove and showBelow', async () => {
    element.showAbove = true;
    element.showBelow = true;
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr
              class="above contextBackground gr-diff side-by-side"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="blame gr-diff" data-line-number="0"></td>
              <td class="contextLineNum gr-diff"></td>
              <td class="gr-diff sign"></td>
              <td class="gr-diff"></td>
              <td class="contextLineNum gr-diff"></td>
              <td class="gr-diff sign"></td>
              <td class="gr-diff"></td>
            </tr>
            <tr class="dividerRow gr-diff show-both">
              <td class="blame gr-diff" data-line-number="0"></td>
              <td class="gr-diff"></td>
              <td class="dividerCell gr-diff" colspan="5">
                <gr-context-controls class="gr-diff" showconfig="both">
                </gr-context-controls>
              </td>
            </tr>
            <tr
              class="below contextBackground gr-diff side-by-side"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="blame gr-diff" data-line-number="0"></td>
              <td class="contextLineNum gr-diff"></td>
              <td class="gr-diff sign"></td>
              <td class="gr-diff"></td>
              <td class="contextLineNum gr-diff"></td>
              <td class="gr-diff sign"></td>
              <td class="gr-diff"></td>
            </tr>
          </tbody>
        </table>
      `
    );
  });
});
