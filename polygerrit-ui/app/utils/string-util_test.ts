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
import {pluralize, ordinal} from './string-util';

suite('formatter util tests', () => {
  test('pluralize', () => {
    const noun = 'comment';
    assert.equal(pluralize(0, noun), '');
    assert.equal(pluralize(1, noun), '1 comment');
    assert.equal(pluralize(2, noun), '2 comments');
  });

  test('ordinal', () => {
    assert.equal(ordinal(0), '0th');
    assert.equal(ordinal(1), '1st');
    assert.equal(ordinal(2), '2nd');
    assert.equal(ordinal(3), '3rd');
    assert.equal(ordinal(4), '4th');
    assert.equal(ordinal(10), '10th');
    assert.equal(ordinal(11), '11th');
    assert.equal(ordinal(12), '12th');
    assert.equal(ordinal(13), '13th');
    assert.equal(ordinal(44413), '44413th');
    assert.equal(ordinal(44451), '44451st');
  });
});
