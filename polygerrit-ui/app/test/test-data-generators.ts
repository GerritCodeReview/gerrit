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
  EmailAddress,
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

export function createCommentLink(match = 'test'): CommentLinkInfo {
  return {
    match,
  };
}

export function createInheritedBoolean(value = false): InheritedBooleanInfo {
  return {
    value,
    configured_value: value
      ? InheritedBooleanInfoConfiguredValue.TRUE
      : InheritedBooleanInfoConfiguredValue.FALSE,
  };
}

export function createMaxObjectSizeLimit(): MaxObjectSizeLimitInfo {
  return {};
}

export function createSubmitType(
  value: Exclude<SubmitType, SubmitType.INHERIT> = SubmitType.MERGE_IF_NECESSARY
): SubmitTypeInfo {
  return {
    value,
    configured_value: SubmitType.INHERIT,
    inherited_value: value,
  };
}

export function createCommentLinks(): CommentLinks {
  return {};
}

export function createConfig(): ConfigInfo {
  return {
    private_by_default: createInheritedBoolean(),
    work_in_progress_by_default: createInheritedBoolean(),
    max_object_size_limit: createMaxObjectSizeLimit(),
    default_submit_type: createSubmitType(),
    submit_type: SubmitType.INHERIT,
    commentlinks: createCommentLinks(),
  };
}

export function createAccountWithId(id = 5): AccountInfo {
  return {
    _account_id: id as AccountId,
  };
}

export function createAccountWithEmail(email = 'test@'): AccountInfo {
  return {
    email: email as EmailAddress,
  };
}

export function createAccountWithIdNameAndEmail(id = 5): AccountInfo {
  return {
    _account_id: id as AccountId,
    email: `user-${id}@` as EmailAddress,
    name: `User-${id}`,
  };
}

export function createReviewers(): Reviewers {
  return {};
}

export const TEST_PROJECT_NAME: RepoName = 'test-project' as RepoName;
export const TEST_BRANCH_ID: BranchName = 'test-branch' as BranchName;
export const TEST_CHANGE_ID: ChangeId = 'TestChangeId' as ChangeId;
export const TEST_CHANGE_INFO_ID: ChangeInfoId = `${TEST_PROJECT_NAME}~${TEST_BRANCH_ID}~${TEST_CHANGE_ID}` as ChangeInfoId;
export const TEST_SUBJECT = 'Test subject';
export const TEST_NUMERIC_CHANGE_ID = 42 as NumericChangeId;

export const TEST_CHANGE_CREATED = new Date(2020, 1, 1, 1, 2, 3);
export const TEST_CHANGE_UPDATED = new Date(2020, 10, 6, 5, 12, 34);

export function createChange(): ChangeInfo {
  return {
    id: TEST_CHANGE_INFO_ID,
    project: TEST_PROJECT_NAME,
    branch: TEST_BRANCH_ID,
    change_id: TEST_CHANGE_ID,
    subject: TEST_SUBJECT,
    status: ChangeStatus.NEW,
    created: dateToTimestamp(TEST_CHANGE_CREATED),
    updated: dateToTimestamp(TEST_CHANGE_UPDATED),
    submitter: createAccountWithId(),
    insertions: 0,
    deletions: 0,
    _number: TEST_NUMERIC_CHANGE_ID,
    owner: createAccountWithId(),
    // This is documented as optional, but actually always set.
    reviewers: createReviewers(),
  };
}
