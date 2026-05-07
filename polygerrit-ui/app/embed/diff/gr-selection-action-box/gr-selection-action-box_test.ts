/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-selection-action-box';
import {GrSelectionActionBox} from './gr-selection-action-box';
import {listenOnce, queryAndAssert} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';
import {Side} from '../../../api/diff';

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
        <slot name="selectionActionBox" invisible="">
          <gr-tooltip id="tooltip" text="Press c to comment"></gr-tooltip>
        </slot>
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
      element.handleMouseDown(e);
      assert.isTrue(e.preventDefault.called);
      assert.equal(
        dispatchEventStub.lastCall.args[0].type,
        'create-comment-requested'
      );
    });

    test('event ignored if not main button', () => {
      e.button = 1;
      element.handleMouseDown(e);
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
      assert.isOk(element.tooltip);
      sinon
        .stub(element.tooltip, 'getBoundingClientRect')
        .returns({width: 10, height: 10} as DOMRect);
    });

    test('renders visible', async () => {
      await element.placeAbove(target);
      await element.updateComplete;
      assertEqualIgnoreWhitespaceAndNewlines(
        element.innerHTML,
        /* HTML */ `
          <!---->
          <slot name="selectionActionBox">
            <gr-tooltip id="tooltip" text="Press c to comment"></gr-tooltip>
          </slot>
        `
      );
    });

    test('fires selection-action-box-visible event with correct target and properties', async () => {
      dispatchEventStub.restore();
      const visibleEventPromise = listenOnce<CustomEvent>(
        document,
        'selection-action-box-visible'
      );

      element.getSelectionContext = () =>
        Promise.resolve({
          path: 'test-path.txt',
          side: Side.LEFT,
          range: {
            start_line: 1,
            start_character: 2,
            end_line: 3,
            end_character: 4,
          },
          text: 'selected text',
        });

      await element.placeAbove(target);

      const ev = await visibleEventPromise;

      const targetEl = ev.target as HTMLElement;
      assert.equal(targetEl, element);
      assert.isDefined(ev.detail.getSelectionContext);
      const context = await ev.detail.getSelectionContext!();
      assert.equal(context.path, 'test-path.txt');
      assert.equal(context.side, Side.LEFT);
      assert.deepEqual(context.range, {
        start_line: 1,
        start_character: 2,
        end_line: 3,
        end_character: 4,
      });
      assert.equal(context.text, 'selected text');
    });

    test('event retargeting in shadow DOM', async () => {
      dispatchEventStub.restore();

      // Create a host element with shadow DOM
      const host = document.createElement('div');
      const shadow = host.attachShadow({mode: 'open'});

      // Put the action box and a target inside shadow DOM
      const box = document.createElement('gr-selection-action-box');
      const shadowTarget = document.createElement('div');
      shadowTarget.textContent = 'shadow text';
      shadow.appendChild(box);
      shadow.appendChild(shadowTarget);

      // Append host to body so it is in the document
      document.body.appendChild(host);
      try {
        await box.updateComplete;

        // Stub necessary methods on the new box instance
        sinon
          .stub(box, 'tooltip' as keyof GrSelectionActionBox)
          .value(element.tooltip);
        sinon.stub(box, 'getTargetBoundingRect').returns({
          top: 42,
          bottom: 20,
          left: 30,
          right: 40,
          width: 100,
          height: 60,
        } as DOMRect);

        let capturedPath: EventTarget[] = [];
        const visibleEventPromise = new Promise<CustomEvent>(resolve => {
          const listener = (e: Event) => {
            capturedPath = e.composedPath();
            document.removeEventListener(
              'selection-action-box-visible',
              listener
            );
            resolve(e as CustomEvent);
          };
          document.addEventListener('selection-action-box-visible', listener);
        });

        box.getSelectionContext = () =>
          Promise.resolve({
            path: 'shadow-path.txt',
            side: Side.RIGHT,
            range: {
              start_line: 5,
              start_character: 6,
              end_line: 7,
              end_character: 8,
            },
            text: 'shadow selected text',
          });

        await box.placeAbove(shadowTarget);

        const ev = await visibleEventPromise;

        // ev.target should be the host 'div' because of retargeting!
        assert.equal(ev.target, host);
        assert.isUndefined(
          (ev.target as unknown as Record<string, unknown>).path
        );
        assert.isUndefined(
          (ev.target as unknown as Record<string, unknown>).side
        );

        // Assert on event detail (should be preserved despite retargeting!)
        assert.isDefined(ev.detail.getSelectionContext);
        const context = await ev.detail.getSelectionContext!();
        assert.equal(context.path, 'shadow-path.txt');
        assert.equal(context.side, Side.RIGHT);
        assert.deepEqual(context.range, {
          start_line: 5,
          start_character: 6,
          end_line: 7,
          end_character: 8,
        });
        assert.equal(context.text, 'shadow selected text');

        // The actual target can be found in capturedPath
        assert.isAbove(capturedPath.length, 0);
        const actualTarget = capturedPath[0] as HTMLElement;
        assert.equal(actualTarget, box);
      } finally {
        host.remove();
      }
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
      assert.equal(element.style.top, '45px');
      assert.equal(element.style.left, '72px');
    });

    test('placeBelow for Text Node argument', async () => {
      await element.placeBelow(target.firstElementChild!);
      assert.equal(element.style.top, '45px');
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
      .replace(/\s+/g, ' ')
      .replace(/\s+>/g, '>')
      .trim();
  if (normalize(actual) !== normalize(expected)) {
    throw new Error(`Assertion failed:
    Actual: "${normalize(actual)}"
    Expected: "${normalize(expected)}"`);
  }
}
