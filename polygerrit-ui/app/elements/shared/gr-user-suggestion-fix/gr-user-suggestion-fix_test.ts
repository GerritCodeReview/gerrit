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
          <div class="title">
            <span>Suggested edit</span>
            <a
              href="https://gerrit-review.googlesource.com/Documentation/user-suggest-edits.html"
              rel="noopener noreferrer"
              target="_blank"
              ><gr-icon icon="help" title="read documentation"></gr-icon
            ></a>
          </div>
          <div class="copyButton">
            <gr-copy-clipboard
              hideinput=""
              multiline=""
              text="Hello World"
              copytargetname="Suggested edit"
            ></gr-copy-clipboard>
          </div>
          <div>
            <gr-button
              aria-disabled="false"
              class="action show-fix"
              secondary=""
              role="button"
              tabindex="0"
              flatten=""
              >Show edit</gr-button
            >
          </div>
        </div>
        <code>Hello World</code>`
    );
  });
});
