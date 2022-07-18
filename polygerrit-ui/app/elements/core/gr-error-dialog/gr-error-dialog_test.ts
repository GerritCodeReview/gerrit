/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import '../../../test/common-test-setup-karma';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrErrorDialog} from './gr-error-dialog';
import './gr-error-dialog';

suite('gr-error-dialog tests', () => {
  let element: GrErrorDialog;

  setup(async () => {
    element = await fixture(html`<gr-error-dialog></gr-error-dialog>`);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-dialog
        cancel-label=""
        confirm-label="Dismiss"
        confirm-on-enter=""
        id="dialog"
        role="dialog"
      >
        <div class="header" slot="header">An error occurred</div>
        <div class="main" slot="main"></div>
      </gr-dialog>
    `);
  });

  test('dismiss tap fires event', async () => {
    const dismissCalled = mockPromise();
    element.addEventListener('dismiss', () => dismissCalled.resolve());
    MockInteractions.tap(
      queryAndAssert<GrDialog>(element, '#dialog').confirmButton!
    );
    await dismissCalled;
  });
});
