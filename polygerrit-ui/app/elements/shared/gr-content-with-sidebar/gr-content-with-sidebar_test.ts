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

  test('renders right sidebar', async () => {
    element.hideSide = false;
    element.side = 'right';
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <div style="width: calc(100% - 400px);">
            <slot name="main"> </slot>
          </div>
          <div class="right sidebar-wrapper" style="width:400px;">
            <div class="resizer-wrapper">
              <div
                aria-label="Resize sidebar"
                aria-orientation="vertical"
                aria-valuenow="400"
                class="right-side resizer"
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

  test('renders left sidebar', async () => {
    element.hideSide = false;
    element.side = 'left';
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <div style="width: calc(100% - 400px); margin-left: 400px;">
            <slot name="main"> </slot>
          </div>
          <div class="left sidebar-wrapper" style="width:400px;">
            <div class="sidebar">
              <slot name="side"> </slot>
            </div>
            <div class="resizer-wrapper">
              <div
                aria-label="Resize sidebar"
                aria-orientation="vertical"
                aria-valuenow="400"
                class="left-side resizer"
                role="separator"
                tabindex="0"
              ></div>
            </div>
          </div>
        </div>
      `
    );
  });
});
