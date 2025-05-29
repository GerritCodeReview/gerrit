/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-tooltip-content';
import {GrTooltipContent} from './gr-tooltip-content';
import {assert, fixture, html} from '@open-wc/testing';
import {GrTooltip} from '../gr-tooltip/gr-tooltip';
import {query} from '../../../test/test-utils';

suite('gr-tooltip-content tests', () => {
  let element: GrTooltipContent;

  function makeTooltip(tooltipRect: DOMRect, parentRect: DOMRect) {
    return {
      arrowCenterOffset: '0',
      getBoundingClientRect() {
        return tooltipRect;
      },
      style: {left: '0', top: '0'},
      parentElement: {
        getBoundingClientRect() {
          return parentRect;
        },
        clientWidth: parentRect.width,
      },
    };
  }

  setup(async () => {
    element = await fixture<GrTooltipContent>(html`
      <gr-tooltip-content></gr-tooltip-content>
    `);
    element.title = 'title';
    await element.updateComplete;
  });

  test('render', async () => {
    element.showIcon = true;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <slot> </slot>
        <gr-icon icon="info" filled></gr-icon>
      `
    );
  });

  test('icon is not visible by default', () => {
    assert.isNotOk(query(element, 'gr-icon'));
  });

  test('icon is visible with showIcon property', async () => {
    element.showIcon = true;
    await element.updateComplete;
    assert.isOk(query(element, 'gr-icon'));
  });

  test('position-below attribute is reflected', async () => {
    assert.isFalse(element.hasAttribute('position-below'));
    element.positionBelow = true;
    await element.updateComplete;
    assert.isTrue(element.hasAttribute('position-below'));
  });

  test('normal position', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(
      () =>
        ({
          top: 100,
          left: 100,
          width: 200,
          right: 300,
          height: 50,
          bottom: 150,
        } as DOMRect)
    );
    const tooltip = makeTooltip(
      {height: 30, width: 50} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element._positionTooltip(tooltip);
    assert.equal(tooltip.arrowCenterOffset, '0px');
    assert.equal(tooltip.style.left, '175px');
    // 100 - tooltip height (30) - arrow height (7.2)
    assert.equal(tooltip.style.top, '62.8px');
  });

  test('left side position', async () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(
      () =>
        ({
          top: 100,
          left: 10,
          width: 50,
          right: 60,
          height: 50,
          bottom: 150,
        } as DOMRect)
    );
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element._positionTooltip(tooltip);
    await element.updateComplete;
    // Aligned with left edge
    assert.equal(tooltip.style.left, '0px');
    // element center (35) - tooltip center (60)
    assert.equal(tooltip.arrowCenterOffset, '-25px');
    // 100 - tooltip height (30) - arrow height (7.2)
    assert.equal(tooltip.style.top, '62.8px');
  });

  test('right side position', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(
      () =>
        ({
          top: 100,
          left: 950,
          width: 50,
          right: 1000,
          height: 50,
          bottom: 150,
        } as DOMRect)
    );
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element._positionTooltip(tooltip);
    // Aligned with right edge: 1000 - tooltip width (120) - 1px pad
    assert.equal(tooltip.style.left, '879px');
    // element center (975) - tooltip center (939)
    assert.equal(tooltip.arrowCenterOffset, '36px');
    // 100 - tooltip height (30) - arrow height (7.2)
    assert.equal(tooltip.style.top, '62.8px');
  });

  test('position to bottom', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(
      () =>
        ({
          top: 100,
          left: 950,
          width: 50,
          height: 50,
          right: 1000,
          bottom: 150,
        } as DOMRect)
    );
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element.positionBelow = true;
    element._positionTooltip(tooltip);
    // Aligned with right edge: 1000 - tooltip width (120) - 1px pad
    assert.equal(tooltip.style.left, '879px');
    // element center (975) - tooltip center (939)
    assert.equal(tooltip.arrowCenterOffset, '36px');
    // 150 + arrow height (7.2)
    assert.equal(tooltip.style.top, '157.2px');
  });

  test('automatic flip to bottom', () => {
    sinon.stub(element, 'getBoundingClientRect').callsFake(
      () =>
        ({
          // Not enough space for arrow
          top: 30,
          left: 950,
          width: 50,
          height: 50,
          right: 1000,
          bottom: 80,
        } as DOMRect)
    );
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element.positionBelow = true;
    element._positionTooltip(tooltip);
    // Aligned with right edge: 1000 - tooltip width (120) - 1px pad
    assert.equal(tooltip.style.left, '879px');
    // element center (975) - tooltip center (939)
    assert.equal(tooltip.arrowCenterOffset, '36px');
    // 150 + arrow height (7.2)
    assert.equal(tooltip.style.top, '87.2px');
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
    await element._handleShowTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);

    // fire mouse-enter
    element._handleHideTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);

    // On other devices, tooltips should be shown.
    element.isTouchDevice = false;

    // fire mouse-enter
    await element._handleShowTooltip();
    await element.updateComplete;
    assert.isOk(element.tooltip);

    // fire mouse-enter
    element._handleHideTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);
  });

  suite('getTooltipParent', () => {
    let divInDialog: HTMLDivElement;
    let divInDialogShadow: ShadowRoot;
    let div: HTMLDivElement;
    let divShadow: ShadowRoot;
    let dialog: HTMLDialogElement;

    setup(() => {
      divInDialog = document.createElement('div');
      divInDialogShadow = divInDialog.attachShadow({mode: 'open'});
      div = document.createElement('div');
      divShadow = div.attachShadow({mode: 'open'});
      dialog = document.createElement('dialog');
      document.body.appendChild(div);
      document.body.appendChild(dialog);
      dialog.appendChild(divInDialog);
    });

    test('tooltip in the div', () => {
      const el = document.createElement('gr-tooltip-content');
      divShadow.appendChild(el);
      const tooltipParent = el.getTooltipParent(el);
      assert.strictEqual(
        tooltipParent,
        document.body,
        'Tooltip expected to be attached to body'
      );
    });

    test('tooltip in the dialog', () => {
      const el = document.createElement('gr-tooltip-content');
      dialog.appendChild(el);
      const tooltipParent = el.getTooltipParent(el);
      assert.strictEqual(
        tooltipParent,
        dialog,
        'Tooltip expected to be attached to dialog'
      );
    });

    test('tooltip in the div in the dialog', () => {
      const el = document.createElement('gr-tooltip-content');
      divInDialogShadow.appendChild(el);
      const tooltipParent = el.getTooltipParent(el);
      assert.strictEqual(
        tooltipParent,
        dialog,
        'Tooltip expected to be attached to dialog'
      );
    });

    teardown(() => {
      document.body.removeChild(div);
      document.body.removeChild(dialog);
    });
  });
});
