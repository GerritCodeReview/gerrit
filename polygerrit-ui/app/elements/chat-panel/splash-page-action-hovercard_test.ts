/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './splash-page-action-hovercard';
import {SplashPageActionHovercard} from './splash-page-action-hovercard';
import {Action} from '../../api/ai-code-review';

suite('splash-page-action-hovercard tests', () => {
  let element: SplashPageActionHovercard;

  setup(async () => {
    element = await fixture<SplashPageActionHovercard>(
      html`<splash-page-action-hovercard></splash-page-action-hovercard>`
    );
  });

  test('renders with action', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      hover_text: 'Test hover',
    };
    element.action = action;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <div class="title">Test Action</div>
          <div class="details">Test hover</div>
        </div>
      `
    );
  });
});
