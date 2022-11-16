/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {
  hasOwnProperty,
  areSetsEqual,
  containsAll,
  intersection,
  difference,
  toggle,
  createArrayFromTo,
} from './common-util';

suite('common-util tests', () => {
  suite('hasOwnProperty', () => {
    test('object with the default prototype', () => {
      const obj = {
        abc: 3,
        'name with spaces': 5,
      };
      assert.isTrue(hasOwnProperty(obj, 'abc'));
      assert.isTrue(hasOwnProperty(obj, 'name with spaces'));
      assert.isFalse(hasOwnProperty(obj, 'def'));
    });
    test('object prototype has overridden hasOwnProperty', () => {
      class MyObject {
        abc = 123;

        hasOwnProperty(_key: PropertyKey) {
          return true;
        }
      }

      const obj = new MyObject();
      assert.isTrue(hasOwnProperty(obj, 'abc'));
      assert.isFalse(hasOwnProperty(obj, 'def'));
    });
  });

  test('createArrayFromTo', () => {
    assert.deepEqual(createArrayFromTo(0, 0), [0]);
    assert.deepEqual(createArrayFromTo(0, 1), [0, 1]);
    assert.deepEqual(createArrayFromTo(0, 2), [0, 1, 2]);
    assert.deepEqual(createArrayFromTo(10, 11), [10, 11]);
    assert.deepEqual(createArrayFromTo(1, 5), [1, 2, 3, 4, 5]);
  });

  test('areSetsEqual', () => {
    assert.isTrue(areSetsEqual(new Set(), new Set()));
    assert.isTrue(areSetsEqual(new Set([1]), new Set([1])));
    assert.isTrue(areSetsEqual(new Set([1, 1, 1, 1]), new Set([1])));
    assert.isTrue(areSetsEqual(new Set([1, 1, 2, 2]), new Set([2, 1, 2, 1])));
    assert.isTrue(areSetsEqual(new Set([1, 2, 3, 4]), new Set([4, 3, 2, 1])));
    assert.isFalse(areSetsEqual(new Set(), new Set([1])));
    assert.isFalse(areSetsEqual(new Set([1]), new Set([2])));
    assert.isFalse(areSetsEqual(new Set([1, 2, 4]), new Set([1, 2, 3])));
  });

  test('containsAll', () => {
    assert.isTrue(containsAll(new Set(), new Set()));
    assert.isTrue(containsAll(new Set([1]), new Set()));
    assert.isTrue(containsAll(new Set([1]), new Set([1])));
    assert.isTrue(containsAll(new Set([1, 2]), new Set([1])));
    assert.isTrue(containsAll(new Set([1, 2]), new Set([2])));
    assert.isTrue(containsAll(new Set([1, 2, 3, 4]), new Set([1, 4])));
    assert.isTrue(containsAll(new Set([1, 2, 3, 4]), new Set([1, 2, 3, 4])));
    assert.isFalse(containsAll(new Set(), new Set([2])));
    assert.isFalse(containsAll(new Set([1]), new Set([2])));
    assert.isFalse(containsAll(new Set([1, 2, 3, 4]), new Set([5])));
    assert.isFalse(containsAll(new Set([1, 2, 3, 4]), new Set([1, 2, 3, 5])));
  });

  test('intersections', () => {
    const arrayWithValues = [1, 2, 3];
    assert.sameDeepMembers(intersection([]), []);
    assert.sameDeepMembers(intersection([arrayWithValues]), arrayWithValues);
    // a new array is returned even if a single array is provided.
    assert.notStrictEqual(intersection([arrayWithValues]), arrayWithValues);
    assert.sameDeepMembers(
      intersection([
        [1, 2, 3],
        [2, 3, 4],
        [5, 3, 2],
      ]),
      [2, 3]
    );

    const foo1 = {value: 5};
    const foo2 = {value: 5};

    // these foo's will fail strict equality with each other, but a comparator
    // can make them intersect.
    assert.sameDeepMembers(intersection([[foo1], [foo2]]), []);
    assert.sameDeepMembers(
      intersection([[foo1], [foo2]], (a, b) => a.value === b.value),
      [foo1]
    );
  });

  test('difference', () => {
    assert.deepEqual(difference([1, 2, 3], []), [1, 2, 3]);
    assert.deepEqual(difference([1, 2, 3], [2, 3, 4]), [1]);
    assert.deepEqual(difference([1, 2, 3], [1, 2, 3]), []);
    assert.deepEqual(difference([1, 2, 3], [4, 5, 6]), [1, 2, 3]);
  });

  test('toggle', () => {
    assert.deepEqual(toggle([], 1), [1]);
    assert.deepEqual(toggle([1], 1), []);
    assert.deepEqual(toggle([1, 2, 3], 1), [2, 3]);
    assert.deepEqual(toggle([2, 3], 1), [2, 3, 1]);
  });
});
