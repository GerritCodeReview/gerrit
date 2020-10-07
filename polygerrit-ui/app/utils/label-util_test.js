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
import {
  getVotingRange,
  getVotingRangeOrDefault,
} from './label-util.js';

suite('label-util', () => {
  test('getVotingRange -1 to +1', () => {
    const label = {
      values: {
        '-1': 'bad',
        '0': 'neutral',
        '+1': 'good',
      },
    };
    const expectedRange = {min: -1, max: 1};
    assert.deepEqual(getVotingRange(label), expectedRange);
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange -2 to +2', () => {
    const label = {
      values: {
        '-1': 'bad',
        '+2': 'perfect',
        '0': 'neutral',
        '-2': 'blocking',
        '+1': 'good',
      },
    };
    const expectedRange = {min: -2, max: 2};
    assert.deepEqual(getVotingRange(label), expectedRange);
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange empty values', () => {
    const label = {
      values: {},
    };
    const expectedRange = {min: 0, max: 0};
    assert.isUndefined(getVotingRange(label));
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange no values', () => {
    const label = {};
    const expectedRange = {min: 0, max: 0};
    assert.isUndefined(getVotingRange(label));
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });
});
