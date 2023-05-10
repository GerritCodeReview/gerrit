/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../gr-diff/gr-diff-line';
import './gr-ranged-comment-layer';
import {
  CommentRangeLayer,
  GrRangedCommentLayer,
} from './gr-ranged-comment-layer';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrDiffLineType, Side} from '../../../api/diff';
import {SinonStub} from 'sinon';
import {assert} from '@open-wc/testing';
import {GrAnnotationImpl} from '../gr-diff-highlight/gr-annotation';

const rangeA: CommentRangeLayer = {
  side: Side.LEFT,
  line: 39,
  range: {
    end_character: 9,
    end_line: 39,
    start_character: 6,
    start_line: 36,
  },
  rootId: 'a',
};

const rangeB: CommentRangeLayer = {
  side: Side.RIGHT,
  line: 12,
  range: {
    end_character: 22,
    end_line: 12,
    start_character: 10,
    start_line: 10,
  },
  rootId: 'b',
};

const rangeC: CommentRangeLayer = {
  side: Side.RIGHT,
  line: 100,
  range: {
    end_character: 15,
    end_line: 100,
    start_character: 5,
    start_line: 100,
  },
};

const rangeD: CommentRangeLayer = {
  side: Side.RIGHT,
  line: 55,
  range: {
    end_character: 2,
    end_line: 55,
    start_character: 32,
    start_line: 55,
  },
  rootId: 'd',
};

const rangeE: CommentRangeLayer = {
  side: Side.RIGHT,
  line: 71,
  range: {
    end_character: 1,
    end_line: 71,
    start_character: 1,
    start_line: 60,
  },
};

const rangeF: CommentRangeLayer = {
  side: Side.RIGHT,
  line: 24,
  range: {
    end_character: 0,
    end_line: 24,
    start_character: 0,
    start_line: 23,
  },
};

suite('gr-ranged-comment-layer', () => {
  let element: GrRangedCommentLayer;

  setup(() => {
    const initialCommentRanges: CommentRangeLayer[] = [
      rangeA,
      rangeB,
      rangeC,
      rangeD,
      rangeE,
      rangeF,
    ];

    element = new GrRangedCommentLayer();
    element.updateRanges(initialCommentRanges);
  });

  suite('annotate', () => {
    let el: HTMLDivElement;
    let line: GrDiffLine;
    let annotateElementStub: SinonStub;
    const lineNumberEl = document.createElement('td');

    function assertHasRange(
      commentRange: CommentRangeLayer,
      hasRange: boolean
    ) {
      assertHasRangeOn(
        commentRange.side,
        commentRange.range.start_line,
        hasRange
      );
    }

    function assertHasRangeOn(
      side: Side,
      lineNumber: number,
      hasRange: boolean
    ) {
      line = new GrDiffLine(GrDiffLineType.BOTH);
      if (side === Side.LEFT) line.beforeNumber = lineNumber;
      if (side === Side.RIGHT) line.afterNumber = lineNumber;
      el.setAttribute('data-side', side);

      element.annotate(el, lineNumberEl, line);

      assert.equal(annotateElementStub.called, hasRange);
      annotateElementStub.reset();
    }

    setup(() => {
      annotateElementStub = sinon.stub(GrAnnotationImpl, 'annotateElement');
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
        'gr-diff range rangeHighlight generated_a'
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
        'gr-diff range rangeHighlight generated_a'
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
        'gr-diff range rangeHighlight generated_b'
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
        'gr-diff range generated_right-60-1-71-1'
      );
    });

    test('do not annotate lines with end_character 0', () => {
      line = new GrDiffLine(GrDiffLineType.BOTH);
      line.afterNumber = 24;
      el.setAttribute('data-side', Side.RIGHT);

      element.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('updateRanges remove all', () => {
      assertHasRange(rangeA, true);
      assertHasRange(rangeB, true);
      assertHasRange(rangeC, true);
      assertHasRange(rangeD, true);
      assertHasRange(rangeE, true);

      element.updateRanges([]);

      assertHasRange(rangeA, false);
      assertHasRange(rangeB, false);
      assertHasRange(rangeC, false);
      assertHasRange(rangeD, false);
      assertHasRange(rangeE, false);
    });

    test('updateRanges remove A and C', () => {
      assertHasRange(rangeA, true);
      assertHasRange(rangeB, true);
      assertHasRange(rangeC, true);
      assertHasRange(rangeD, true);
      assertHasRange(rangeE, true);

      element.updateRanges([rangeB, rangeD, rangeE]);

      assertHasRange(rangeA, false);
      assertHasRange(rangeB, true);
      assertHasRange(rangeC, false);
      assertHasRange(rangeD, true);
      assertHasRange(rangeE, true);
    });

    test('updateRanges add B and D', () => {
      element.updateRanges([]);

      assertHasRange(rangeA, false);
      assertHasRange(rangeB, false);
      assertHasRange(rangeC, false);
      assertHasRange(rangeD, false);
      assertHasRange(rangeE, false);

      element.updateRanges([rangeB, rangeD]);

      assertHasRange(rangeA, false);
      assertHasRange(rangeB, true);
      assertHasRange(rangeC, false);
      assertHasRange(rangeD, true);
      assertHasRange(rangeE, false);
    });

    test('updateRanges add A, remove B', () => {
      element.updateRanges([rangeB, rangeC]);

      assertHasRange(rangeA, false);
      assertHasRange(rangeB, true);
      assertHasRange(rangeC, true);

      element.updateRanges([rangeA, rangeC]);

      assertHasRange(rangeA, true);
      assertHasRange(rangeB, false);
      assertHasRange(rangeC, true);
    });

    test('_getRangesForLine normalizes invalid ranges', () => {
      const line = new GrDiffLine(GrDiffLineType.BOTH);
      line.afterNumber = 55;
      line.text = 'getRangesForLine normalizes invalid ranges';
      const ranges = element.getRangesForLine(line, Side.RIGHT);
      assert.equal(ranges.length, 1);
      const range = ranges[0];
      assert.isTrue(range.start < range.end, 'start and end are normalized');
      assert.equal(range.end, line.text.length);
    });
  });
});
