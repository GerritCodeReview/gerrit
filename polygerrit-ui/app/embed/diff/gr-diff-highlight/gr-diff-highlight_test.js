/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-diff-highlight.js';
import {_getTextOffset} from './gr-range-normalizer.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

// Splitting long lines in html into shorter rows breaks tests:
// zero-length text nodes and new lines are not expected in some places
/* eslint-disable max-len */
const basicFixture = fixtureFromTemplate(html`
<style>
      .tab-indicator:before {
        color: #C62828;
        /* >> character */
        content: '\\00BB';
      }
    </style>
    <gr-diff-highlight>
      <table id="diffTable">

        <tbody class="section both">
           <tr class="diff-row side-by-side" left-type="both" right-type="both">
            <td class="left lineNum" data-value="1"></td>
            <td class="content both"><div class="contentText">[1] Nam cum ad me in Cumanum salutandi causa uterque venisset,</div></td>
            <td class="right lineNum" data-value="1"></td>
            <td class="content both"><div class="contentText">[1] Nam cum ad me in Cumanum salutandi causa uterque</div></td>
          </tr>
        </tbody>

        <tbody class="section delta">
          <tr class="diff-row side-by-side" left-type="remove" right-type="add">
            <td class="left lineNum" data-value="2"></td>
            <!-- Next tag is formatted to eliminate zero-length text nodes. -->
            <td class="content remove"><div class="contentText">na💢ti <hl class="foo">te, inquit</hl>, sumus <hl class="bar">aliquando</hl> otiosum, <hl>certe</hl> a <hl><span class="tab-indicator" style="tab-size:8;">\u0009</span></hl>udiam, <hl>quid</hl> sit, <span class="tab-indicator" style="tab-size:8;">\u0009</span>quod <hl>Epicurum</hl></div></td>
            <td class="right lineNum" data-value="2"></td>
            <!-- Next tag is formatted to eliminate zero-length text nodes. -->
            <td class="content add"><div class="contentText">nacti , <hl>,</hl> sumus <hl><span class="tab-indicator" style="tab-size:8;">\u0009</span></hl> otiosum,  <span class="tab-indicator" style="tab-size:8;">\u0009</span> audiam,  sit, quod</div></td>
          </tr>
        </tbody>

<tbody class="section both">
          <tr class="diff-row side-by-side" left-type="both" right-type="both">
            <td class="left lineNum" data-value="138"></td>
            <td class="content both"><div class="contentText">[14] Nam cum ad me in Cumanum salutandi causa uterque venisset,</div></td>
            <td class="right lineNum" data-value="119"></td>
            <td class="content both"><div class="contentText">[14] Nam cum ad me in Cumanum salutandi causa uterque venisset,</div></td>
          </tr>
        </tbody>

        <tbody class="section delta">
          <tr class="diff-row side-by-side" left-type="remove" right-type="add">
            <td class="left lineNum" data-value="140"></td>
            <!-- Next tag is formatted to eliminate zero-length text nodes. -->
            <td class="content remove"><div class="contentText">na💢ti <hl class="foo">te, inquit</hl>, sumus <hl class="bar">aliquando</hl> otiosum, <hl>certe</hl> a <hl><span class="tab-indicator" style="tab-size:8;">\u0009</span></hl>udiam, <hl>quid</hl> sit, <span class="tab-indicator" style="tab-size:8;">\u0009</span>quod <hl>Epicurum</hl></div><div class="comment-thread">
                [Yet another random diff thread content here]
            </div></td>
            <td class="right lineNum" data-value="120"></td>
            <!-- Next tag is formatted to eliminate zero-length text nodes. -->
            <td class="content add"><div class="contentText">nacti , <hl>,</hl> sumus <hl><span class="tab-indicator" style="tab-size:8;">\u0009</span></hl> otiosum,  <span class="tab-indicator" style="tab-size:8;">\u0009</span> audiam,  sit, quod</div></td>
          </tr>
        </tbody>

        <tbody class="section both">
          <tr class="diff-row side-by-side" left-type="both" right-type="both">
            <td class="left lineNum" data-value="141"></td>
            <td class="content both"><div class="contentText">nam et<hl><span class="tab-indicator" style="tab-size:8;">\u0009</span></hl>complectitur<span class="tab-indicator" style="tab-size:8;">\u0009</span>verbis, quod vult, et dicit plane, quod intellegam;</div></td>
            <td class="right lineNum" data-value="130"></td>
            <td class="content both"><div class="contentText">nam et complectitur verbis, quod vult, et dicit plane, quod intellegam;</div></td>
          </tr>
        </tbody>

        <tbody class="section contextControl">
          <tr class="diff-row side-by-side" left-type="contextControl" right-type="contextControl">
            <td class="left contextLineNum"></td>
            <td>
              <gr-button>+10↑</gr-button>
              -
              <gr-button>Show 21 common lines</gr-button>
              -
              <gr-button>+10↓</gr-button>
            </td>
            <td class="right contextLineNum"></td>
            <td>
              <gr-button>+10↑</gr-button>
              -
              <gr-button>Show 21 common lines</gr-button>
              -
              <gr-button>+10↓</gr-button>
            </td>
          </tr>
        </tbody>

        <tbody class="section delta total">
          <tr class="diff-row side-by-side" left-type="blank" right-type="add">
            <td class="left"></td>
            <td class="blank"></td>
            <td class="right lineNum" data-value="146"></td>
            <td class="content add"><div class="contentText">[17] Quid igitur est? inquit; audire enim cupio, quid non probes. Principio, inquam,</div></td>
          </tr>
        </tbody>

        <tbody class="section both">
          <tr class="diff-row side-by-side" left-type="both" right-type="both">
            <td class="left lineNum" data-value="165"></td>
            <td class="content both"><div class="contentText"></div></td>
            <td class="right lineNum" data-value="147"></td>
            <td class="content both"><div class="contentText">in physicis, <hl><span class="tab-indicator" style="tab-size:8;">\u0009</span></hl> quibus maxime gloriatur, primum totus est alienus. Democritea dicit</div></td>
          </tr>
        </tbody>

      </table>
    </gr-diff-highlight>
`);
/* eslint-enable max-len */

suite('gr-diff-highlight', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate()[1];
  });

  suite('comment events', () => {
    let builder;

    setup(() => {
      builder = {
        getContentsByLineRange: sinon.stub().returns([]),
        getLineElByChild: sinon.stub().returns({}),
        getSideByLineEl: sinon.stub().returns('other-side'),
      };
      element._cachedDiffBuilder = builder;
    });

    test('comment-thread-mouseenter from line comments is ignored', () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', 3);
      element.appendChild(threadEl);
      element.commentRanges = [{side: 'right'}];

      sinon.stub(element, 'set');
      threadEl.dispatchEvent(new CustomEvent(
          'comment-thread-mouseenter', {bubbles: true, composed: true}));
      assert.isFalse(element.set.called);
    });

    test('comment-thread-mouseenter from ranged comment causes set', () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', 3);
      threadEl.setAttribute('range', JSON.stringify({
        start_line: 3,
        start_character: 4,
        end_line: 5,
        end_character: 6,
      }));
      element.appendChild(threadEl);
      element.commentRanges = [{side: 'right', range: {
        start_line: 3,
        start_character: 4,
        end_line: 5,
        end_character: 6,
      }}];

      sinon.stub(element, 'set');
      threadEl.dispatchEvent(new CustomEvent(
          'comment-thread-mouseenter', {bubbles: true, composed: true}));
      assert.isTrue(element.set.called);
      const args = element.set.lastCall.args;
      assert.deepEqual(args[0], ['commentRanges', 0, 'hovering']);
      assert.deepEqual(args[1], true);
    });

    test('comment-thread-mouseleave from line comments is ignored', () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', 3);
      element.appendChild(threadEl);
      element.commentRanges = [{side: 'right'}];

      sinon.stub(element, 'set');
      threadEl.dispatchEvent(new CustomEvent(
          'comment-thread-mouseleave', {bubbles: true, composed: true}));
      assert.isFalse(element.set.called);
    });

    test(`create-range-comment for range when create-comment-requested
          is fired`, () => {
      sinon.stub(element, '_removeActionBox');
      element.selectedRange = {
        side: 'left',
        range: {
          start_line: 7,
          start_character: 11,
          end_line: 24,
          end_character: 42,
        },
      };
      const requestEvent = new CustomEvent('create-comment-requested');
      let createRangeEvent;
      element.addEventListener('create-range-comment', e => {
        createRangeEvent = e;
      });
      element.dispatchEvent(requestEvent);
      assert.deepEqual(element.selectedRange, createRangeEvent.detail);
      assert.isTrue(element._removeActionBox.called);
    });
  });

  suite('selection', () => {
    let diff;
    let builder;
    let contentStubs;

    const stubContent = (line, side, opt_child) => {
      const contentTd = diff.querySelector(
          `.${side}.lineNum[data-value="${line}"] ~ .content`);
      const contentText = contentTd.querySelector('.contentText');
      const lineEl = diff.querySelector(
          `.${side}.lineNum[data-value="${line}"]`);
      contentStubs.push({
        lineEl,
        contentTd,
        contentText,
      });
      builder.getContentTdByLineEl.withArgs(lineEl).returns(contentTd);
      builder.getLineNumberByChild.withArgs(lineEl).returns(line);
      builder.getContentTdByLine.withArgs(line, side).returns(contentTd);
      builder.getSideByLineEl.withArgs(lineEl).returns(side);
      return contentText;
    };

    const emulateSelection = (startNode, startOffset, endNode, endOffset) => {
      const selection = document.getSelection();
      const range = document.createRange();
      range.setStart(startNode, startOffset);
      range.setEnd(endNode, endOffset);
      selection.addRange(range);
      element._handleSelection(selection);
    };

    const getLineElByChild = node => {
      const stubs = contentStubs.find(stub => stub.contentTd.contains(node));
      return stubs && stubs.lineEl;
    };

    setup(() => {
      contentStubs = [];
      stub('gr-selection-action-box', 'placeAbove');
      stub('gr-selection-action-box', 'placeBelow');
      diff = element.querySelector('#diffTable');
      builder = {
        getContentTdByLine: sinon.stub(),
        getContentTdByLineEl: sinon.stub(),
        getLineElByChild,
        getLineNumberByChild: sinon.stub(),
        getSideByLineEl: sinon.stub(),
      };
      element._cachedDiffBuilder = builder;
    });

    teardown(() => {
      contentStubs = null;
      document.getSelection().removeAllRanges();
    });

    test('single first line', () => {
      const content = stubContent(1, 'right');
      sinon.spy(element, '_positionActionBox');
      emulateSelection(content.firstChild, 5, content.firstChild, 12);
      const actionBox = element.shadowRoot
          .querySelector('gr-selection-action-box');
      assert.isTrue(actionBox.positionBelow);
    });

    test('multiline starting on first line', () => {
      const startContent = stubContent(1, 'right');
      const endContent = stubContent(2, 'right');
      sinon.spy(element, '_positionActionBox');
      emulateSelection(
          startContent.firstChild, 10, endContent.lastChild, 7);
      const actionBox = element.shadowRoot
          .querySelector('gr-selection-action-box');
      assert.isTrue(actionBox.positionBelow);
    });

    test('single line', () => {
      const content = stubContent(138, 'left');
      sinon.spy(element, '_positionActionBox');
      emulateSelection(content.firstChild, 5, content.firstChild, 12);
      const actionBox = element.shadowRoot
          .querySelector('gr-selection-action-box');
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 138,
        start_character: 5,
        end_line: 138,
        end_character: 12,
      });
      assert.equal(side, 'left');
      assert.notOk(actionBox.positionBelow);
    });

    test('multiline', () => {
      const startContent = stubContent(119, 'right');
      const endContent = stubContent(120, 'right');
      sinon.spy(element, '_positionActionBox');
      emulateSelection(
          startContent.firstChild, 10, endContent.lastChild, 7);
      const actionBox = element.shadowRoot
          .querySelector('gr-selection-action-box');
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 119,
        start_character: 10,
        end_line: 120,
        end_character: 36,
      });
      assert.equal(side, 'right');
      assert.notOk(actionBox.positionBelow);
    });

    test('multiple ranges aka firefox implementation', () => {
      const startContent = stubContent(119, 'right');
      const endContent = stubContent(120, 'right');

      const startRange = document.createRange();
      startRange.setStart(startContent.firstChild, 10);
      startRange.setEnd(startContent.firstChild, 11);

      const endRange = document.createRange();
      endRange.setStart(endContent.lastChild, 6);
      endRange.setEnd(endContent.lastChild, 7);

      const getRangeAtStub = sinon.stub();
      getRangeAtStub
          .onFirstCall().returns(startRange)
          .onSecondCall()
          .returns(endRange);
      const selection = {
        rangeCount: 2,
        getRangeAt: getRangeAtStub,
        removeAllRanges: sinon.stub(),
      };
      element._handleSelection(selection);
      const {range} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 119,
        start_character: 10,
        end_line: 120,
        end_character: 36,
      });
    });

    test('multiline grow end highlight over tabs', () => {
      const startContent = stubContent(119, 'right');
      const endContent = stubContent(120, 'right');
      emulateSelection(startContent.firstChild, 10, endContent.firstChild, 2);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 119,
        start_character: 10,
        end_line: 120,
        end_character: 2,
      });
      assert.equal(side, 'right');
    });

    test('collapsed', () => {
      const content = stubContent(138, 'left');
      emulateSelection(content.firstChild, 5, content.firstChild, 5);
      assert.isOk(document.getSelection().getRangeAt(0).startContainer);
      assert.isFalse(!!element.selectedRange);
    });

    test('starts inside hl', () => {
      const content = stubContent(140, 'left');
      const hl = content.querySelector('.foo');
      emulateSelection(hl.firstChild, 2, hl.nextSibling, 7);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 8,
        end_line: 140,
        end_character: 23,
      });
      assert.equal(side, 'left');
    });

    test('ends inside hl', () => {
      const content = stubContent(140, 'left');
      const hl = content.querySelector('.bar');
      emulateSelection(hl.previousSibling, 2, hl.firstChild, 3);
      const {range} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 18,
        end_line: 140,
        end_character: 27,
      });
    });

    test('multiple hl', () => {
      const content = stubContent(140, 'left');
      const hl = content.querySelectorAll('hl')[4];
      emulateSelection(content.firstChild, 2, hl.firstChild, 2);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 2,
        end_line: 140,
        end_character: 61,
      });
      assert.equal(side, 'left');
    });

    test('starts outside of diff', () => {
      const contentText = stubContent(140, 'left');
      const contentTd = contentText.parentElement;

      emulateSelection(contentTd.parentElement, 0,
          contentText.firstChild, 2);
      assert.isFalse(!!element.selectedRange);
    });

    test('ends outside of diff', () => {
      const content = stubContent(140, 'left');
      emulateSelection(content.nextElementSibling.firstChild, 2,
          content.firstChild, 2);
      assert.isFalse(!!element.selectedRange);
    });

    test('starts and ends on different sides', () => {
      const startContent = stubContent(140, 'left');
      const endContent = stubContent(130, 'right');
      emulateSelection(startContent.firstChild, 2, endContent.firstChild, 2);
      assert.isFalse(!!element.selectedRange);
    });

    test('starts in comment thread element', () => {
      const startContent = stubContent(140, 'left');
      const comment = startContent.parentElement.querySelector(
          '.comment-thread');
      const endContent = stubContent(141, 'left');
      emulateSelection(comment.firstChild, 2, endContent.firstChild, 4);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 83,
        end_line: 141,
        end_character: 4,
      });
      assert.equal(side, 'left');
    });

    test('ends in comment thread element', () => {
      const content = stubContent(140, 'left');
      const comment = content.parentElement.querySelector(
          '.comment-thread');
      emulateSelection(content.firstChild, 4, comment.firstChild, 1);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 4,
        end_line: 140,
        end_character: 83,
      });
      assert.equal(side, 'left');
    });

    test('starts in context element', () => {
      const contextControl =
          diff.querySelector('.contextControl').querySelector('gr-button');
      const content = stubContent(146, 'right');
      emulateSelection(contextControl, 0, content.firstChild, 7);
      // TODO (viktard): Select nearest line.
      assert.isFalse(!!element.selectedRange);
    });

    test('ends in context element', () => {
      const contextControl =
          diff.querySelector('.contextControl').querySelector('gr-button');
      const content = stubContent(141, 'left');
      emulateSelection(content.firstChild, 2, contextControl, 1);
      // TODO (viktard): Select nearest line.
      assert.isFalse(!!element.selectedRange);
    });

    test('selection containing context element', () => {
      const startContent = stubContent(130, 'right');
      const endContent = stubContent(146, 'right');
      emulateSelection(startContent.firstChild, 3, endContent.firstChild, 14);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 130,
        start_character: 3,
        end_line: 146,
        end_character: 14,
      });
      assert.equal(side, 'right');
    });

    test('ends at a tab', () => {
      const content = stubContent(140, 'left');
      emulateSelection(
          content.firstChild, 1, content.querySelector('span'), 0);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 1,
        end_line: 140,
        end_character: 51,
      });
      assert.equal(side, 'left');
    });

    test('starts at a tab', () => {
      const content = stubContent(140, 'left');
      emulateSelection(
          content.querySelectorAll('hl')[3], 0,
          content.querySelectorAll('span')[1].nextSibling, 1);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 140,
        start_character: 51,
        end_line: 140,
        end_character: 71,
      });
      assert.equal(side, 'left');
    });

    test('properly accounts for syntax highlighting', () => {
      const content = stubContent(140, 'left');
      const spy = sinon.spy(element, '_normalizeRange');
      emulateSelection(
          content.querySelectorAll('hl')[3], 0,
          content.querySelectorAll('span')[1], 0);
      const spyCall = spy.getCall(0);
      const range = document.getSelection().getRangeAt(0);
      assert.notDeepEqual(spyCall.returnValue, range);
    });

    test('GrRangeNormalizer._getTextOffset computes text offset', () => {
      let content = stubContent(140, 'left');
      let child = content.lastChild.lastChild;
      let result = _getTextOffset(content, child);
      assert.equal(result, 75);
      content = stubContent(146, 'right');
      child = content.lastChild;
      result = _getTextOffset(content, child);
      assert.equal(result, 0);
    });

    test('_fixTripleClickSelection', () => {
      const startContent = stubContent(119, 'right');
      const endContent = stubContent(120, 'right');
      emulateSelection(startContent.firstChild, 0, endContent.firstChild, 0);
      const {range, side} = element.selectedRange;
      assert.deepEqual(range, {
        start_line: 119,
        start_character: 0,
        end_line: 119,
        end_character: element._getLength(startContent),
      });
      assert.equal(side, 'right');
    });
  });
});

