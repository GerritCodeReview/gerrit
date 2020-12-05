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

import '../../../test/common-test-setup-karma.js';
import {GrCountStringFormatter} from './gr-count-string-formatter.js';

suite('gr-count-string-formatter tests', () => {
  test('computeString', () => {
    const noun = 'unresolved';
    assert.equal(GrCountStringFormatter.computeString(0, noun), '');
    assert.equal(GrCountStringFormatter.computeString(1, noun),
        '1 unresolved');
    assert.equal(GrCountStringFormatter.computeString(2, noun),
        '2 unresolved');
  });

  test('computeShortString', () => {
    const noun = 'c';
    assert.equal(GrCountStringFormatter.computeShortString(0, noun), '');
    assert.equal(GrCountStringFormatter.computeShortString(1, noun), '1c');
    assert.equal(GrCountStringFormatter.computeShortString(2, noun), '2c');
  });

  test('computePluralString', () => {
    const noun = 'comment';
    assert.equal(GrCountStringFormatter.computePluralString(0, noun), '');
    assert.equal(GrCountStringFormatter.computePluralString(1, noun),
        '1 comment');
    assert.equal(GrCountStringFormatter.computePluralString(2, noun),
        '2 comments');
  });
});

