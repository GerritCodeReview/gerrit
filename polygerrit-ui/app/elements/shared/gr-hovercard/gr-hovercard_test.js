/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-hovercard.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-hovercard for="foo" id="bar"></gr-hovercard>
`);

suite('gr-hovercard tests', () => {
  let element;

  let button;
  let testResolve;
  let testPromise;

  setup(() => {
    testResolve = undefined;
    testPromise = new Promise(r => testResolve = r);
    button = document.createElement('button');
    button.innerHTML = 'Hello';
    button.setAttribute('id', 'foo');
    document.body.appendChild(button);

    element = basicFixture.instantiate();
  });

  teardown(() => {
    element.hide({});
    button.remove();
  });

  test('updatePosition', () => {
    // Test that the correct style properties have at least been set.
    element.position = 'bottom';
    element.updatePosition();
    assert.typeOf(element.style.getPropertyValue('left'), 'string');
    assert.typeOf(element.style.getPropertyValue('top'), 'string');
    assert.typeOf(element.style.getPropertyValue('paddingTop'), 'string');
    assert.typeOf(element.style.getPropertyValue('marginTop'), 'string');

    const parentRect = document.documentElement.getBoundingClientRect();
    const targetRect = element._target.getBoundingClientRect();
    const thisRect = element.getBoundingClientRect();

    const targetLeft = targetRect.left - parentRect.left;
    const targetTop = targetRect.top - parentRect.top;

    const pixelCompare = pixel =>
      Math.round(parseInt(pixel.substring(0, pixel.length - 1)), 10);

    assert.equal(
        pixelCompare(element.style.left),
        pixelCompare(
            (targetLeft + (targetRect.width - thisRect.width) / 2) + 'px'));
    assert.equal(
        pixelCompare(element.style.top),
        pixelCompare(
            (targetTop + targetRect.height + element.offset) + 'px'));
  });

  test('hide', () => {
    element.hide({});
    const style = getComputedStyle(element);
    assert.isFalse(element._isShowing);
    assert.isFalse(element.classList.contains('hovered'));
    assert.equal(style.display, 'none');
    assert.notEqual(element.container, element.parentNode);
  });

  test('show', async () => {
    await element.show({});
    const style = getComputedStyle(element);
    assert.isTrue(element._isShowing);
    assert.isTrue(element.classList.contains('hovered'));
    assert.equal(style.opacity, '1');
    assert.equal(style.visibility, 'visible');
  });

  test('debounceShow does not show immediately', async () => {
    element.debounceShowBy(100);
    setTimeout(testResolve, 0);
    await testPromise;
    assert.isFalse(element._isShowing);
  });

  test('debounceShow shows after delay', async () => {
    element.debounceShowBy(1);
    setTimeout(testResolve, 10);
    await testPromise;
    assert.isTrue(element._isShowing);
  });

  test('card is scheduled to show on enter and hides on leave', async () => {
    const button = document.querySelector('button');
    let enterResolve = undefined;
    const enterPromise = new Promise(r => enterResolve = r);
    button.addEventListener('mouseenter', enterResolve);
    let leaveResolve = undefined;
    const leavePromise = new Promise(r => leaveResolve = r);
    button.addEventListener('mouseleave', leaveResolve);

    assert.isFalse(element._isShowing);
    button.dispatchEvent(new CustomEvent('mouseenter'));

    await enterPromise;
    await flush();
    assert.isTrue(element.isScheduledToShow);
    element.showTask.flush();
    assert.isTrue(element._isShowing);
    assert.isFalse(element.isScheduledToShow);

    button.dispatchEvent(new CustomEvent('mouseleave'));

    await leavePromise;
    assert.isTrue(element.isScheduledToHide);
    assert.isTrue(element._isShowing);
    element.hideTask.flush();
    assert.isFalse(element.isScheduledToShow);
    assert.isFalse(element._isShowing);

    button.removeEventListener('mouseenter', enterResolve);
    button.removeEventListener('mouseleave', leaveResolve);
  });

  test('card should disappear on click', async () => {
    const button = document.querySelector('button');
    let enterResolve = undefined;
    const enterPromise = new Promise(r => enterResolve = r);
    button.addEventListener('mouseenter', enterResolve);
    let clickResolve = undefined;
    const clickPromise = new Promise(r => clickResolve = r);
    button.addEventListener('click', clickResolve);

    assert.isFalse(element._isShowing);

    button.dispatchEvent(new CustomEvent('mouseenter'));

    await enterPromise;
    await flush();
    assert.isTrue(element.isScheduledToShow);
    MockInteractions.tap(button);

    await clickPromise;
    assert.isFalse(element.isScheduledToShow);
    assert.isFalse(element._isShowing);

    button.removeEventListener('mouseenter', enterResolve);
    button.removeEventListener('click', clickResolve);
  });
});

