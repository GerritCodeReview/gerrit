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

  test('updateSidebarHeight sets --sidebar-height based on viewport position', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    const wrapper = element.sidebarWrapper!;
    sinon.stub(wrapper, 'getBoundingClientRect').returns({
      top: 80,
      bottom: 800,
      left: 400,
      right: 800,
      width: 400,
      height: 720,
      x: 400,
      y: 80,
      toJSON: () => {},
    });

    element.updateSidebarHeight();

    const sidebarHeight = element.style.getPropertyValue('--sidebar-height');
    assert.isTrue(sidebarHeight.endsWith('px'));
    const heightVal = parseFloat(sidebarHeight);
    assert.isAbove(heightVal, 0);
  });

  test('updateSidebarHeight clamps to available viewport height when top offset exceeds sidebar-top', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    const wrapper = element.sidebarWrapper!;
    sinon.stub(wrapper, 'getBoundingClientRect').returns({
      top: 120, // 80px + 40px banner
      bottom: window.innerHeight + 500,
      left: 400,
      right: 800,
      width: 400,
      height: 480,
      x: 400,
      y: 120,
      toJSON: () => {},
    });

    element.updateSidebarHeight();

    const expectedHeight = `${window.innerHeight - 120}px`;
    assert.equal(
      element.style.getPropertyValue('--sidebar-height'),
      expectedHeight
    );
  });

  test('updateSidebarHeight clamps to container bottom when near bottom of page', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    const wrapper = element.sidebarWrapper!;
    sinon.stub(wrapper, 'getBoundingClientRect').returns({
      top: 20, // scrolled past 80px
      bottom: 600, // wrapper ends at 600px, above window.innerHeight
      left: 400,
      right: 800,
      width: 400,
      height: 580,
      x: 400,
      y: 20,
      toJSON: () => {},
    });

    element.updateSidebarHeight();

    // topInViewport is clamped to resolvedSidebarTop (80px).
    // bottomInViewport is clamped to rect.bottom (600px).
    // Available height = 600 - 80 = 520px.
    assert.equal(element.style.getPropertyValue('--sidebar-height'), '520px');
  });

  test('cleans up --sidebar-height when hideSide becomes true', async () => {
    element.hideSide = false;
    await element.updateComplete;
    assert.isNotEmpty(element.style.getPropertyValue('--sidebar-height'));

    element.hideSide = true;
    await element.updateComplete;
    assert.isEmpty(element.style.getPropertyValue('--sidebar-height'));
  });
});
