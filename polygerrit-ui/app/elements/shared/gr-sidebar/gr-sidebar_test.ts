/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-sidebar';
import {assert, fixture, html} from '@open-wc/testing';
import {GrSidebar} from './gr-sidebar';

suite('gr-sidebar tests', () => {
  let element: GrSidebar;

  setup(async () => {
    element = await fixture<GrSidebar>(html`<gr-sidebar></gr-sidebar>`);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <aside>
          <slot></slot>
        </aside>
      `
    );
  });
});
