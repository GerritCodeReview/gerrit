/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-copy-clipboard';
import {GrCopyClipboard} from './gr-copy-clipboard';
import {queryAndAssert} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-copy-clipboard');

suite('gr-copy-clipboard tests', () => {
  let element: GrCopyClipboard;

  setup(async () => {
    element = basicFixture.instantiate();
    element.text = `git fetch http://gerrit@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`;
    await flush();
  });

  test('copy to clipboard', () => {
    const clipboardSpy = sinon.spy(navigator.clipboard, 'writeText');
    const copyBtn = queryAndAssert(element, '.copyToClipboard');
    MockInteractions.click(copyBtn);
    assert.isTrue(clipboardSpy.called);
  });

  test('focusOnCopy', () => {
    element.focusOnCopy();
    const activeElement = element.shadowRoot!.activeElement;
    const button = queryAndAssert(element, '.copyToClipboard');
    assert.deepEqual(activeElement, button);
  });

  test('_handleInputClick', () => {
    // iron-input as parent should never be hidden as copy won't work
    // on nested hidden elements
    const ironInputElement = queryAndAssert(element, 'iron-input');
    assert.notEqual(getComputedStyle(ironInputElement).display, 'none');

    const inputElement = queryAndAssert<HTMLInputElement>(element, 'input');
    MockInteractions.tap(inputElement);
    assert.equal(inputElement.selectionStart, 0);
    assert.equal(inputElement.selectionEnd, element.text!.length - 1);
  });

  test('hideInput', async () => {
    // iron-input as parent should never be hidden as copy won't work
    // on nested hidden elements
    const ironInputElement = queryAndAssert(element, 'iron-input');
    assert.notEqual(getComputedStyle(ironInputElement).display, 'none');

    const input = queryAndAssert(element, 'input');
    assert.notEqual(getComputedStyle(input).display, 'none');
    element.hideInput = true;
    await flush();
    assert.equal(getComputedStyle(input).display, 'none');
  });

  test('stop events propagation', () => {
    const divParent = document.createElement('div');
    divParent.appendChild(element);
    const clickStub = sinon.stub();
    divParent.addEventListener('click', clickStub);
    const copyBtn = queryAndAssert(element, '.copyToClipboard');
    MockInteractions.tap(copyBtn);
    assert.isFalse(clickStub.called);
  });
});
