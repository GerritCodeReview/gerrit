/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {DiffInfo} from '../api/diff';
import '../test/common-test-setup';
import {createDiff} from '../test/test-data-generators';
import {isFileUnchanged} from './diff-util';

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
});
