/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {HovercardMixin} from './hovercard-mixin';
import {LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {
  MockPromise,
  mockPromise,
  pressKey,
  waitEventLoop,
} from '../../test/test-utils';
import {findActiveElement, Key} from '../../utils/dom-util';
import {fixture, html, assert} from '@open-wc/testing';

const base = HovercardMixin(LitElement);

declare global {
  interface HTMLElementTagNameMap {
    'hovercard-mixin-test': HovercardMixinTest;
  }
}

@customElement('hovercard-mixin-test')
class HovercardMixinTest extends base {
  constructor() {
    super();
    this.for = 'foo';
  }

  override render() {
    return html` <div id="container">
      <span tabindex="0" id="focusable"></span>
      <slot></slot>
    </div>`;
  }
}

suite('gr-hovercard tests', () => {
  let element: HovercardMixinTest;

  let button: HTMLElement;
  let testPromise: MockPromise<void>;

  setup(async () => {
    testPromise = mockPromise();
    button = document.createElement('button');
    button.innerHTML = 'Hello';
    button.setAttribute('id', 'foo');
    document.body.appendChild(button);

    element = await fixture(
      html`<hovercard-mixin-test></hovercard-mixin-test>`
    );
  });

  teardown(() => {
    pressKey(element, Key.ESC);
    element.mouseHide(new MouseEvent('click'));
    if (button) button.remove();
  });

  test('updatePosition', async () => {
    // Test that the correct style properties have at least been set.
    element.position = 'bottom';
    element.updatePosition();
    await element.updateComplete;
    assert.typeOf(element.style.getPropertyValue('left'), 'string');
    assert.typeOf(element.style.getPropertyValue('top'), 'string');
    assert.typeOf(element.style.getPropertyValue('paddingTop'), 'string');
    assert.typeOf(element.style.getPropertyValue('marginTop'), 'string');

    const parentRect = document.documentElement.getBoundingClientRect();
    const targetRect = element._target!.getBoundingClientRect();
    const thisRect = element.getBoundingClientRect();

    const targetLeft = targetRect.left - parentRect.left;
    const targetTop = targetRect.top - parentRect.top;

    const pixelCompare = (pixel: string) =>
      Math.round(parseInt(pixel.substring(0, pixel.length - 1), 10));

    assert.equal(
      pixelCompare(element.style.left),
      pixelCompare(`${targetLeft + (targetRect.width - thisRect.width) / 2}px`)
    );
    assert.equal(
      pixelCompare(element.style.top),
      pixelCompare(`${targetTop + targetRect.height + element.offset}px`)
    );
  });

  test('hide', () => {
    element.mouseHide(new MouseEvent('click'));
    const style = getComputedStyle(element);
    assert.isFalse(element._isShowing);
    assert.isFalse(element.classList.contains('hovered'));
    assert.equal(style.display, 'none');
    assert.notEqual(element.container, element.parentNode);
  });

  test('show', async () => {
    await element.show({});
    await element.updateComplete;
    const style = getComputedStyle(element);
    assert.isTrue(element._isShowing);
    assert.isTrue(element.classList.contains('hovered'));
    assert.equal(style.opacity, '1');
    assert.equal(style.visibility, 'visible');
  });

  test('debounceShow does not show immediately', async () => {
    element.debounceShowBy(100, {});
    setTimeout(() => testPromise.resolve(), 0);
    await testPromise;
    assert.isFalse(element._isShowing);
  });

  test('debounceShow shows after delay', async () => {
    element.debounceShowBy(1, {});
    setTimeout(() => testPromise.resolve(), 10);
    await testPromise;
    assert.isTrue(element._isShowing);
  });

  test('card is scheduled to show on enter and hides on leave', async () => {
    const button = document.querySelector('button');
    const enterPromise = mockPromise();
    button!.addEventListener('mousemove', () => enterPromise.resolve());
    const leavePromise = mockPromise();
    button!.addEventListener('mouseleave', () => leavePromise.resolve());

    assert.isFalse(element._isShowing);
    button!.dispatchEvent(new CustomEvent('mousemove'));

    await enterPromise;
    await waitEventLoop();
    assert.isTrue(element.isScheduledToShow);
    element.showTask!.flush();
    assert.isTrue(element._isShowing);
    assert.isFalse(element.isScheduledToShow);

    button!.dispatchEvent(new CustomEvent('mouseleave'));

    await leavePromise;
    assert.isTrue(element.isScheduledToHide);
    assert.isTrue(element._isShowing);
    element.hideTask!.flush();
    assert.isFalse(element.isScheduledToShow);
    assert.isFalse(element._isShowing);
  });

  test('card should disappear on click', async () => {
    const button = document.querySelector('button');
    const enterPromise = mockPromise();
    const clickPromise = mockPromise();
    button!.addEventListener('mousemove', () => enterPromise.resolve());
    button!.addEventListener('click', () => clickPromise.resolve());

    assert.isFalse(element._isShowing);

    button!.dispatchEvent(new CustomEvent('mousemove'));

    await enterPromise;
    await waitEventLoop();
    assert.isTrue(element.isScheduledToShow);
    button!.click();

    await clickPromise;
    assert.isFalse(element.isScheduledToShow);
    assert.isFalse(element._isShowing);
  });

  test('do not show on focus', async () => {
    const button = document.querySelector('button');
    button?.focus();
    await element.updateComplete;
    assert.isNotTrue(element.isScheduledToShow);
    assert.isFalse(element._isShowing);
  });

  test('show on pressing enter when focused', async () => {
    const button = document.querySelector('button')!;
    button.focus();
    await element.updateComplete;
    pressKey(button, Key.ENTER);
    await element.updateComplete;
    assert.isTrue(element._isShowing);
  });

  test('show on pressing space when focused', async () => {
    const button = document.querySelector('button')!;
    button.focus();
    await element.updateComplete;
    pressKey(button, Key.SPACE);
    await element.updateComplete;
    assert.isTrue(element._isShowing);
  });

  test('when on pressing enter, focus is moved to hovercard', async () => {
    const button = document.querySelector('button')!;
    button.focus();
    await element.updateComplete;
    await element.show({keyboardEvent: new KeyboardEvent('enter')});
    await element.updateComplete;
    assert.isTrue(element._isShowing);
    const activeElement = findActiveElement(document);
    assert.equal(activeElement?.id, 'focusable');
  });

  test('when on mouseEvent, focus is not moved to hovercard', async () => {
    const button = document.querySelector('button')!;
    button.focus();
    await element.updateComplete;
    await element.show({mouseEvent: new MouseEvent('enter')});
    await element.updateComplete;
    assert.isTrue(element._isShowing);
    const activeElement = findActiveElement(document);
    assert.notEqual(activeElement?.id, 'focusable');
  });
});
