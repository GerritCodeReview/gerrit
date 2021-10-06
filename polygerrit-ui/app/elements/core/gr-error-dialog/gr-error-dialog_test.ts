/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    await flush();
  });

  test('dismiss tap fires event', async () => {
    const dismissCalled = mockPromise();
    element.addEventListener('dismiss', () => dismissCalled.resolve());
    MockInteractions.tap(
      (queryAndAssert(element, '#dialog') as GrDialog).confirmButton!
    );
    await dismissCalled;
  });
});
