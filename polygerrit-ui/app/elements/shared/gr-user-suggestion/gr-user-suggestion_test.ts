/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-user-suggestion';
import {fixture, html, assert} from '@open-wc/testing';
import {GrUserSuggetion} from './gr-user-suggestion';
import {getAppContext} from '../../../services/app-context';

suite('gr-user-suggestion tests', () => {
  let element: GrUserSuggetion;

  setup(async () => {
    const flagsService = getAppContext().flagsService;
    sinon.stub(flagsService, 'isEnabled').returns(true);
    element = await fixture<GrUserSuggetion>(html`
      <gr-user-suggestion></gr-user-suggestion>
    `);
    element.code = 'Hello World';
    await element.updateComplete;
  });

  test('render', async () => {
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="header">
          <span> Suggested fix </span>
          <gr-copy-clipboard
            hideinput=""
            text="Hello World"
          ></gr-copy-clipboard>
          <gr-button class="action show-fix" secondary=""
            >Preview Fix</gr-button
          >
        </div>
        <pre><code>Hello World</code></pre>`
    );
  });
});
