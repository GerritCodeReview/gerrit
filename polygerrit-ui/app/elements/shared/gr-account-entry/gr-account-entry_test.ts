/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-account-entry';
import {GrAccountEntry} from './gr-account-entry';
import {fixture, html, assert} from '@open-wc/testing';
import {queryAndAssert, waitUntil} from '../../../test/test-utils';
import {GrAutocomplete} from '../gr-autocomplete/gr-autocomplete';
import {PaperInputElement} from '@polymer/paper-input/paper-input';

suite('gr-account-entry tests', () => {
  let element: GrAccountEntry;

  setup(async () => {
    element = await fixture<GrAccountEntry>(html`
      <gr-account-entry></gr-account-entry>
    `);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-autocomplete
          allow-non-suggested-values="false"
          clear-on-commit=""
          id="input"
          warn-uncommitted=""
        >
        </gr-autocomplete>
      `
    );
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
      queryAndAssert<PaperInputElement>(input, '#input').value,
      'test text'
    );
    assert.isFalse(suggestStub.called);
  });
});
