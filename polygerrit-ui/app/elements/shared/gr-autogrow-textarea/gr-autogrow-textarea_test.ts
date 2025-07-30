/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrAutogrowTextarea} from './gr-autogrow-textarea';
import './gr-autogrow-textarea';

suite('gr-autogrow-textarea tests', () => {
  let element: GrAutogrowTextarea;

  setup(async () => {
    element = await fixture(
      html`<gr-autogrow-textarea></gr-autogrow-textarea>`
    );
  });

  test('render', async () => {
    // prettier and shadowDom string disagree about wrapping in <p> tag.
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <div
        aria-hidden="true"
        class="mirror-text"
        id="mirror"
      >
      </div>
      <div class="textarea-container">
        <textarea
          aria-disabled="false"
          aria-multiline="true"
          autocapitalize=""
          autocomplete="off"
          class="editableTextArea"
          rows="1"
          spellcheck="true"
        >
        </textarea>
      </div>
    `
    );
  });
});
