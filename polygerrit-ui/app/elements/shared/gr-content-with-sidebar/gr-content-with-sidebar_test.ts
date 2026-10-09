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
        <div aria-hidden="true" class="top-reference"></div>
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
        <div aria-hidden="true" class="top-reference"></div>
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
        <div aria-hidden="true" class="top-reference"></div>
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

  test('sidebar-wrapper has fixed position', async () => {
    element.hideSide = false;
    await element.updateComplete;

    const wrapper = element.sidebarWrapper!;
    const style = getComputedStyle(wrapper);
    assert.equal(style.position, 'fixed');
  });

  test('updateSidebarTop sets --sidebar-wrapper-top when top offset exceeds sidebar-top', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    // Simulate content being pushed down by 40px (e.g. 120px top from banner)
    sinon.stub(element, 'getBoundingClientRect').returns({
      top: 120,
      bottom: 800,
      left: 0,
      right: 1200,
      width: 1200,
      height: 680,
      x: 0,
      y: 120,
      toJSON: () => {},
    });

    sinon.stub(element.topReference!, 'getBoundingClientRect').returns({
      top: 80,
      bottom: 80,
      left: 0,
      right: 0,
      width: 0,
      height: 0,
      x: 0,
      y: 80,
      toJSON: () => {},
    });

    element.updateSidebarTop();

    assert.equal(
      element.style.getPropertyValue('--sidebar-wrapper-top'),
      '120px'
    );
  });

  test('updateSidebarTop clears --sidebar-wrapper-top when scrolled past banner', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    element.style.setProperty('--sidebar-wrapper-top', '120px');
    await element.updateComplete;

    sinon.stub(element, 'getBoundingClientRect').returns({
      top: 20, // scrolled past 80px
      bottom: 800,
      left: 0,
      right: 1200,
      width: 1200,
      height: 780,
      x: 0,
      y: 20,
      toJSON: () => {},
    });

    sinon.stub(element.topReference!, 'getBoundingClientRect').returns({
      top: 80,
      bottom: 80,
      left: 0,
      right: 0,
      width: 0,
      height: 0,
      x: 0,
      y: 80,
      toJSON: () => {},
    });

    element.updateSidebarTop();

    assert.equal(element.style.getPropertyValue('--sidebar-wrapper-top'), '');
  });

  test('subpixel differences <= 0.5px do not set --sidebar-wrapper-top', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    sinon.stub(element, 'getBoundingClientRect').returns({
      top: 80.3,
      bottom: 800,
      left: 0,
      right: 1200,
      width: 1200,
      height: 720,
      x: 0,
      y: 80.3,
      toJSON: () => {},
    });

    sinon.stub(element.topReference!, 'getBoundingClientRect').returns({
      top: 80.0,
      bottom: 80.0,
      left: 0,
      right: 0,
      width: 0,
      height: 0,
      x: 0,
      y: 80.0,
      toJSON: () => {},
    });

    element.updateSidebarTop();

    assert.equal(element.style.getPropertyValue('--sidebar-wrapper-top'), '');
  });

  test('window scroll and resize events trigger updateSidebarTop', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    const elementStub = sinon.stub(element, 'getBoundingClientRect').returns({
      top: 120,
      bottom: 800,
      left: 0,
      right: 1200,
      width: 1200,
      height: 680,
      x: 0,
      y: 120,
      toJSON: () => {},
    });

    sinon.stub(element.topReference!, 'getBoundingClientRect').returns({
      top: 80,
      bottom: 80,
      left: 0,
      right: 0,
      width: 0,
      height: 0,
      x: 0,
      y: 80,
      toJSON: () => {},
    });

    window.dispatchEvent(new Event('scroll'));
    assert.equal(
      element.style.getPropertyValue('--sidebar-wrapper-top'),
      '120px'
    );

    elementStub.returns({
      top: 20,
      bottom: 800,
      left: 0,
      right: 1200,
      width: 1200,
      height: 780,
      x: 0,
      y: 20,
      toJSON: () => {},
    });

    window.dispatchEvent(new Event('resize'));
    assert.equal(element.style.getPropertyValue('--sidebar-wrapper-top'), '');
  });

  test('disconnectedCallback cleans up --sidebar-wrapper-top', async () => {
    element.style.setProperty('--sidebar-wrapper-top', '120px');

    element.disconnectedCallback();

    assert.equal(element.style.getPropertyValue('--sidebar-wrapper-top'), '');
  });

  test('connectedCallback recomputes --sidebar-wrapper-top if hasUpdated', async () => {
    element.hideSide = false;
    element.style.setProperty('--sidebar-top', '80px');
    await element.updateComplete;

    sinon.stub(element, 'getBoundingClientRect').returns({
      top: 120,
      bottom: 800,
      left: 0,
      right: 1200,
      width: 1200,
      height: 680,
      x: 0,
      y: 120,
      toJSON: () => {},
    });

    sinon.stub(element.topReference!, 'getBoundingClientRect').returns({
      top: 80,
      bottom: 80,
      left: 0,
      right: 0,
      width: 0,
      height: 0,
      x: 0,
      y: 80,
      toJSON: () => {},
    });

    element.disconnectedCallback();
    assert.equal(element.style.getPropertyValue('--sidebar-wrapper-top'), '');

    element.connectedCallback();
    assert.equal(
      element.style.getPropertyValue('--sidebar-wrapper-top'),
      '120px'
    );
  });
});
