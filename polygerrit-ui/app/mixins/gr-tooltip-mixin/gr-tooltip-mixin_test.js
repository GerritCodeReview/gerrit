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

import '../../test/common-test-setup-karma.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {TooltipMixin} from './gr-tooltip-mixin.js';

const basicFixture = fixtureFromElement('gr-tooltip-mixin-element');

class GrTooltipMixinTestElement extends TooltipMixin(PolymerElement) {
  static get is() {
    return 'gr-tooltip-mixin-element';
  }
}

customElements.define(GrTooltipMixinTestElement.is,
    GrTooltipMixinTestElement);

suite('gr-tooltip-mixin tests', () => {
  let element;

  function makeTooltip(tooltipRect, parentRect) {
    return {
      getBoundingClientRect() { return tooltipRect; },
      updateStyles: sinon.stub(),
      style: {left: 0, top: 0},
      parentElement: {
        getBoundingClientRect() { return parentRect; },
      },
    };
  }

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('normal position', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(() => {
      return {top: 100, left: 100, width: 200};
    });
    const tooltip = makeTooltip(
        {height: 30, width: 50},
        {top: 0, left: 0, width: 1000});

    element._positionTooltip(tooltip);
    assert.isFalse(tooltip.updateStyles.called);
    assert.equal(tooltip.style.left, '175px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('left side position', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(() => {
      return {top: 100, left: 10, width: 50};
    });
    const tooltip = makeTooltip(
        {height: 30, width: 120},
        {top: 0, left: 0, width: 1000});

    element._positionTooltip(tooltip);
    assert.isTrue(tooltip.updateStyles.called);
    const offset = tooltip.updateStyles
        .lastCall.args[0]['--gr-tooltip-arrow-center-offset'];
    assert.isBelow(parseFloat(offset.replace(/px$/, '')), 0);
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
    assert.isTrue(tooltip.updateStyles.called);
    const offset = tooltip.updateStyles
        .lastCall.args[0]['--gr-tooltip-arrow-center-offset'];
    assert.isAbove(parseFloat(offset.replace(/px$/, '')), 0);
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
    assert.isTrue(tooltip.updateStyles.called);
    const offset = tooltip.updateStyles
        .lastCall.args[0]['--gr-tooltip-arrow-center-offset'];
    assert.isAbove(parseFloat(offset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '915px');
    assert.equal(tooltip.style.top, '157.2px');
  });

  test('hides tooltip when detached', () => {
    sinon.stub(element, '_handleHideTooltip');
    element.remove();
    flush();
    assert.isTrue(element._handleHideTooltip.called);
  });

  test('sets up listeners when has-tooltip is changed', () => {
    const addListenerStub = sinon.stub(element, 'addEventListener');
    element.hasTooltip = true;
    assert.isTrue(addListenerStub.called);
  });

  test('clean up listeners when has-tooltip changed to false', () => {
    const removeListenerStub = sinon.stub(element, 'removeEventListener');
    element.hasTooltip = true;
    element.hasTooltip = false;
    assert.isTrue(removeListenerStub.called);
  });
});

