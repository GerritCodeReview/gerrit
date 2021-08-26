/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import {deepEqual} from './compare-util';

suite('compare-utils tests', () => {
  test('deepEqual', () => {
    assert.isTrue(deepEqual({}, {}));
    assert.isTrue(deepEqual({x: 'y'}, {x: 'y'}));
    assert.isTrue(deepEqual({x: 'y', p: 'q'}, {p: 'q', x: 'y'}));

    assert.isFalse(deepEqual({}, {x: 'y'}));
    assert.isFalse(deepEqual({x: 'y'}, {x: 'z'}));
    assert.isFalse(deepEqual({x: 'y'}, {z: 'y'}));
  });
});
