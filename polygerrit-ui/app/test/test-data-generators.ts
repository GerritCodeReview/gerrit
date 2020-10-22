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

import {
  AccountId,
  AccountInfo,
  BranchName,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  CommentLinkInfo,
  CommentLinks,
  ConfigInfo,
  InheritedBooleanInfo,
  MaxObjectSizeLimitInfo,
  NumericChangeId,
  RepoName,
  Reviewers,
  SubmitTypeInfo,
  Timestamp,
} from '../types/common';
import {
  ChangeStatus,
  InheritedBooleanInfoConfiguredValue,
  SubmitType,
} from '../constants/constants';
import {formatDate} from '../utils/date-util';

export function dateToTimestamp(date: Date): Timestamp {
  const nanosecondSuffix = '.000000000';
  return (formatDate(date, 'YYYY-MM-DD HH:mm:ss') +
    nanosecondSuffix) as Timestamp;
}

export function createDefaultCommentLinkInfo(): CommentLinkInfo {
  return {
    match: 'test',
  };
}

export function createDefaultInheritedBooleanInfo(): InheritedBooleanInfo {
  return {
    value: false,
    configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
  };
}

export function createDefaultMaxObjectSizeLimitInfo(): MaxObjectSizeLimitInfo {
  return {};
}

export function createDefaultSubmitTypeInfo(): SubmitTypeInfo {
  return {
    value: SubmitType.MERGE_IF_NECESSARY,
    configured_value: SubmitType.INHERIT,
    inherited_value: SubmitType.MERGE_IF_NECESSARY,
  };
}

export function createDefaultCommentLinks(): CommentLinks {
  return {};
}

export function createDefaultConfigInfo(): ConfigInfo {
  return {
    private_by_default: createDefaultInheritedBooleanInfo(),
    work_in_progress_by_default: createDefaultInheritedBooleanInfo(),
    max_object_size_limit: createDefaultMaxObjectSizeLimitInfo(),
    default_submit_type: createDefaultSubmitTypeInfo(),
    submit_type: SubmitType.INHERIT,
    commentlinks: createDefaultCommentLinks(),
  };
}

export function createDefaultAccountInfo(): AccountInfo {
  return {
    _account_id: 5 as AccountId,
  };
}

export function createDefaultReviewers(): Reviewers {
  return {};
}

export const DEFAULT_TEST_PROJECT_NAME: RepoName = 'default-test-project' as RepoName;
export const DEFAULT_TEST_BRANCH_ID: BranchName = 'default-test-branch' as BranchName;
export const DEFAULT_TEST_CHANGE_ID: ChangeId = 'DefaultTestChangeId' as ChangeId;
export const DEFAULT_TEST_CHANGE_INFO_ID: ChangeInfoId = `${DEFAULT_TEST_PROJECT_NAME}~${DEFAULT_TEST_BRANCH_ID}~${DEFAULT_TEST_CHANGE_ID}` as ChangeInfoId;
export const DEFAULT_TEST_SUBJECT = 'Default test subject';
export const DEFAULT_TEST_NUMERIC_CHANGE_ID = 5 as NumericChangeId;

export const DEFAULT_TEST_CHANGE_CREATED = new Date(2020, 1, 1, 1, 2, 3);
export const DEFAULT_TEST_CHANGE_UPDATED = new Date(2020, 10, 6, 5, 12, 34);

export function createDefaultChangeInfo(): ChangeInfo {
  return {
    id: DEFAULT_TEST_CHANGE_INFO_ID,
    project: DEFAULT_TEST_PROJECT_NAME,
    branch: DEFAULT_TEST_BRANCH_ID,
    change_id: DEFAULT_TEST_CHANGE_ID,
    subject: DEFAULT_TEST_SUBJECT,
    status: ChangeStatus.NEW,
    created: dateToTimestamp(DEFAULT_TEST_CHANGE_CREATED),
    updated: dateToTimestamp(DEFAULT_TEST_CHANGE_UPDATED),
    submitter: createDefaultAccountInfo(),
    insertions: 0,
    deletions: 0,
    _number: DEFAULT_TEST_NUMERIC_CHANGE_ID,
    owner: createDefaultAccountInfo(),
    // This is documented as optional, but actually always set.
    reviewers: createDefaultReviewers(),
  };
}
