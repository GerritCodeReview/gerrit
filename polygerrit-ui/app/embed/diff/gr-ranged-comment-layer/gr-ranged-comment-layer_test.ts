/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import '../gr-diff/gr-diff-line';
import './gr-ranged-comment-layer';
import {
  CommentRangeLayer,
  GrRangedCommentLayer,
} from './gr-ranged-comment-layer';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {Side} from '../../../api/diff';
import {SinonStub} from 'sinon';

const basicFixture = fixtureFromElement('gr-ranged-comment-layer');

suite('gr-ranged-comment-layer', () => {
  let element: GrRangedCommentLayer;

  setup(() => {
    const initialCommentRanges: CommentRangeLayer[] = [
      {
        side: Side.LEFT,
        range: {
          end_character: 9,
          end_line: 39,
          start_character: 6,
          start_line: 36,
        },
        rootId: 'a',
        hovering: false,
      },
      {
        side: Side.RIGHT,
        range: {
          end_character: 22,
          end_line: 12,
          start_character: 10,
          start_line: 10,
        },
        rootId: 'b',
        hovering: false,
      },
      {
        side: Side.RIGHT,
        range: {
          end_character: 15,
          end_line: 100,
          start_character: 5,
          start_line: 100,
        },
        rootId: 'c',
        hovering: false,
      },
      {
        side: Side.RIGHT,
        range: {
          end_character: 2,
          end_line: 55,
          start_character: 32,
          start_line: 55,
        },
        rootId: 'd',
        hovering: false,
      },
      {
        side: Side.RIGHT,
        range: {
          end_character: 1,
          end_line: 71,
          start_character: 1,
          start_line: 60,
        },
        rootId: 'e',
        hovering: false,
      },
    ];

    element = basicFixture.instantiate();
    element.commentRanges = initialCommentRanges;
  });

  suite('annotate', () => {
    let el: HTMLDivElement;
    let line: GrDiffLine;
    let annotateElementStub: SinonStub;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      el = document.createElement('div');
      el.setAttribute('data-side', Side.LEFT);
      line = new GrDiffLine(GrDiffLineType.BOTH);
      line.text = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit,';
    });

    test('type=Remove no-comment', () => {
      line = new GrDiffLine(GrDiffLineType.REMOVE);
      line.beforeNumber = 40;

      element.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('type=Remove has-comment', () => {
      line = new GrDiffLine(GrDiffLineType.REMOVE);
      line.beforeNumber = 36;
      const expectedStart = 6;
      const expectedLength = line.text.length - expectedStart;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(
        lastCall.args[3],
        'style-scope gr-diff range rangeHighlight generated_a'
      );
    });

    test('type=Remove has-comment hovering', () => {
      line = new GrDiffLine(GrDiffLineType.REMOVE);
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
        lastCall.args[3],
        'style-scope gr-diff range rangeHoverHighlight generated_a'
      );
    });

    test('type=Both has-comment', () => {
      line = new GrDiffLine(GrDiffLineType.BOTH);
      line.beforeNumber = 36;

      const expectedStart = 6;
      const expectedLength = line.text.length - expectedStart;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(
        lastCall.args[3],
        'style-scope gr-diff range rangeHighlight generated_a'
      );
    });

    test('type=Both has-comment off side', () => {
      line = new GrDiffLine(GrDiffLineType.BOTH);
      line.beforeNumber = 36;
      el.setAttribute('data-side', Side.RIGHT);

      element.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('type=Add has-comment', () => {
      line = new GrDiffLine(GrDiffLineType.ADD);
      line.afterNumber = 12;
      el.setAttribute('data-side', Side.RIGHT);

      const expectedStart = 0;
      const expectedLength = 22;

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      const lastCall = annotateElementStub.lastCall;
      assert.equal(lastCall.args[0], el);
      assert.equal(lastCall.args[1], expectedStart);
      assert.equal(lastCall.args[2], expectedLength);
      assert.equal(
        lastCall.args[3],
        'style-scope gr-diff range rangeHighlight generated_b'
      );
    });

    test('long range comment', () => {
      line = new GrDiffLine(GrDiffLineType.ADD);
      line.afterNumber = 65;
      el.setAttribute('data-side', Side.RIGHT);

      element.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementStub.called);
      assert.equal(
        annotateElementStub.lastCall.args[3],
        'style-scope gr-diff range generated_e'
      );
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
    assert.equal(lastCall.args[2], Side.RIGHT);
  });

  test('_handleCommentRangesChange splice in', () => {
    const notifyStub = sinon.stub();
    element.addListener(notifyStub);

    element.splice('commentRanges', 1, 0, {
      side: Side.LEFT,
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
    assert.equal(lastCall.args[2], Side.LEFT);
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
      side: Side.LEFT,
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
    for (let i = 36; i <= 39; i++) {
      leftKeys.push('' + i);
    }
    assert.deepEqual(
      Object.keys(element._rangesMap.left).sort(),
      leftKeys.sort()
    );

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

    // The right has four ranged comments: 10-12, 55-55, 60-71, 100-100
    const rightKeys = [];
    for (let i = 10; i <= 12; i++) {
      rightKeys.push('' + i);
    }
    for (let i = 60; i <= 71; i++) {
      rightKeys.push('' + i);
    }
    rightKeys.push('55', '100');
    assert.deepEqual(
      Object.keys(element._rangesMap.right).sort(),
      rightKeys.sort()
    );

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
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.afterNumber = 55;
    line.text = '_getRangesForLine normalizes invalid ranges';
    const ranges = element._getRangesForLine(line, Side.RIGHT);
    assert.equal(ranges.length, 1);
    const range = ranges[0];
    assert.isTrue(range.start < range.end, 'start and end are normalized');
    assert.equal(range.end, line.text.length);
  });
});
