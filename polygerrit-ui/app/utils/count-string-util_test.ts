/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import {
  computePluralString,
  computeString,
  computeShortString,
} from './count-string-util';

suite('count-string tests', () => {
  test('computeString', () => {
    const noun = 'unresolved';
    assert.equal(computeString(0, noun), '');
    assert.equal(computeString(1, noun), '1 unresolved');
    assert.equal(computeString(2, noun), '2 unresolved');
  });

  test('computeShortString', () => {
    const noun = 'c';
    assert.equal(computeShortString(0, noun), '');
    assert.equal(computeShortString(1, noun), '1c');
    assert.equal(computeShortString(2, noun), '2c');
  });

  test('computePluralString', () => {
    const noun = 'comment';
    assert.equal(computePluralString(0, noun), '');
    assert.equal(computePluralString(1, noun), '1 comment');
    assert.equal(computePluralString(2, noun), '2 comments');
  });
});
