/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './gr-content-with-sidebar';
import {GrContentWithSidebar} from './gr-content-with-sidebar';

suite('gr-content-with-sidebar tests', () => {
  let element: GrContentWithSidebar;

  setup(async () => {
    element = await fixture<GrContentWithSidebar>(
      html`<gr-content-with-sidebar></gr-content-with-sidebar>`
    );
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
              <div
                aria-label="Resize sidebar"
                aria-orientation="vertical"
                aria-valuenow="300"
                class="resizer"
                role="separator"
                tabindex="0"
              ></div>
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
