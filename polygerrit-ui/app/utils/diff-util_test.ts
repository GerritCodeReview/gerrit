/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {DiffInfo, Side} from '../api/diff';
import '../test/common-test-setup';
import {createDiff} from '../test/test-data-generators';
import {getContentFromDiff, isFileUnchanged} from './diff-util';

suite('diff-util tests', () => {
  test('isFileUnchanged', () => {
    let diff: DiffInfo = {
      ...createDiff(),
      content: [
        {a: ['abcd'], ab: ['ef']},
        {b: ['ancd'], a: ['xx']},
      ],
    };
    assert.equal(isFileUnchanged(diff), false);
    diff = {
      ...createDiff(),
      content: [{ab: ['abcd']}, {ab: ['ancd']}],
    };
    assert.equal(isFileUnchanged(diff), true);
    diff = {
      ...createDiff(),
      content: [
        {a: ['abcd'], ab: ['ef'], common: true},
        {b: ['ancd'], ab: ['xx']},
      ],
    };
    assert.equal(isFileUnchanged(diff), false);
    diff = {
      ...createDiff(),
      content: [
        {a: ['abcd'], ab: ['ef'], common: true},
        {b: ['ancd'], ab: ['xx'], common: true},
      ],
    };
    assert.equal(isFileUnchanged(diff), true);
  });

  suite('getContentFromDiff', () => {
    test('one changed line', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [{a: ['abcd']}, {b: ['wxyz']}],
      };
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.LEFT), 'bc');
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.RIGHT), 'xy');
    });

    test('one common line', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [{ab: ['abcd']}],
      };
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.LEFT), 'bc');
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.RIGHT), 'bc');
    });

    test('multiple lines', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [
          {a: ['l1-asdf', 'l2-asdf']},
          {b: ['r1-wxyz']},
          {ab: ['l3-r2-qwer', 'l4-r3-uiop']},
          {b: ['r4-hjkl']},
          {ab: ['l5-r5-bnm,']},
        ],
      };
      assert.equal(
        getContentFromDiff(diff, 1, 0, 5, 10, Side.LEFT),
        'l1-asdf\nl2-asdf\nl3-r2-qwer\nl4-r3-uiop\nl5-r5-bnm,'
      );
      assert.equal(
        getContentFromDiff(diff, 1, 0, 5, 10, Side.RIGHT),
        'r1-wxyz\nl3-r2-qwer\nl4-r3-uiop\nr4-hjkl\nl5-r5-bnm,'
      );
    });

    test('one skip chunk', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [{skip: 5}, {ab: ['abcd']}],
      };
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.LEFT), '');
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.RIGHT), '');
      assert.equal(getContentFromDiff(diff, 6, 1, 6, 3, Side.LEFT), 'bc');
      assert.equal(getContentFromDiff(diff, 6, 1, 6, 3, Side.RIGHT), 'bc');
    });

    test('multiple skip chunks', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [
          {skip: 5},
          {ab: ['abcd']},
          {skip: 5},
          {ab: ['qwer']},
          {skip: 5},
          {ab: ['zxcv']},
        ],
      };
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.LEFT), '');
      assert.equal(getContentFromDiff(diff, 1, 1, 1, 3, Side.RIGHT), '');
      assert.equal(getContentFromDiff(diff, 6, 1, 6, 3, Side.LEFT), 'bc');
      assert.equal(getContentFromDiff(diff, 6, 1, 6, 3, Side.RIGHT), 'bc');
      assert.equal(getContentFromDiff(diff, 12, 1, 12, 3, Side.LEFT), 'we');
      assert.equal(getContentFromDiff(diff, 12, 1, 12, 3, Side.RIGHT), 'we');
      assert.equal(getContentFromDiff(diff, 18, 1, 18, 3, Side.LEFT), 'xc');
      assert.equal(getContentFromDiff(diff, 18, 1, 18, 3, Side.RIGHT), 'xc');
    });
  });
});
