/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-tooltip-content.js';

const basicFixture = fixtureFromElement('gr-tooltip-content');

suite('gr-tooltip-content tests', () => {
  let element;

  function makeTooltip(tooltipRect, parentRect) {
    return {
      arrowCenterOffset: '0',
      getBoundingClientRect() {
        return tooltipRect;
      },
      style: {left: 0, top: 0},
      parentElement: {
        getBoundingClientRect() {
          return parentRect;
        },
      },
    };
  }

  setup(async () => {
    element = basicFixture.instantiate();
    element.title = 'title';
    await element.updateComplete;
  });

  test('icon is not visible by default', () => {
    assert.isNotOk(element.shadowRoot.querySelector('iron-icon'));
  });

  test('icon is visible with showIcon property', async () => {
    element.showIcon = true;
    await element.updateComplete;
    assert.isOk(element.shadowRoot.querySelector('iron-icon'));
  });

  test('position-below attribute is reflected', async () => {
    assert.isFalse(element.hasAttribute('position-below'));
    element.positionBelow = true;
    await element.updateComplete;
    assert.isTrue(element.hasAttribute('position-below'));
  });

  test('normal position', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(() => {
      return {top: 100, left: 100, width: 200};
    });
    const tooltip = makeTooltip(
        {height: 30, width: 50},
        {top: 0, left: 0, width: 1000});

    element._positionTooltip(tooltip);
    assert.equal(tooltip.arrowCenterOffset, '0');
    assert.equal(tooltip.style.left, '175px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('left side position', async () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(() => {
      return {top: 100, left: 10, width: 50};
    });
    const tooltip = makeTooltip(
        {height: 30, width: 120},
        {top: 0, left: 0, width: 1000});

    element._positionTooltip(tooltip);
    await element.updateComplete;
    assert.isBelow(parseFloat(tooltip.arrowCenterOffset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '0px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('right side position', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(() => {
      return {top: 100, left: 950, width: 50};
    });
    const tooltip = makeTooltip(
        {height: 30, width: 120},
        {top: 0, left: 0, width: 1000});

    element._positionTooltip(tooltip);
    assert.isAbove(parseFloat(tooltip.arrowCenterOffset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '915px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('position to bottom', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(() => {
      return {top: 100, left: 950, width: 50, height: 50};
    });
    const tooltip = makeTooltip(
        {height: 30, width: 120},
        {top: 0, left: 0, width: 1000});

    element.positionBelow = true;
    element._positionTooltip(tooltip);
    assert.isAbove(parseFloat(tooltip.arrowCenterOffset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '915px');
    assert.equal(tooltip.style.top, '157.2px');
  });

  test('hides tooltip when detached', async () => {
    const handleHideTooltipStub = sinon.stub(element, '_handleHideTooltip');
    element.remove();
    await element.updateComplete;
    assert.isTrue(handleHideTooltipStub.called);
  });

  test('sets up listeners when has-tooltip is changed', async () => {
    const addListenerStub = sinon.stub(element, 'addEventListener');
    element.hasTooltip = true;
    await element.updateComplete;
    assert.isTrue(addListenerStub.called);
  });

  test('clean up listeners when has-tooltip changed to false', async () => {
    const removeListenerStub = sinon.stub(element, 'removeEventListener');
    element.hasTooltip = true;
    await element.updateComplete;
    element.hasTooltip = false;
    await element.updateComplete;
    assert.isTrue(removeListenerStub.called);
  });

  test('do not display tooltips on touch devices', async () => {
    // On touch devices, tooltips should not be shown.
    element.isTouchDevice = true;
    await element.updateComplete;

    // fire mouse-enter
    element._handleShowTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);

    // fire mouse-enter
    element._handleHideTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);

    // On other devices, tooltips should be shown.
    element.isTouchDevice = false;

    // fire mouse-enter
    element._handleShowTooltip();
    await element.updateComplete;
    assert.isOk(element.tooltip);

    // fire mouse-enter
    element._handleHideTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);
  });
});

