/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-selection-action-box';
import {GrSelectionActionBox} from './gr-selection-action-box';
import {queryAndAssert} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-selection-action-box', () => {
  let container: HTMLDivElement;
  let element: GrSelectionActionBox;
  let dispatchEventStub: sinon.SinonStub;

  setup(async () => {
    container = await fixture<HTMLDivElement>(html`
      <div>
        <gr-selection-action-box></gr-selection-action-box>
        <div class="target">some text</div>
      </div>
    `);
    element = queryAndAssert<GrSelectionActionBox>(
      container,
      'gr-selection-action-box'
    );
    await element.updateComplete;

    dispatchEventStub = sinon.stub(element, 'dispatchEvent');
  });

  test('renders', () => {
    assertEqualIgnoreWhitespaceAndNewlines(
      element.innerHTML,
      /* HTML */ `
        <!---->
        <div class="menu" invisible="">
          <div class="menu-item">Press c to comment</div>
        </div>
      `
    );
  });

  test('ignores regular keys', () => {
    const event = new KeyboardEvent('keydown', {key: 'a'});
    document.body.dispatchEvent(event);
    assert.isFalse(dispatchEventStub.called);
  });

  suite('mousedown reacts only to main button', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let e: any;

    setup(() => {
      e = {
        button: 0,
        preventDefault: sinon.stub(),
        stopPropagation: sinon.stub(),
      };
    });

    test('event handled if main button', () => {
      const menuItem = element.querySelector('.menu-item') as HTMLElement;
      assert.isOk(menuItem);
      e.target = menuItem;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (element as any).handleMenuMouseDown(e);
      assert.isTrue(e.preventDefault.called);
      assert.equal(
        dispatchEventStub.lastCall.args[0].type,
        'create-comment-requested'
      );
    });

    test('event ignored if not main button', () => {
      e.button = 1;
      const menuItem = element.querySelector('.menu-item') as HTMLElement;
      assert.isOk(menuItem);
      e.target = menuItem;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (element as any).handleMenuMouseDown(e);
      assert.isFalse(e.preventDefault.called);
      assert.isFalse(dispatchEventStub.called);
    });
  });

  suite('placeAbove', () => {
    let target: HTMLDivElement;
    let getTargetBoundingRectStub: sinon.SinonStub;

    setup(() => {
      target = queryAndAssert<HTMLDivElement>(container, '.target');
      sinon.stub(container, 'getBoundingClientRect').returns({
        top: 1,
        bottom: 2,
        left: 3,
        right: 4,
        width: 50,
        height: 6,
      } as DOMRect);
      getTargetBoundingRectStub = sinon
        .stub(element, 'getTargetBoundingRect')
        .returns({
          top: 42,
          bottom: 20,
          left: 30,
          right: 40,
          width: 100,
          height: 60,
        } as DOMRect);
      assert.isOk(element.menuElement);
      sinon
        .stub(element.menuElement, 'getBoundingClientRect')
        .returns({width: 10, height: 10} as DOMRect);
    });

    test('renders visible', async () => {
      await element.placeAbove(target);
      await element.updateComplete;
      assertEqualIgnoreWhitespaceAndNewlines(
        element.innerHTML,
        /* HTML */ `
          <!---->
          <div class="menu">
            <div class="menu-item">Press c to comment</div>
          </div>
        `
      );
    });

    test('placeAbove for Element argument', async () => {
      await element.placeAbove(target);
      assert.equal(element.style.top, '25px');
      assert.equal(element.style.left, '72px');
    });

    test('placeAbove for Text Node argument', async () => {
      await element.placeAbove(target.firstElementChild!);
      assert.equal(element.style.top, '25px');
      assert.equal(element.style.left, '72px');
    });

    test('placeBelow for Element argument', async () => {
      await element.placeBelow(target);
      assert.equal(element.style.top, '107px');
      assert.equal(element.style.left, '72px');
    });

    test('placeBelow for Text Node argument', async () => {
      await element.placeBelow(target.firstElementChild!);
      assert.equal(element.style.top, '107px');
      assert.equal(element.style.left, '72px');
    });

    test('uses document.createRange', async () => {
      const createRangeSpy = sinon.spy(document, 'createRange');
      getTargetBoundingRectStub.restore();
      await element.placeAbove(target.firstChild as HTMLElement);
      assert.isTrue(createRangeSpy.called);
    });
  });
});

function assertEqualIgnoreWhitespaceAndNewlines(
  actual: string,
  expected: string
): void {
  const normalize = (str: string): string =>
    str
      .replace(/\r/g, '')
      .replace(/\n/g, '')
      .replace(/<style>.*?<\/style>/g, '')
      .replace(/<!--\?lit\$[0-9]*\$-->/g, '')
      .replace(/>\s+/g, '>')
      .replace(/\s+</g, '<')
      .replace(/\s+/g, ' ')
      .trim();
  if (normalize(actual) !== normalize(expected)) {
    throw new Error(`Assertion failed:
    Actual: "${normalize(actual)}"
    Expected: "${normalize(expected)}"`);
  }
}
