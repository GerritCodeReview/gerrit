/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
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
    assert.isFalse(deepEqual({a: 'y'}, {b: 'y'}));
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
  });

  test('deepEqual recursive', () => {
    const a = {};
    const b = {a};
    (a as any)['b'] = b;
    const c = {};
    const d = {a: c};
    (c as any)['b'] = d;

    assert.isTrue(deepEqual(a, c));
  });

  test('deepEqual map recursive', () => {
    const a = new Map();
    const b = {a};
    a.set('b', b);

    const c = new Map();
    const d = {a: c};
    c.set('b', d);

    assert.isTrue(deepEqual(a, c));
  });

  test('deepEqual direct map recursive', () => {
    const a = new Map();
    const b = new Map();
    b.set('a', a);
    a.set('b', b);

    const c = new Map();
    const d = new Map();
    d.set('a', c);
    c.set('b', d);

    assert.isTrue(deepEqual(a, c));
  });

  test('deepEqual recursively deeper', () => {
    let a: {link?: any} = {};
    let b: {link?: any} = {};
    let c: {link?: any} = {};
    a.link = b;
    b.link = c;
    c.link = a;
    deepEqual(a, c);
  });
});
