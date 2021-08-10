/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-default-editor';
import {GrDefaultEditor} from './gr-default-editor';
import {queryAndAssert} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-default-editor');

suite('gr-default-editor tests', () => {
  let element: GrDefaultEditor;

  setup(async () => {
    element = basicFixture.instantiate();
    element.fileContent = '';
    await flush();
  });

  test('fires content-change event', done => {
    const textarea = queryAndAssert<HTMLTextAreaElement>(element, '#textarea');
    element.addEventListener('content-change', e => {
      assert.equal((e as CustomEvent).detail.value, 'test');
      done();
    });
    textarea.value = 'test';
    MockInteractions.tap(textarea);
  });
});
