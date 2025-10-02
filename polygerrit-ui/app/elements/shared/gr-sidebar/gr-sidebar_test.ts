/**
 * @license
 * Copyright 2025 Google LLC
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

  test('renders no sidebar', async () => {
    element.hideSide = true;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <div style="width:calc(100% - 0px);">
            <slot name="main"></slot>
          </div>
        </div>
      `
    );
  });

  test('renders sidebar', async () => {
    element.hideSide = false;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <div style="width: calc(100% - 300px);">
            <slot name="main"> </slot>
          </div>
          <div class="sidebar-wrapper" style="width:300px;">
            <div class="resizer-wrapper">
              <div class="resizer"></div>
            </div>
            <div class="sidebar">
              <slot name="side"> </slot>
            </div>
          </div>
        </div>
      `
    );
  });
});
