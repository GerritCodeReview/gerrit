/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-create-change-help';
import {GrCreateChangeHelp} from './gr-create-change-help';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-create-change-help');

suite('gr-create-change-help tests', () => {
  let element: GrCreateChangeHelp;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('Create change tap', async () => {
    const promise = mockPromise();
    element.addEventListener('create-tap', () => promise.resolve());
    MockInteractions.tap(queryAndAssert<GrButton>(element, 'gr-button'));
    await promise;
  });
});
