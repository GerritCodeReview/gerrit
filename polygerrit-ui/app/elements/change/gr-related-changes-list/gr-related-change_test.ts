/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {createParsedChange} from '../../../test/test-data-generators';
import './gr-related-change';
import {GrRelatedChange} from './gr-related-change';

suite('gr-related-change', () => {
  let element: GrRelatedChange;

  setup(async () => {
    element = await fixture<GrRelatedChange>(
      html`<gr-related-change
        .change=${createParsedChange()}
        href="/c/test-project/+/42"
        label="Test subject"
      ></gr-related-change>`
    );
  });

  test('render', async () => {
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="changeContainer">
          <a href="/c/test-project/+/42" aria-label="Test subject">
            <slot name="name"></slot>
          </a>
          <slot name="extra"></slot>
        </div>
      `
    );
  });
});
