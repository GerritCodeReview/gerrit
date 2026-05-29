/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  DIProviderElement,
  wrapInProvider,
} from '../../../models/di-provider-element';
import '../../../test/common-test-setup';
import {
  createContextGroup,
  createDiff,
} from '../../../test/test-data-generators';
import {DiffModel, diffModelToken} from '../gr-diff-model/gr-diff-model';
import './gr-context-controls-section';
import {GrContextControlsSection} from './gr-context-controls-section';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-context-controls-section test', () => {
  let element: GrContextControlsSection;

  setup(async () => {
    const diffModel = new DiffModel(document);
    element = (
      await fixture<DIProviderElement>(
        wrapInProvider(
          html`<gr-context-controls-section></gr-context-controls-section>`,
          diffModelToken,
          diffModel
        )
      )
    ).querySelector<GrContextControlsSection>('gr-context-controls-section')!;
    await element.updateComplete;

    diffModel.updateState({diff: createDiff()});
    element.addTableWrapperForTesting = true;
    await element.updateComplete;
  });

  test('render nothing, if group is not set', async () => {
    assert.lightDom.equal(element, '');
  });

  test('render above and below', async () => {
    element.group = createContextGroup({offset: 10, count: 10});
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr
              class="above contextBackground side-by-side"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="contextLineNum"></td>
              <td></td>
              <td class="contextLineNum"></td>
              <td></td>
            </tr>
            <tr class="dividerRow show-both">
              <td class="dividerCell" colspan="4">
                <gr-context-controls showconfig="both"> </gr-context-controls>
              </td>
            </tr>
            <tr
              class="below contextBackground side-by-side"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="contextLineNum"></td>
              <td></td>
              <td class="contextLineNum"></td>
              <td></td>
            </tr>
          </tbody>
        </table>
      `
    );
  });

  test('render above only', async () => {
    element.group = createContextGroup({offset: 35, count: 10});
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr
              class="above contextBackground side-by-side"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="contextLineNum"></td>
              <td></td>
              <td class="contextLineNum"></td>
              <td></td>
            </tr>
            <tr class="dividerRow show-above">
              <td class="dividerCell" colspan="4">
                <gr-context-controls showconfig="above"> </gr-context-controls>
              </td>
            </tr>
          </tbody>
        </table>
      `
    );
  });

  test('render below only', async () => {
    element.group = createContextGroup({offset: 0, count: 10});
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr class="dividerRow show-below">
              <td class="dividerCell" colspan="4">
                <gr-context-controls showconfig="below"> </gr-context-controls>
              </td>
            </tr>
            <tr
              class="below contextBackground side-by-side"
              left-type="contextControl"
              right-type="contextControl"
            >
              <td class="contextLineNum"></td>
              <td></td>
              <td class="contextLineNum"></td>
              <td></td>
            </tr>
          </tbody>
        </table>
      `
    );
  });
});
