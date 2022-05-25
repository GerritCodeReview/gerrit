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

  test('deepEqual sets', () => {
    assert.isTrue(deepEqual(new Set([]), new Set([])));
    assert.isTrue(deepEqual(new Set([1]), new Set([1])));
    assert.isTrue(deepEqual(new Set(['a', 'b']), new Set(['a', 'b'])));

    assert.isFalse(deepEqual(undefined, new Set([])));
    assert.isFalse(deepEqual(null, new Set([])));
    assert.isFalse(deepEqual(new Set([]), undefined));
    assert.isFalse(deepEqual(new Set([]), null));
    assert.isFalse(deepEqual(new Set([]), new Set([1])));
    assert.isFalse(deepEqual(new Set([1]), new Set([2])));
    assert.isFalse(deepEqual(new Set([1, 2]), new Set([1])));
  });

  test('deepEqual maps', () => {
    assert.isTrue(deepEqual(new Map([]), new Map([])));
    assert.isTrue(deepEqual(new Map([[1, 'b']]), new Map([[1, 'b']])));
    assert.isTrue(deepEqual(new Map([['a', 'b']]), new Map([['a', 'b']])));

    assert.isFalse(deepEqual(undefined, new Map([])));
    assert.isFalse(deepEqual(null, new Map([])));
    assert.isFalse(deepEqual(new Map([]), undefined));
    assert.isFalse(deepEqual(new Map([]), null));
    assert.isFalse(deepEqual(new Map([]), new Map([[1, 'b']])));
    assert.isFalse(deepEqual(new Map([[1, 'a']]), new Map([[1, 'b']])));
    assert.isFalse(
      deepEqual(
        new Map([[1, 'a']]),
        new Map([
          [1, 'a'],
          [2, 'b'],
        ])
      )
    );
  });

  test('deepEqual nested', () => {
    assert.isFalse(deepEqual({foo: new Set([])}, {foo: new Map([])}));
  })
});
