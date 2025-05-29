/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-default-editor';
import {GrDefaultEditor} from './gr-default-editor';
import {
  mockPromise,
  queryAndAssert,
  waitEventLoop,
} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-default-editor tests', () => {
  let element: GrDefaultEditor;

  setup(async () => {
    element = await fixture(html`<gr-default-editor></gr-default-editor>`);
    element.fileContent = '';
    await waitEventLoop();
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <textarea id="textarea"></textarea> '
    );
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
