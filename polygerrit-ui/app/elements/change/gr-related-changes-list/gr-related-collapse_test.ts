/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-related-collapse';
import {GrRelatedCollapse} from './gr-related-collapse';

suite('gr-related-collapse', () => {
  let element: GrRelatedCollapse;

  setup(async () => {
    element = await fixture<GrRelatedCollapse>(
      html`<gr-related-collapse></gr-related-collapse>`
    );
  });

  test('render', async () => {
    element.name = 'Related Changes';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <h3 class="heading-3 title">Related Changes</h3>
        </div>
        <div>
          <slot> </slot>
        </div>
      `
    );
  });
});
