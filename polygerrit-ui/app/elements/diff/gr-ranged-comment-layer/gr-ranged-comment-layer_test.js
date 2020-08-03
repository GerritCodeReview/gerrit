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
import '../gr-diff/gr-diff-line.js';
import './gr-ranged-comment-layer.js';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation.js';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';

const basicFixture = fixtureFromElement('gr-ranged-comment-layer');

suite('gr-ranged-comment-layer', () => {
  let element;

  setup(() => {
    const initialCommentRanges = [
      {
        side: 'left',
        range: {
          end_character: 9,
          end_line: 39,
          start_character: 6,
          start_line: 36,
        },
        rootId: 'a',
      },
      {
        side: 'right',
        range: {
          end_character: 22,
          end_line: 12,
          start_character: 10,
          start_line: 10,
        },
        rootId: 'b',
      },
      {
        side: 'right',
        range: {
          end_character: 15,
          end_line: 100,
          start_character: 5,
          start_line: 100,
        },
        rootId: 'c',
      },
      {
        side: 'right',
        range: {
          end_character: 2,
          end_line: 55,
          start_character: 32,
          start_line: 55,
        },
        rootId: 'd',
      },
    ];

    element = basicFixture.instantiate();
    element.commentRanges = initialCommentRanges;
  });

  suite('annotate', () => {
    let el;
    let line;
    let annotateElementStub;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      el = document.createElement('div');
      el.setAttribute('data-side', 'left');
      line = new GrDiffLine(GrDiffLineType.BOTH);
      line.text = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit,';
    });

    test('type=Remove no-comment', () => {
      line.type = GrDiffLineType.REMOVE;
      line.beforeNumber = 40;

      element.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('type=Remove has-comment', () => {
      line.type = GrDiffLineType.REMOVE;
      line.beforeNumber = 36;
      const expectedStart = 6;
      const expectedLength = line.text.length - expectedStart;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(lastCall.args[3], 'style-scope gr-diff range generated_a');
    });

    test('type=Remove has-comment hovering', () => {
      line.type = GrDiffLineType.REMOVE;
      line.beforeNumber = 36;
      element.set(['commentRanges', 0, 'hovering'], true);

      const expectedStart = 6;
      const expectedLength = line.text.length - expectedStart;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(
          lastCall.args[3], 'style-scope gr-diff rangeHighlight generated_a'
      );
    });

    test('type=Both has-comment', () => {
      line.type = GrDiffLineType.BOTH;
      line.beforeNumber = 36;

      const expectedStart = 6;
      const expectedLength = line.text.length - expectedStart;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(lastCall.args[3], 'style-scope gr-diff range generated_a');
    });

    test('type=Both has-comment off side', () => {
      line.type = GrDiffLineType.BOTH;
      line.beforeNumber = 36;
      el.setAttribute('data-side', 'right');

      element.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('type=Add has-comment', () => {
      line.type = GrDiffLineType.ADD;
      line.afterNumber = 12;
      el.setAttribute('data-side', 'right');

      const expectedStart = 0;
      const expectedLength = 22;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(lastCall.args[3], 'style-scope gr-diff range generated_b');
    });
  });

  test('_handleCommentRangesChange overwrite', () => {
    element.set('commentRanges', []);

    assert.equal(Object.keys(element._rangesMap.left).length, 0);
    assert.equal(Object.keys(element._rangesMap.right).length, 0);
  });

  test('_handleCommentRangesChange hovering', () => {
    const notifyStub = sinon.stub();
    element.addListener(notifyStub);
    const updateRangesMapSpy = sinon.spy(element, '_updateRangesMap');

    element.set(['commentRanges', 1, 'hovering'], true);

    // notify will be skipped for hovering
    assert.isFalse(notifyStub.called);

    assert.isTrue(updateRangesMapSpy.called);
  });

  test('_handleCommentRangesChange splice out', () => {
    const notifyStub = sinon.stub();
    element.addListener(notifyStub);

    element.splice('commentRanges', 1, 1);

    assert.isTrue(notifyStub.called);
    const lastCall = notifyStub.lastCall;
    assert.equal(lastCall.args[0], 10);
    assert.equal(lastCall.args[1], 12);
    assert.equal(lastCall.args[2], 'right');
  });

  test('_handleCommentRangesChange splice in', () => {
    const notifyStub = sinon.stub();
    element.addListener(notifyStub);

    element.splice('commentRanges', 1, 0, {
      side: 'left',
      range: {
        end_character: 15,
        end_line: 275,
        start_character: 5,
        start_line: 250,
      },
    });

    assert.isTrue(notifyStub.called);
    const lastCall = notifyStub.lastCall;
    assert.equal(lastCall.args[0], 250);
    assert.equal(lastCall.args[1], 275);
    assert.equal(lastCall.args[2], 'left');
  });

  test('_handleCommentRangesChange mixed actions', () => {
    const notifyStub = sinon.stub();
    element.addListener(notifyStub);
    const updateRangesMapSpy = sinon.spy(element, '_updateRangesMap');

    element.set(['commentRanges', 1, 'hovering'], true);
    assert.isTrue(updateRangesMapSpy.callCount === 1);
    element.splice('commentRanges', 1, 1);
    assert.isTrue(updateRangesMapSpy.callCount === 2);
    element.splice('commentRanges', 1, 1);
    assert.isTrue(updateRangesMapSpy.callCount === 3);
    element.splice('commentRanges', 1, 0, {
      side: 'left',
      range: {
        end_character: 15,
        end_line: 275,
        start_character: 5,
        start_line: 250,
      },
    });
    assert.isTrue(updateRangesMapSpy.callCount === 4);
    element.set(['commentRanges', 2, 'hovering'], true);
    assert.isTrue(updateRangesMapSpy.callCount === 5);
  });

  test('_computeCommentMap creates maps correctly', () => {
    // There is only one ranged comment on the left, but it spans ll.36-39.
    const leftKeys = [];
    for (let i = 36; i <= 39; i++) { leftKeys.push('' + i); }
    assert.deepEqual(Object.keys(element._rangesMap.left).sort(),
        leftKeys.sort());

    assert.equal(element._rangesMap.left[36].length, 1);
    assert.equal(element._rangesMap.left[36][0].start, 6);
    assert.equal(element._rangesMap.left[36][0].end, -1);

    assert.equal(element._rangesMap.left[37].length, 1);
    assert.equal(element._rangesMap.left[37][0].start, 0);
    assert.equal(element._rangesMap.left[37][0].end, -1);

    assert.equal(element._rangesMap.left[38].length, 1);
    assert.equal(element._rangesMap.left[38][0].start, 0);
    assert.equal(element._rangesMap.left[38][0].end, -1);

    assert.equal(element._rangesMap.left[39].length, 1);
    assert.equal(element._rangesMap.left[39][0].start, 0);
    assert.equal(element._rangesMap.left[39][0].end, 9);

    // The right has two ranged comments, one spanning ll.10-12 and the other
    // on line 100.
    const rightKeys = [];
    for (let i = 10; i <= 12; i++) { rightKeys.push('' + i); }
    rightKeys.push('55', '100');
    assert.deepEqual(Object.keys(element._rangesMap.right).sort(),
        rightKeys.sort());

    assert.equal(element._rangesMap.right[10].length, 1);
    assert.equal(element._rangesMap.right[10][0].start, 10);
    assert.equal(element._rangesMap.right[10][0].end, -1);

    assert.equal(element._rangesMap.right[11].length, 1);
    assert.equal(element._rangesMap.right[11][0].start, 0);
    assert.equal(element._rangesMap.right[11][0].end, -1);

    assert.equal(element._rangesMap.right[12].length, 1);
    assert.equal(element._rangesMap.right[12][0].start, 0);
    assert.equal(element._rangesMap.right[12][0].end, 22);

    assert.equal(element._rangesMap.right[100].length, 1);
    assert.equal(element._rangesMap.right[100][0].start, 5);
    assert.equal(element._rangesMap.right[100][0].end, 15);
  });

  test('_getRangesForLine normalizes invalid ranges', () => {
    const line = {
      afterNumber: 55,
      text: '_getRangesForLine normalizes invalid ranges',
    };
    const ranges = element._getRangesForLine(line, 'right');
    assert.equal(ranges.length, 1);
    const range = ranges[0];
    assert.isTrue(range.start < range.end, 'start and end are normalized');
    assert.equal(range.end, line.text.length);
  });
});

