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
  AccountsConfigInfo,
  ApprovalInfo,
  AuthInfo,
  BranchName,
  ChangeConfigInfo,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  ChangeMessageId,
  ChangeMessageInfo,
  ChangeViewChangeInfo,
  CommentLinkInfo,
  CommentLinks,
  CommitId,
  CommitInfo,
  ConfigInfo,
  DownloadInfo,
  EditPatchSetNum,
  GerritInfo,
  EmailAddress,
  GitPersonInfo,
  GitRef,
  InheritedBooleanInfo,
  MaxObjectSizeLimitInfo,
  MergeableInfo,
  NumericChangeId,
  PatchSetNum,
  PluginConfigInfo,
  PreferencesInfo,
  RepoName,
  Reviewers,
  RevisionInfo,
  SchemesInfoMap,
  ServerInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  TimezoneOffset,
  UserConfigInfo,
} from '../types/common';
import {
  AccountsVisibility,
  AppTheme,
  AuthType,
  ChangeStatus,
  DateFormat,
  DefaultBase,
  DefaultDisplayNameConfig,
  DiffViewMode,
  EmailStrategy,
  InheritedBooleanInfoConfiguredValue,
  MergeabilityComputationBehavior,
  RevisionKind,
  SubmitType,
  TimeFormat,
} from '../constants/constants';
import {formatDate} from '../utils/date-util';
import {GetDiffCommentsOutput} from '../services/services/gr-rest-api/gr-rest-api';
import {AppElementChangeViewParams} from '../elements/gr-app-types';
import {GerritView} from '../elements/core/gr-navigation/gr-navigation';
import {
  EditRevisionInfo,
  ParsedChangeInfo,
} from '../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';

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

export function createGitPerson(name = 'Test name'): GitPersonInfo {
  return {
    name,
    email: `${name}@`,
    date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
    tz: 0 as TimezoneOffset,
  };
}

export function createCommit(): CommitInfo {
  return {
    parents: [],
    author: createGitPerson(),
    committer: createGitPerson(),
    subject: 'Test commit subject',
    message: 'Test commit message',
  };
}

export function createRevision(patchSetNum = 1): RevisionInfo {
  return {
    _number: patchSetNum as PatchSetNum,
    commit: createCommit(),
    created: dateToTimestamp(TEST_CHANGE_CREATED),
    kind: RevisionKind.REWORK,
    ref: 'refs/changes/5/6/1' as GitRef,
    uploader: createAccountWithId(),
  };
}

export function createEditRevision(): EditRevisionInfo {
  return {
    _number: EditPatchSetNum,
    basePatchNum: 1 as PatchSetNum,
    commit: createCommit(),
  };
}

export function createChangeMessage(id = 'cm_id_1'): ChangeMessageInfo {
  return {
    id: id as ChangeMessageId,
    date: dateToTimestamp(TEST_CHANGE_CREATED),
    message: `This is a message with id ${id}`,
  };
}

export function createRevisions(
  count: number
): {[revisionId: string]: RevisionInfo} {
  const revisions: {[revisionId: string]: RevisionInfo} = {};
  const revisionDate = TEST_CHANGE_CREATED;
  const revisionIdStart = 1; // The same as getCurrentRevision
  for (let i = 0; i < count; i++) {
    const revisionId = (i + revisionIdStart).toString(16);
    const revision: RevisionInfo = {
      ...createRevision(i + 1),
      created: dateToTimestamp(revisionDate),
      ref: `refs/changes/5/6/${i + 1}` as GitRef,
    };
    revisions[revisionId] = revision;
    // advance 1 day
    revisionDate.setDate(revisionDate.getDate() + 1);
  }
  return revisions;
}

export function getCurrentRevision(count: number): CommitId {
  const revisionIdStart = 1; // The same as createRevisions
  return (count + revisionIdStart).toString(16) as CommitId;
}

export function createChangeMessages(count: number): ChangeMessageInfo[] {
  const messageIdStart = 1000;
  const messages: ChangeMessageInfo[] = [];
  const messageDate = TEST_CHANGE_CREATED;
  for (let i = 0; i < count; i++) {
    messages.push({
      ...createChangeMessage((i + messageIdStart).toString(16)),
      date: dateToTimestamp(messageDate),
    });
    messageDate.setDate(messageDate.getDate() + 1);
  }
  return messages;
}

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

export function createChangeViewChange(): ChangeViewChangeInfo {
  return {
    ...createChange(),
    revisions: {
      abc: createRevision(),
    },
    current_revision: 'abc' as CommitId,
  };
}

export function createParsedChange(): ParsedChangeInfo {
  return createChangeViewChange();
}

export function createAccountsConfig(): AccountsConfigInfo {
  return {
    visibility: AccountsVisibility.ALL,
    default_display_name: DefaultDisplayNameConfig.FULL_NAME,
  };
}

export function createAuth(): AuthInfo {
  return {
    auth_type: AuthType.OPENID,
    editable_account_fields: [],
  };
}

export function createChangeConfig(): ChangeConfigInfo {
  return {
    large_change: 500,
    reply_label: 'Reply',
    reply_tooltip: 'Reply and score',
    // The default update_delay is 5 minutes, but we don't want to accidentally
    // start polling in tests
    update_delay: 0,
    mergeability_computation_behavior:
      MergeabilityComputationBehavior.REF_UPDATED_AND_CHANGE_REINDEX,
    enable_attention_set: false,
    enable_assignee: false,
  };
}

export function createDownloadSchemes(): SchemesInfoMap {
  return {};
}

export function createDownloadInfo(): DownloadInfo {
  return {
    schemes: createDownloadSchemes(),
    archives: ['tgz', 'tar'],
  };
}

export function createGerritInfo(): GerritInfo {
  return {
    all_projects: 'All-Projects',
    all_users: 'All-Users',
    doc_search: false,
  };
}

export function createPluginConfig(): PluginConfigInfo {
  return {
    has_avatars: false,
    js_resource_paths: [],
    html_resource_paths: [],
  };
}

export function createSuggestInfo(): SuggestInfo {
  return {
    from: 0,
  };
}

export function createUserConfig(): UserConfigInfo {
  return {
    anonymous_coward_name: 'Name of user not set',
  };
}

export function createServerInfo(): ServerInfo {
  return {
    accounts: createAccountsConfig(),
    auth: createAuth(),
    change: createChangeConfig(),
    download: createDownloadInfo(),
    gerrit: createGerritInfo(),
    plugin: createPluginConfig(),
    suggest: createSuggestInfo(),
    user: createUserConfig(),
  };
}

export function createGetDiffCommentsOutput(): GetDiffCommentsOutput {
  return {
    baseComments: [],
    comments: [],
  };
}

export function createMergeable(): MergeableInfo {
  return {
    submit_type: SubmitType.MERGE_IF_NECESSARY,
    mergeable: false,
  };
}

export function createPreferences(): PreferencesInfo {
  return {
    changes_per_page: 10,
    theme: AppTheme.LIGHT,
    date_format: DateFormat.ISO,
    time_format: TimeFormat.HHMM_24,
    diff_view: DiffViewMode.SIDE_BY_SIDE,
    my: [],
    change_table: [],
    email_strategy: EmailStrategy.ENABLED,
    default_base_for_merges: DefaultBase.AUTO_MERGE,
  };
}

export function createApproval(): ApprovalInfo {
  return createAccountWithId();
}

export function createAppElementChangeViewParams(): AppElementChangeViewParams {
  return {
    view: GerritView.CHANGE,
    changeNum: TEST_NUMERIC_CHANGE_ID,
    project: TEST_PROJECT_NAME,
  };
}
