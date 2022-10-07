/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
import '../shared/gr-autocomplete/gr-autocomplete';
import {fixture, html, assert} from '@open-wc/testing';
import {FitController} from './fit-controller';
import {LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';

@customElement('fit-element')
class FitElement extends LitElement {
  fitController = new FitController(this);

  horizontalOffset = 0;

  verticalOffset = 0;

  override render() {
    return html`<div></div>`;
  }
}

suite('fit controller', () => {
  let element: FitElement;
  setup(async () => {
    element = await fixture(html`<fit-element></fit-element>`);
  });

  test('refit positioning', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const windowRect = {
      top: 0,
      left: 0,
      width: 600,
      height: 600,
      right: 600,
      bottom: 600,
    } as DOMRect;

    element.fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(parseInt(element.style.top), 37);
    assert.equal(parseInt(element.style.left), 37);
  });

  test('host margin updates positioning', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const windowRect = {
      top: 0,
      left: 0,
      width: 600,
      height: 600,
      right: 600,
      bottom: 600,
    } as DOMRect;

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';
    element.fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    // is 10px extra from the previous test due to host margin
    assert.equal(parseInt(element.style.top), 47);
    assert.equal(parseInt(element.style.left), 47);
  });

  test('host minWidth, minHeight overrides positioning', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const windowRect = {
      top: 0,
      left: 0,
      width: 600,
      height: 600,
      right: 600,
      bottom: 600,
    } as DOMRect;

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';

    element.style.minHeight = '50px';
    element.style.minWidth = '60px';

    element.fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(parseInt(element.style.top), 47);

    // Should be 47 like the previous test but that would make it overall
    // smaller in width than the minWidth defined
    assert.equal(parseInt(element.style.left), 37);
    assert.equal(parseInt(element.style.maxWidth), 60);
  });

  test('positioning happens within window size ', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    // window size is small hence limits the position
    const windowRect = {
      top: 0,
      left: 0,
      width: 40,
      height: 40,
      right: 40,
      bottom: 40,
    } as DOMRect;

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';

    element.fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(parseInt(element.style.top), 40);
    assert.equal(parseInt(element.style.left), 40);
  });
});
