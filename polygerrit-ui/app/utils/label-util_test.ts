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

import '../test/common-test-setup-karma';
import {
  getApprovalInfo,
  getLabelStatus,
  getMaxAccounts,
  getRepresentativeValue,
  getVotingRange,
  getVotingRangeOrDefault,
  labelCompare,
  LabelStatus,
} from './label-util';
import {
  AccountId,
  AccountInfo,
  ApprovalInfo,
  DetailedLabelInfo,
} from '../types/common';

const VALUES_1 = {
  '-1': 'bad',
  '0': 'neutral',
  '+1': 'good',
};

const VALUES_2 = {
  '-2': 'blocking',
  '-1': 'bad',
  '0': 'neutral',
  '+1': 'good',
  '+2': 'perfect',
};

suite('label-util', () => {
  test('getVotingRange -1 to +1', () => {
    const label = {values: VALUES_1};
    const expectedRange = {min: -1, max: 1};
    assert.deepEqual(getVotingRange(label), expectedRange);
    assert.deepEqual(getVotingRangeOrDefault(label), expectedRange);
  });

  test('getVotingRange -2 to +2', () => {
    const label = {values: VALUES_2};
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

  test('getMaxAccounts', () => {
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [
        {value: 2, _account_id: 314 as AccountId},
        {value: 1, _account_id: 777 as AccountId},
      ],
    };

    const maxAccounts = getMaxAccounts(label);

    assert.equal(maxAccounts.length, 1);
    assert.equal(maxAccounts[0]._account_id, 314 as AccountId);
  });

  test('getMaxAccounts unset parameters', () => {
    assert.isEmpty(getMaxAccounts());
    assert.isEmpty(getMaxAccounts({}));
    assert.isEmpty(getMaxAccounts({values: VALUES_2}));
  });

  test('getApprovalInfo', () => {
    const myAccountInfo: AccountInfo = {_account_id: 314 as AccountId};
    const myApprovalInfo: ApprovalInfo = {
      value: 2,
      _account_id: 314 as AccountId,
    };
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [myApprovalInfo, {value: 1, _account_id: 777 as AccountId}],
    };
    assert.equal(getApprovalInfo(label, myAccountInfo), myApprovalInfo);
  });

  test('getApprovalInfo no approval for user', () => {
    const myAccountInfo: AccountInfo = {_account_id: 123 as AccountId};
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [
        {value: 2, _account_id: 314 as AccountId},
        {value: 1, _account_id: 777 as AccountId},
      ],
    };
    assert.isUndefined(getApprovalInfo(label, myAccountInfo));
  });

  test('labelCompare', () => {
    let sorted = ['c', 'b', 'a'].sort(labelCompare);
    assert.sameOrderedMembers(sorted, ['a', 'b', 'c']);
    sorted = ['b', 'a', 'Code-Review'].sort(labelCompare);
    assert.sameOrderedMembers(sorted, ['Code-Review', 'a', 'b']);
  });

  test('getLabelStatus', () => {
    let labelInfo: DetailedLabelInfo = {all: [], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {all: [{value: 0}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.NEUTRAL);
    labelInfo = {all: [{value: 1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.RECOMMENDED);
    labelInfo = {all: [{value: -1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.DISLIKED);
    labelInfo = {all: [{value: 1}], values: VALUES_1};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: -1}], values: VALUES_1};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {all: [{value: 2}, {value: 1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: -2}, {value: -1}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {all: [{value: -2}, {value: 2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
    labelInfo = {all: [{value: -1}, {value: 2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: 0}, {value: 2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.APPROVED);
    labelInfo = {all: [{value: 0}, {value: -2}], values: VALUES_2};
    assert.equal(getLabelStatus(labelInfo), LabelStatus.REJECTED);
  });

  test('getRepresentativeValue', () => {
    let labelInfo: DetailedLabelInfo = {all: []};
    assert.equal(getRepresentativeValue(labelInfo), 0);
    labelInfo = {all: [{value: 0}]};
    assert.equal(getRepresentativeValue(labelInfo), 0);
    labelInfo = {all: [{value: 1}]};
    assert.equal(getRepresentativeValue(labelInfo), 1);
    labelInfo = {all: [{value: 2}, {value: 1}]};
    assert.equal(getRepresentativeValue(labelInfo), 2);
    labelInfo = {all: [{value: -2}, {value: -1}]};
    assert.equal(getRepresentativeValue(labelInfo), -2);
    labelInfo = {all: [{value: -2}, {value: 2}]};
    assert.equal(getRepresentativeValue(labelInfo), -2);
    labelInfo = {all: [{value: -1}, {value: 2}]};
    assert.equal(getRepresentativeValue(labelInfo), 2);
    labelInfo = {all: [{value: 0}, {value: 2}]};
    assert.equal(getRepresentativeValue(labelInfo), 2);
    labelInfo = {all: [{value: 0}, {value: -2}]};
    assert.equal(getRepresentativeValue(labelInfo), -2);
  });
});
