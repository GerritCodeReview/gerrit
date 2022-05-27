/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {Side, TokenHighlightEventDetails} from '../../../api/diff';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {HOVER_DELAY_MS, TokenHighlightLayer} from './token-highlight-layer';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {html, render} from 'lit';
import {_testOnly_allTasks} from '../../../utils/async-util';
import {queryAndAssert} from '../../../test/test-utils';

// MockInteractions.makeMouseEvent always sets buttons to 1.
function dispatchMouseEvent(
  type: string,
  xy: {x: number; y: number},
  node: Element
) {
  const props = {
    bubbles: true,
    cancellable: true,
    composed: true,
    clientX: xy.x,
    clientY: xy.y,
    buttons: 0,
  };
  node.dispatchEvent(new MouseEvent(type, props));
}

class MockListener {
  private results: any[][] = [];

  notify(...args: any[]) {
    this.results.push(args);
  }

  shift() {
    return this.results.shift();
  }

  flush() {
    this.results = [];
  }

  get pending(): number {
    return this.results.length;
  }
}

suite('token-highlight-layer', () => {
  let container: HTMLElement;
  let listener: MockListener;
  let highlighter: TokenHighlightLayer;
  let tokenHighlightingCalls: {details?: TokenHighlightEventDetails}[] = [];

  function tokenHighlightListener(
    highlightDetails?: TokenHighlightEventDetails
  ) {
    tokenHighlightingCalls.push({details: highlightDetails});
  }

  setup(async () => {
    listener = new MockListener();
    tokenHighlightingCalls = [];
    container = document.createElement('div');
    document.body.appendChild(container);
    highlighter = new TokenHighlightLayer(container, tokenHighlightListener);
    highlighter.addListener((...args) => listener.notify(...args));
  });

  teardown(() => {
    document.body.removeChild(container);
  });

  function annotate(el: HTMLElement, side: Side = Side.LEFT, line = 1) {
    const diffLine = new GrDiffLine(GrDiffLineType.BOTH);
    diffLine.afterNumber = line;
    diffLine.beforeNumber = line;
    highlighter.annotate(el, document.createElement('div'), diffLine, side);
    return el;
  }

  let uniqueId = 0;
  function createLineId() {
    uniqueId++;
    return `line-${uniqueId.toString()}`;
  }

  function createLine(text: string, line = 1): HTMLElement {
    const lineId = createLineId();
    const template = html`
      <div class="line">
        <div data-value=${line} class="lineNum right"></div>
        <div class="content">
          <div id=${lineId} class="contentText">${text}</div>
        </div>
      </div>
    `;

    const div = document.createElement('div');
    render(template, div);
    container.appendChild(div);
    const el = queryAndAssert(container, `#${lineId}`);
    return el as HTMLElement;
  }

  suite('annotate', () => {
    function assertAnnotation(
      args: any[],
      el: HTMLElement,
      start: number,
      length: number,
      cssClass: string
    ) {
      assert.equal(args[0], el);
      assert.equal(args[1], start);
      assert.equal(args[2], length);
      assert.equal(args[3], cssClass);
    }

    test('annotate adds css token', () => {
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      const el = createLine('these are words');
      annotate(el);
      assert.isTrue(annotateElementStub.calledThrice);
      assertAnnotation(
        annotateElementStub.args[0],
        el,
        0,
        5,
        'tk-text-these tk-index-0 token'
      );
      assertAnnotation(
        annotateElementStub.args[1],
        el,
        6,
        3,
        'tk-text-are tk-index-6 token'
      );
      assertAnnotation(
        annotateElementStub.args[2],
        el,
        10,
        5,
        'tk-text-words tk-index-10 token'
      );
    });

    test('annotate adds mouse handlers', () => {
      const el = createLine('these are words');
      const addEventListenerStub = sinon.stub(el, 'addEventListener');
      annotate(el);
      assert.isTrue(addEventListenerStub.calledTwice);
      assert.equal(addEventListenerStub.args[0][0], 'mouseover');
      assert.equal(addEventListenerStub.args[1][0], 'mouseout');
    });

    test('annotate does not add mouse handlers without words', () => {
      const el = createLine('  ');
      const addEventListenerStub = sinon.stub(el, 'addEventListener');
      annotate(el);
      assert.isFalse(addEventListenerStub.called);
    });

    test('annotate adds mouse handlers for longest word', () => {
      const el = createLine('w'.repeat(100));
      const addEventListenerStub = sinon.stub(el, 'addEventListener');
      annotate(el);
      assert.isTrue(addEventListenerStub.called);
    });

    test('annotate does not add mouse handlers for long words', () => {
      const el = createLine('w'.repeat(101));
      const addEventListenerStub = sinon.stub(el, 'addEventListener');
      annotate(el);
      assert.isFalse(addEventListenerStub.called);
    });
  });

  suite('highlight', () => {
    test('highlighting hover delay', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words');
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-text-words');
      assert.isTrue(words1.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );

      assert.equal(listener.pending, 0);
      assert.equal(_testOnly_allTasks.size, 1);

      // Too early for hover behavior to trigger.
      clock.tick(100);
      assert.equal(listener.pending, 0);
      assert.equal(_testOnly_allTasks.size, 1);

      // After a total of HOVER_DELAY_MS ms the hover behavior should trigger.
      clock.tick(HOVER_DELAY_MS - 100);
      assert.equal(listener.pending, 2);
      assert.equal(_testOnly_allTasks.size, 0);
      assert.deepEqual(listener.shift(), [1, 1, Side.LEFT]);
      assert.deepEqual(listener.shift(), [2, 2, Side.RIGHT]);
    });

    test('highlighting spans many lines', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words');
      annotate(line2, Side.RIGHT, 1000);
      const words1 = queryAndAssert(line1, '.tk-text-words');
      assert.isTrue(words1.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );

      assert.equal(listener.pending, 0);

      // After a total of HOVER_DELAY_MS ms the hover behavior should trigger.
      clock.tick(HOVER_DELAY_MS);
      assert.equal(listener.pending, 2);
      assert.equal(_testOnly_allTasks.size, 0);
      assert.deepEqual(listener.shift(), [1, 1, Side.LEFT]);
      assert.deepEqual(listener.shift(), [1000, 1000, Side.RIGHT]);
    });

    test('highlighting mouse out before delay', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words', 2);
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-text-words');
      assert.isTrue(words1.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(listener.pending, 0);
      clock.tick(100);
      // Mouse out after 100ms but before hover delay.
      dispatchMouseEvent(
        'mouseout',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(listener.pending, 0);
      clock.tick(HOVER_DELAY_MS - 100);
      assert.equal(listener.pending, 0);
      assert.equal(_testOnly_allTasks.size, 0);
    });

    test('triggers listener for applying and clearing highlighting', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words', 2);
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-text-words');
      assert.isTrue(words1.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(tokenHighlightingCalls.length, 0);
      clock.tick(HOVER_DELAY_MS);
      assert.equal(tokenHighlightingCalls.length, 1);
      assert.deepEqual(tokenHighlightingCalls[0].details, {
        token: 'words',
        side: Side.RIGHT,
        element: words1,
        range: {start_line: 1, start_column: 5, end_line: 1, end_column: 9},
      });

      MockInteractions.click(container);
      assert.equal(tokenHighlightingCalls.length, 2);
      assert.deepEqual(tokenHighlightingCalls[1].details, undefined);
    });

    test('triggers listener on token with single occurrence', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('a tokenWithSingleOccurence');
      const line2 = createLine('can be highlighted', 2);
      annotate(line1);
      annotate(line2, Side.RIGHT, 2);
      const tokenNode = queryAndAssert(
        line1,
        '.tk-text-tokenWithSingleOccurence'
      );
      assert.isTrue(tokenNode.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(tokenNode),
        tokenNode
      );
      assert.equal(tokenHighlightingCalls.length, 0);
      clock.tick(HOVER_DELAY_MS);
      assert.equal(tokenHighlightingCalls.length, 1);
      assert.deepEqual(tokenHighlightingCalls[0].details, {
        token: 'tokenWithSingleOccurence',
        side: Side.RIGHT,
        element: tokenNode,
        range: {start_line: 1, start_column: 3, end_line: 1, end_column: 26},
      });

      MockInteractions.click(container);
      assert.equal(tokenHighlightingCalls.length, 2);
      assert.deepEqual(tokenHighlightingCalls[1].details, undefined);
    });

    test('clicking clears highlight', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words', 2);
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-text-words');
      assert.isTrue(words1.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(listener.pending, 0);
      clock.tick(HOVER_DELAY_MS);
      assert.equal(listener.pending, 2);
      listener.flush();
      assert.equal(listener.pending, 0);
      MockInteractions.click(container);
      assert.equal(listener.pending, 2);
      assert.deepEqual(listener.shift(), [1, 1, Side.LEFT]);
      assert.deepEqual(listener.shift(), [2, 2, Side.RIGHT]);
    });

    test('clicking on word does not clear highlight', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words', 2);
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-text-words');
      assert.isTrue(words1.classList.contains('token'));
      dispatchMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(listener.pending, 0);
      clock.tick(HOVER_DELAY_MS);
      assert.equal(listener.pending, 2);
      listener.flush();
      assert.equal(listener.pending, 0);
      MockInteractions.click(words1);
      assert.equal(listener.pending, 0);
    });
  });
});
