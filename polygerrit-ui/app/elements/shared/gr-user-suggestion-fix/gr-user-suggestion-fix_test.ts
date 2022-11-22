/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-user-suggestion-fix';
import {fixture, html, assert} from '@open-wc/testing';
import {GrUserSuggetionFix} from './gr-user-suggestion-fix';
import {getAppContext} from '../../../services/app-context';

suite('gr-user-suggestion-fix tests', () => {
  let element: GrUserSuggetionFix;

  setup(async () => {
    const flagsService = getAppContext().flagsService;
    sinon.stub(flagsService, 'isEnabled').returns(true);
    element = await fixture<GrUserSuggetionFix>(html`
      <gr-user-suggestion-fix>Hello World</gr-user-suggestion-fix>
    `);
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
