/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import '../../../test/common-test-setup-karma';
import {queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-action-bar';
import type {GrChangeListActionBar} from './gr-change-list-action-bar';

suite('gr-change-list-action-bar tests', () => {
  let element: GrChangeListActionBar;
  setup(async () => {
    element = await fixture(
      html`<gr-change-list-action-bar></gr-change-list-action-bar>`
    );
    await element.updateComplete;
  });

  test('renders all buttons', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <td>
        <div class="container">
          <gr-button aria-disabled="false" role="button" tabindex="0"
            >abandon</gr-button
          >
        </div>
      </td>
    `);
  });

  test('abanonded clicked', () => {
    const consoleLogSpy = sinon.spy(console, 'log');
    const button = queryAndAssert<GrButton>(element, 'gr-button');

    button.click();

    assert.isTrue(consoleLogSpy.calledWith('abandon clicked'));
  });
});
