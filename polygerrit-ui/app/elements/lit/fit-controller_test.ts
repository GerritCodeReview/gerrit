/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
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
    new DOMRect();
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(element.style.top, '37px');
    assert.equal(element.style.left, '37px');
  });

  test('host margin updates positioning', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';
    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    // is 10px extra from the previous test due to host margin
    assert.equal(element.style.top, '47px');
    assert.equal(element.style.left, '47px');
  });

  test('host minWidth, minHeight overrides positioning', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';

    element.style.minHeight = '50px';
    element.style.minWidth = '60px';

    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(element.style.top, '47px');

    // Should be 47 like the previous test but that would make it overall
    // smaller in width than the minWidth defined
    assert.equal(element.style.left, '37px');
    assert.equal(element.style.maxWidth, '60px');
  });

  test('positioning happens within window size ', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    // window size is small hence limits the position
    const windowRect = new DOMRect(0, 0, 40, 40);

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';

    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(element.style.top, '40px');
    assert.equal(element.style.left, '40px');
  });
});
