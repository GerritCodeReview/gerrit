/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {Side} from '../../../api/diff';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {TokenHighlightLayer} from './token-highlight-layer';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import sinon from 'sinon/pkg/sinon-esm';
import {_testOnly_allTasks} from '../../../utils/async-util';
import {queryAndAssert} from '../../../test/test-utils';

const blankFixture = fixtureFromElement('div');

// MockInteractions.makeMouseEvent always sets buttons to 1.
function makeMouseEvent(
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

  setup(async () => {
    listener = new MockListener();
    highlighter = new TokenHighlightLayer();
    highlighter.addListener((...args) => listener.notify(...args));
    container = blankFixture.instantiate();
  });

  function annotate(el: HTMLElement, side: Side = Side.LEFT, line = 1) {
    const diffLine = new GrDiffLine(GrDiffLineType.BOTH);
    diffLine.afterNumber = line;
    diffLine.beforeNumber = line;
    highlighter.annotate(el, document.createElement('div'), diffLine, side);
    return el;
  }

  function createLine(text: string, line = 1) {
    const lineNum = document.createElement('div');
    lineNum.classList.add('lineNum');
    lineNum.setAttribute('data-value', line.toString());
    const el = document.createElement('div');
    el.textContent = text;
    const fullline = document.createElement('div');
    fullline.appendChild(lineNum);
    fullline.appendChild(el);
    container.appendChild(fullline);
    return el;
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
      assertAnnotation(annotateElementStub.args[0], el, 0, 5, 'tk-these token');
      assertAnnotation(annotateElementStub.args[1], el, 6, 3, 'tk-are token');
      assertAnnotation(
        annotateElementStub.args[2],
        el,
        10,
        5,
        'tk-words token'
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

    test('annotate does not add mouse handlers for longest word', () => {
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
      const words1 = queryAndAssert(line1, '.tk-words');
      assert.isTrue(words1.classList.contains('token'));
      makeMouseEvent(
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

      // After 200 ms the hover behavior should trigger.
      clock.tick(100);
      assert.equal(listener.pending, 2);
      assert.equal(_testOnly_allTasks.size, 0);
      assert.deepEqual(listener.shift(), [1, 1, Side.LEFT]);
      assert.deepEqual(listener.shift(), [2, 2, Side.RIGHT]);
    });

    test('highlighting mouse out before delay', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words', 2);
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-words');
      assert.isTrue(words1.classList.contains('token'));
      makeMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(listener.pending, 0);
      clock.tick(100);
      // Mouse out after 100ms but before hover delay.
      makeMouseEvent('mouseout', MockInteractions.middleOfNode(words1), words1);
      assert.equal(listener.pending, 0);
      clock.tick(100);
      assert.equal(listener.pending, 0);
      assert.equal(_testOnly_allTasks.size, 0);
    });

    test('clicking clears highlight', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words', 2);
      annotate(line2, Side.RIGHT, 2);
      const words1 = queryAndAssert(line1, '.tk-words');
      assert.isTrue(words1.classList.contains('token'));
      makeMouseEvent(
        'mouseover',
        MockInteractions.middleOfNode(words1),
        words1
      );
      assert.equal(listener.pending, 0);
      clock.tick(200);
      assert.equal(listener.pending, 2);
      listener.flush();
      assert.equal(listener.pending, 0);
      MockInteractions.click(container);
      assert.equal(listener.pending, 4);
      assert.deepEqual(listener.shift(), [1, 1, Side.LEFT]);
      assert.deepEqual(listener.shift(), [2, 2, Side.RIGHT]);
      assert.deepEqual(listener.shift(), [1, 1, Side.LEFT]);
      assert.deepEqual(listener.shift(), [2, 2, Side.RIGHT]);
    });
  });
});
