/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-default-editor';
import {GrDefaultEditor} from './gr-default-editor';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-default-editor');

suite('gr-default-editor tests', () => {
  let element: GrDefaultEditor;

  setup(async () => {
    element = basicFixture.instantiate();
    element.fileContent = '';
    await flush();
  });

  test('fires content-change event', async () => {
    const textarea = queryAndAssert<HTMLTextAreaElement>(element, '#textarea');
    const promise = mockPromise();
    element.addEventListener('content-change', e => {
      assert.equal((e as CustomEvent).detail.value, 'test');
      promise.resolve();
    });
    textarea.value = 'test';
    textarea.dispatchEvent(new Event('input', {bubbles: true, composed: true}));
    await promise;
  });
});
