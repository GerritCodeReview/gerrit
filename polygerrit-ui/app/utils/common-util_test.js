/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import '../test/common-test-setup-karma.js';
import {hasOwnProperty, areSetsEqual, containsAll} from './common-util.js';

suite('common-util tests', () => {
  suite('hasOwnProperty', () => {
    test('object with the default prototype', () => {
      const obj = {
        'abc': 3,
        'name with spaces': 5,
      };
      assert.isTrue(hasOwnProperty(obj, 'abc'));
      assert.isTrue(hasOwnProperty(obj, 'name with spaces'));
      assert.isFalse(hasOwnProperty(obj, 'def'));
    });
    test('object prototype has overriden hasOwnProperty', () => {
      const F = function() {
        this.abc = 23;
      };
      F.prototype.hasOwnProperty = function(key) {
        return true;
      };
      const obj = new F();
      assert.isTrue(hasOwnProperty(obj, 'abc'));
      assert.isFalse(hasOwnProperty(obj, 'def'));
    });
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
});
