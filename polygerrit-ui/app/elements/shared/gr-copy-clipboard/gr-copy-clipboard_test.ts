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
import {fixture, html} from '@open-wc/testing';

suite('gr-copy-clipboard tests', () => {
  let element: GrCopyClipboard;

  setup(async () => {
    element = await fixture(html`<gr-copy-clipboard></gr-copy-clipboard>`);
    element.text = `git fetch http://gerrit@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`;
    await flush();
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="text">
          <iron-input class="copyText">
            <input
              id="input"
              is="iron-input"
              part="text-container-style"
              readonly=""
              type="text"
            />
          </iron-input>
          <gr-tooltip-content>
            <gr-button
              aria-disabled="false"
              aria-label="Click to copy to clipboard"
              class="copyToClipboard"
              id="copy-clipboard-button"
              link=""
              role="button"
              tabindex="0"
            >
              <div>
                <gr-icon icon="content_copy" id="icon" small></gr-icon>
              </div>
            </gr-button>
          </gr-tooltip-content>
        </div>
      `
    );
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
