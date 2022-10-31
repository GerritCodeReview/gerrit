/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
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
              class="above contextBackground gr-diff side-by-side style-scope"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="blame gr-diff style-scope" data-line-number="0"></td>
              <td class="contextLineNum gr-diff style-scope"></td>
              <td class="gr-diff style-scope"></td>
              <td class="contextLineNum gr-diff style-scope"></td>
              <td class="gr-diff style-scope"></td>
            </tr>
            <tr class="dividerRow gr-diff show-both style-scope">
              <td class="blame gr-diff style-scope" data-line-number="0"></td>
              <td class="gr-diff style-scope"></td>
              <td class="dividerCell gr-diff style-scope" colspan="3">
                <gr-context-controls showconfig="both"> </gr-context-controls>
              </td>
            </tr>
            <tr
              class="below contextBackground gr-diff side-by-side style-scope"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="blame gr-diff style-scope" data-line-number="0"></td>
              <td class="contextLineNum gr-diff style-scope"></td>
              <td class="gr-diff style-scope"></td>
              <td class="contextLineNum gr-diff style-scope"></td>
              <td class="gr-diff style-scope"></td>
            </tr>
          </tbody>
        </table>
      `
    );
  });
});
