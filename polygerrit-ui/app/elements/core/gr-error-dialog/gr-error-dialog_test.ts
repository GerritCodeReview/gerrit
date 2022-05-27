/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import '../../../test/common-test-setup-karma';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrErrorDialog} from './gr-error-dialog';

const basicFixture = fixtureFromElement('gr-error-dialog');

suite('gr-error-dialog tests', () => {
  let element: GrErrorDialog;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
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
