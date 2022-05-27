/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import {deepEqual} from './deep-util';

suite('compare-util tests', () => {
  test('deepEqual primitives', () => {
    assert.isTrue(deepEqual(undefined, undefined));
    assert.isTrue(deepEqual(null, null));
    assert.isTrue(deepEqual(0, 0));
    assert.isTrue(deepEqual('', ''));

    assert.isFalse(deepEqual(1, 2));
    assert.isFalse(deepEqual('a', 'b'));
  });

  test('deepEqual Dates', () => {
    const a = new Date();
    const b = new Date(a.getTime());
    assert.isTrue(deepEqual(a, b));
    assert.isFalse(deepEqual(a, undefined));
    assert.isFalse(deepEqual(undefined, b));
    assert.isFalse(deepEqual(a, new Date(a.getTime() + 1)));
  });

  test('deepEqual objects', () => {
    assert.isTrue(deepEqual({}, {}));
    assert.isTrue(deepEqual({x: 'y'}, {x: 'y'}));
    assert.isTrue(deepEqual({x: 'y', p: 'q'}, {p: 'q', x: 'y'}));
    assert.isTrue(deepEqual({x: {y: 'y'}}, {x: {y: 'y'}}));

    assert.isFalse(deepEqual(undefined, {}));
    assert.isFalse(deepEqual(null, {}));
    assert.isFalse(deepEqual({}, undefined));
    assert.isFalse(deepEqual({}, null));
    assert.isFalse(deepEqual({}, {x: 'y'}));
    assert.isFalse(deepEqual({x: 'y'}, {x: 'z'}));
    assert.isFalse(deepEqual({x: 'y'}, {z: 'y'}));
    assert.isFalse(deepEqual({x: {y: 'y'}}, {x: {y: 'z'}}));
  });

  test('deepEqual arrays', () => {
    assert.isTrue(deepEqual([], []));
    assert.isTrue(deepEqual([1], [1]));
    assert.isTrue(deepEqual(['a', 'b'], ['a', 'b']));
    assert.isTrue(deepEqual(['a', ['b']], ['a', ['b']]));

    assert.isFalse(deepEqual(undefined, []));
    assert.isFalse(deepEqual(null, []));
    assert.isFalse(deepEqual([], undefined));
    assert.isFalse(deepEqual([], null));
    assert.isFalse(deepEqual([], [1]));
    assert.isFalse(deepEqual([1], [2]));
    assert.isFalse(deepEqual([1, 2], [1]));
    assert.isFalse(deepEqual(['a', ['b']], ['a', ['c']]));
  });
});
