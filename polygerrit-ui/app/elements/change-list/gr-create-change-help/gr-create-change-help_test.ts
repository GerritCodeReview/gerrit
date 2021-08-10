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

import '../../../test/common-test-setup-karma';
import './gr-create-change-help';
import {GrCreateChangeHelp} from './gr-create-change-help';
import {queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-create-change-help');

suite('gr-create-change-help tests', () => {
  let element: GrCreateChangeHelp;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('Create change tap', done => {
    element.addEventListener('create-tap', () => done());
    MockInteractions.tap(queryAndAssert<GrButton>(element, 'gr-button'));
  });
});
