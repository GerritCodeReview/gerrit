/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-account-entry';
import {GrAccountEntry} from './gr-account-entry';
import {fixture, html} from '@open-wc/testing-helpers';
import {queryAndAssert, waitUntil} from '../../../test/test-utils';
import {GrAutocomplete} from '../gr-autocomplete/gr-autocomplete';
import {PaperInputElementExt} from '../../../types/types';

suite('gr-account-entry tests', () => {
  let element: GrAccountEntry;

  setup(async () => {
    element = await fixture<GrAccountEntry>(html`
      <gr-account-entry></gr-account-entry>
    `);
    await element.updateComplete;
  });

  test('account-text-changed fired when input text changed and allowAnyInput', async () => {
    // Spy on query, as that is called when _updateSuggestions proceeds.
    const changeStub = sinon.stub();
    element.allowAnyInput = true;
    element.querySuggestions = () => Promise.resolve([]);
    element.addEventListener('account-text-changed', changeStub);
    queryAndAssert<GrAutocomplete>(element, '#input').text = 'a';
    await element.updateComplete;
    await waitUntil(() => changeStub.calledOnce);
    queryAndAssert<GrAutocomplete>(element, '#input').text = 'ab';
    await element.updateComplete;
    await waitUntil(() => changeStub.calledTwice);
  });

  test('account-text-changed not fired when input text changed without allowAnyInput', async () => {
    // Spy on query, as that is called when _updateSuggestions proceeds.
    const changeStub = sinon.stub();
    element.querySuggestions = () => Promise.resolve([]);
    element.addEventListener('account-text-changed', changeStub);
    queryAndAssert<GrAutocomplete>(element, '#input').text = 'a';
    await element.updateComplete;
    assert.isFalse(changeStub.called);
  });

  test('setText', async () => {
    // Stub on query, as that is called when _updateSuggestions proceeds.
    const suggestStub = sinon.stub(
      queryAndAssert<GrAutocomplete>(element, '#input'),
      'query'
    );
    element.setText('test text');
    await element.updateComplete;

    const input = queryAndAssert<GrAutocomplete>(element, '#input');
    assert.equal(
      queryAndAssert<PaperInputElementExt>(input, '#input').value,
      'test text'
    );
    assert.isFalse(suggestStub.called);
  });
});
