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

import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {Side} from '../../../api/diff';
import '../../../test/common-test-setup-karma';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {TokenHighlightLayer} from './token-highlight-layer';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import sinon from 'sinon/pkg/sinon-esm';
import {queryAndAssert} from '../../../test/test-utils';

const blankFixture = fixtureFromElement('div');
class LazyListener {
  // Either results has pending results to be waited on, or waiters has
  // pending waiters for a notification.
  private results: any[][] = [];
  private waiters: ((args: any[]) => void)[] = [];

  notify(...args: any[]) {
    // Either results or waiters is empty. They cannot both be non-empty.
    if (this.waiters.length > 0) {
      this.waiters.shift()?.(args);
    } else {
      this.results.push(args);
    }
  }

  get pending(): number {
    return this.results.length;
  }

  waitForNotify(): Promise<any[] | undefined> {
    // If we have pending results, pop one of them.
    if (this.pending > 0) {
      return Promise.resolve(this.results.shift());
    } else {
      return new Promise(resolve => {
        this.waiters.push(resolve);
      })
    }
  }
}

suite('token-highlight-layer', () => {
  let container: HTMLElement;
  let listener: LazyListener;
  let lineNumber: HTMLElement;
  let highlighter: TokenHighlightLayer;

  setup(async () => {
    listener = new LazyListener();
    highlighter = new TokenHighlightLayer();
    highlighter.addListener((...args) => listener.notify(...args));
    lineNumber = document.createElement('td');
    container = blankFixture.instantiate();
  });

  function annotate(el: HTMLElement, side: Side = Side.LEFT, line: number = 0) {
    const diffLine = new GrDiffLine(GrDiffLineType.BOTH);
    diffLine.afterNumber = line;
    diffLine.beforeNumber = line;
    highlighter.annotate(el, lineNumber, diffLine, side);
  }

  function createLine(text: string) {
    const el = document.createElement('div');
    el.textContent = text;
    container.appendChild(el);
    return el;
  }

  suite('annotate', () => {
    function assertAnnotation(
      args: any[],
      el: HTMLElement,
      start: number,
      length: number,
      cssClass: string) {
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
      assertAnnotation(annotateElementStub.args[2], el, 10, 5, 'tk-words token');
    });

    test('annotate adds mouse handlers', () => {
      const el = createLine('these are words');
      const addEventListenerStub = sinon.stub(el, 'addEventListener');
      annotate(el);
      assert.isTrue(addEventListenerStub.calledTwice);
      assert.equal(addEventListenerStub.args[0][0], 'mouseover');
      assert.equal(addEventListenerStub.args[1][0], 'mouseout');
    });

    test('annotate does not add mouse handlers for empty', () => {
      const el = createLine('  ');
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
      let words1 = queryAndAssert(line1, '.tk-words');
      assert.isTrue(words1.classList.contains('token'));
      MockInteractions.move(container, {x: 0, y: 0},
        MockInteractions.middleOfNode(words1));
      assert.equal(listener.pending, 0);
      clock.tick(100);
      await flush();
      assert.equal(listener.pending, 0);
      clock.tick(100);
      await flush();
      assert.equal(listener.pending, 1);
      let firstCall = await listener.waitForNotify();
      assert.equal(firstCall, [0, 0, Side.LEFT]);
    })

    test('highlighting across lines', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      annotate(line1);
      const line2 = createLine('three words');
      annotate(line2, Side.RIGHT, 1);
      let words1 = queryAndAssert(line1, '.tk-words');
      let words2 = queryAndAssert(line2, '.tk-words');
      assert.isTrue(words1.classList.contains('token'));
      assert.isTrue(words2.classList.contains('token'));
      MockInteractions.move(container, {x: 0, y: 0},
        MockInteractions.middleOfNode(words1));
      clock.tick(200);
      await flush();
      let firstCall = await listener.waitForNotify();
      assert.equal(firstCall, [0, 0, Side.LEFT]);
      let secondCall = await listener.waitForNotify();
      assert.equal(secondCall, [1, 1, Side.RIGHT]);
    })
  });
});
