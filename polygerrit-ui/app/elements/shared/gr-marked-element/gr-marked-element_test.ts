/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-marked-element';
import {assert, fixture, html} from '@open-wc/testing';
import {GrMarkedElement} from './gr-marked-element';

suite('gr-app-element tests', () => {
  let element: GrMarkedElement;

  setup(async () => {
    element = await fixture<GrMarkedElement>(
      html`<gr-marked-element></gr-marked-element>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <slot name="markdown-html">
          <div id="content" slot="markdown-html"></div>
        </slot>
      `
    );
  });
});
