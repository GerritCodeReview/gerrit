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
  ChangeMessageId,
  ChangeMessageInfo,
  CommentLinkInfo,
  CommentLinks,
  CommitInfo,
  ConfigInfo,
  GitPersonInfo,
  GitRef,
  InheritedBooleanInfo,
  MaxObjectSizeLimitInfo,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  Reviewers,
  RevisionInfo,
  SubmitTypeInfo,
  Timestamp,
  TimezoneOffset,
} from '../types/common';
import {
  ChangeStatus,
  InheritedBooleanInfoConfiguredValue,
  RevisionKind,
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
export const DEFAULT_TEST_NUMERIC_CHANGE_ID = 42 as NumericChangeId;

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

export function createDefaultGitPersonInfo(): GitPersonInfo {
  return {
    name: 'Test person',
    email: 'email@google.com',
    date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
    tz: 0 as TimezoneOffset,
  };
}

export function createDefaultCommitInfo(): CommitInfo {
  return {
    parents: [],
    author: createDefaultGitPersonInfo(),
    committer: createDefaultGitPersonInfo(),
    subject: 'Test commit subject',
    message: 'Test commit message',
  };
}

export function createDefaultRevisionInfo(): RevisionInfo {
  return {
    _number: 1 as PatchSetNum,
    commit: createDefaultCommitInfo(),
    created: dateToTimestamp(DEFAULT_TEST_CHANGE_CREATED),
    kind: RevisionKind.REWORK,
    ref: 'refs/changes/5/6/1' as GitRef,
    uploader: createDefaultAccountInfo(),
  };
}

export interface GenerateChangeOptions {
  revisionsCount?: number;
  messagesCount?: number;
  status: ChangeStatus;
}

export function generateChange(options: GenerateChangeOptions) {
  const change: ChangeInfo = {
    ...createDefaultChangeInfo(),
    status: options?.status ?? ChangeStatus.NEW,
  };
  const revisionIdStart = 1;
  const messageIdStart = 1000;
  // We want to distinguish between empty arrays/objects and undefined
  // If an option is not set - the appropriate property is not set
  // If an options is set - the property always set
  if (options && typeof options.revisionsCount !== 'undefined') {
    const revisions: {[revisionId: string]: RevisionInfo} = {};
    const revisionDate = DEFAULT_TEST_CHANGE_CREATED;
    for (let i = 0; i < options.revisionsCount; i++) {
      const revisionId = (i + revisionIdStart).toString(16);
      const revision: RevisionInfo = {
        ...createDefaultRevisionInfo(),
        _number: (i + 1) as PatchSetNum,
        created: dateToTimestamp(revisionDate),
        ref: `refs/changes/5/6/${i + 1}` as GitRef,
      };
      revisions[revisionId] = revision;
      // advance 1 day
      revisionDate.setDate(revisionDate.getDate() + 1);
    }
    change.revisions = revisions;
  }
  if (options && typeof options.messagesCount !== 'undefined') {
    const messages: ChangeMessageInfo[] = [];
    for (let i = 0; i < options.messagesCount; i++) {
      messages.push({
        id: (i + messageIdStart).toString(16) as ChangeMessageId,
        date: '2020-01-01 00:00:00.000000000' as Timestamp,
        message: `This is a message N${i + 1}`,
      });
    }
    change.messages = messages;
  }
  if (options && options.status) {
    change.status = options.status;
  }
  return change;
}
