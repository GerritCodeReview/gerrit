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


suite('token-highlight-layer', () => {
  let container : HTMLElement;
  let lineNumber : HTMLElement;
  let highlighter: TokenHighlightLayer;
  setup(async () => {
    highlighter = new TokenHighlightLayer();
    lineNumber = document.createElement('td');
    container = blankFixture.instantiate();
  });

  teardown(() => {
    highlighter.detach();
  });

  function annotate(el: HTMLElement, side: Side = Side.LEFT, line?: GrDiffLine) {
    line = line ?? new GrDiffLine(GrDiffLineType.BOTH);
    highlighter.annotate(el, lineNumber, line, side);
  }

  suite('annotate', () => {
    let el: HTMLElement;

    setup(() => {
      el = document.createElement('div');
      el.textContent = 'these are words';
      container.appendChild(el);
    })

    function assertAnnotation(
      args: any[], start: number, length: number, cssClass: string) {
      assert.equal(args[0], el);
      assert.equal(args[1], start);
      assert.equal(args[2], length);
      assert.equal(args[3], cssClass);
    }

    test('annotate adds css token', () => {
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      annotate(el);
      assert.isTrue(annotateElementStub.calledThrice);
      assertAnnotation(annotateElementStub.args[0], 0, 5, 'tk-these token');
      assertAnnotation(annotateElementStub.args[1], 6, 3, 'tk-are token');
      assertAnnotation(annotateElementStub.args[2], 10, 5, 'tk-words token');
    });

    test('annotate adds mouse handlers', () => {
      const addEventListenerStub = sinon.stub(el, 'addEventListener');
      annotate(el);
      assert.isTrue(addEventListenerStub.calledTwice);
      assert.equal(addEventListenerStub.args[0][0], 'mouseover');
      assert.equal(addEventListenerStub.args[1][0], 'mouseout');
    });
  });

  suite('highlight', () => {
    function createLine(text: string) {
      const el = document.createElement('div');
      el.textContent = text;
      container.appendChild(el);
      annotate(el);
      return el;
    }

    test('highlighting across lines', async () => {
      const clock = sinon.useFakeTimers();
      const line1 = createLine('two words');
      const line2 = createLine('three words');
      await flush();
      let words1 = queryAndAssert(line1, '.tk-words');
      let words2 = queryAndAssert(line2, '.tk-words');
      MockInteractions.move(container, {x: 0, y: 0},
        MockInteractions.middleOfNode(words1));
      clock.tick(100);
      assert.isTrue(words1.classList.contains('token'));
      assert.isTrue(words2.classList.contains('token'));
      clock.tick(100);
      assert.isTrue(words1.classList.contains('token-highlight'));
      assert.isTrue(words2.classList.contains('token-highlight'));
    })
  });
});
