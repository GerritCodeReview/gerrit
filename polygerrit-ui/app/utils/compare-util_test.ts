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
import {deepEqualStringDict, equalArray} from './compare-util';

suite('compare-utils tests', () => {
  test('deepEqual', () => {
    assert.isTrue(deepEqualStringDict({}, {}));
    assert.isTrue(deepEqualStringDict({x: 'y'}, {x: 'y'}));
    assert.isTrue(deepEqualStringDict({x: 'y', p: 'q'}, {p: 'q', x: 'y'}));

    assert.isFalse(deepEqualStringDict({}, {x: 'y'}));
    assert.isFalse(deepEqualStringDict({x: 'y'}, {x: 'z'}));
    assert.isFalse(deepEqualStringDict({x: 'y'}, {z: 'y'}));
  });

  test('equalArray', () => {
    assert.isTrue(equalArray(undefined, undefined));
    assert.isTrue(equalArray([], []));
    assert.isTrue(equalArray([1], [1]));
    assert.isTrue(equalArray(['a', 'b'], ['a', 'b']));

    assert.isFalse(equalArray(undefined, []));
    assert.isFalse(equalArray([], undefined));
    assert.isFalse(equalArray([], [1]));
    assert.isFalse(equalArray([1], [2]));
    assert.isFalse(equalArray([1, 2], [1]));
  });
});
