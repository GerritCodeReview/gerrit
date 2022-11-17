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
  AccountDetailInfo,
  AccountId,
  AccountInfo,
  AccountsConfigInfo,
  ApprovalInfo,
  AuthInfo,
  BasePatchSetNum,
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
  EmailAddress,
  FixId,
  FixSuggestionInfo,
  GerritInfo,
  GitPersonInfo,
  GitRef,
  GroupAuditEventInfo,
  GroupAuditEventType,
  GroupId,
  GroupInfo,
  InheritedBooleanInfo,
  LabelInfo,
  MaxObjectSizeLimitInfo,
  MergeableInfo,
  NumericChangeId,
  PatchSetNum,
  PluginConfigInfo,
  PreferencesInfo,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  RepoName,
  Requirement,
  RequirementType,
  Reviewers,
  RevisionInfo,
  SchemesInfoMap,
  ServerInfo,
  SubmittedTogetherInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  TimezoneOffset,
  UrlEncodedCommentId,
  UserConfigInfo,
} from '../types/common';
import {
  AccountsVisibility,
  AppTheme,
  AuthType,
  ChangeStatus,
  CommentSide,
  DateFormat,
  DefaultBase,
  DefaultDisplayNameConfig,
  DiffViewMode,
  EmailStrategy,
  InheritedBooleanInfoConfiguredValue,
  MergeabilityComputationBehavior,
  RequirementStatus,
  RevisionKind,
  SubmitType,
  TimeFormat,
} from '../constants/constants';
import {formatDate} from '../utils/date-util';
import {GetDiffCommentsOutput} from '../services/gr-rest-api/gr-rest-api';
import {AppElementChangeViewParams} from '../elements/gr-app-types';
import {CommitInfoWithRequiredCommit} from '../elements/change/gr-change-metadata/gr-change-metadata';
import {WebLinkInfo} from '../types/diff';
import {createCommentThreads, UIComment, UIDraft} from '../utils/comment-util';
import {GerritView} from '../services/router/router-model';
import {ChangeComments} from '../elements/diff/gr-comment-api/gr-comment-api';
import {EditRevisionInfo, ParsedChangeInfo} from '../types/types';
import {ChangeMessage} from '../elements/change/gr-message/gr-message';
import {GenerateUrlEditViewParameters} from '../elements/core/gr-navigation/gr-navigation';
import {
  DetailedLabelInfo,
  SubmitRequirementExpressionInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../api/rest-api';
import {RunResult} from '../services/checks/checks-model';
import {Category, RunStatus} from '../api/checks';

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

export function createAccountDetailWithId(id = 5): AccountDetailInfo {
  return {
    _account_id: id as AccountId,
    registered_on: dateToTimestamp(new Date(2020, 10, 15, 14, 5, 8)),
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

export function createAccountWithIdNameEmailAndDisplayname(id = 5): AccountInfo {
  return {
    _account_id: id as AccountId,
    email: `user-${id}@` as EmailAddress,
    name: `User-${id}`,
    display_name: `User ${id} Displayed Name`,
  };
}

export function createReviewers(): Reviewers {
  return {};
}

export const TEST_PROJECT_NAME: RepoName = 'test-project' as RepoName;
export const TEST_BRANCH_ID: BranchName = 'test-branch' as BranchName;
export const TEST_CHANGE_ID: ChangeId = 'TestChangeId' as ChangeId;
export const TEST_CHANGE_INFO_ID: ChangeInfoId =
  `${TEST_PROJECT_NAME}~${TEST_BRANCH_ID}~${TEST_CHANGE_ID}` as ChangeInfoId;
export const TEST_SUBJECT = 'Test subject';
export const TEST_NUMERIC_CHANGE_ID = 42 as NumericChangeId;

export const TEST_CHANGE_CREATED = new Date(2020, 1, 1, 1, 2, 3);
export const TEST_CHANGE_UPDATED = new Date(2020, 10, 6, 5, 12, 34);

export function createGitPerson(name = 'Test name'): GitPersonInfo {
  return {
    name,
    email: `${name}@` as EmailAddress,
    date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
    tz: 0 as TimezoneOffset,
  };
}

export function createLabelInfo(score = 1): LabelInfo {
  return {
    all: [
      {
        value: score,
        permitted_voting_range: {
          min: -1,
          max: 1,
        },
        _account_id: 1000 as AccountId,
        name: 'Foo',
        email: 'foo@example.com' as EmailAddress,
        username: 'foo',
      },
    ],
    values: {
      '-1': 'Fail',
      ' 0': 'No score',
      '+1': 'Pass',
    },
    default_value: 0,
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

export function createCommitInfoWithRequiredCommit(
  commit = 'commit'
): CommitInfoWithRequiredCommit {
  return {
    ...createCommit(),
    commit: commit as CommitId,
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
    basePatchNum: 1 as BasePatchSetNum,
    commit: createCommit(),
  };
}

export function createChangeMessageInfo(id = 'cm_id_1'): ChangeMessageInfo {
  return {
    id: id as ChangeMessageId,
    date: dateToTimestamp(TEST_CHANGE_CREATED),
    message: `This is a message with id ${id}`,
  };
}

export function createChangeMessage(id = 'cm_id_1'): ChangeMessage {
  return {
    ...createChangeMessageInfo(id),
    type: '',
    expanded: false,
    commentThreads: [],
  };
}

export function createRevisions(count: number): {
  [revisionId: string]: RevisionInfo;
} {
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
      ...createChangeMessageInfo((i + messageIdStart).toString(16)),
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
    // The default update_delay is 5 minutes, but we don't want to accidentally
    // start polling in tests
    update_delay: 0,
    mergeability_computation_behavior:
      MergeabilityComputationBehavior.REF_UPDATED_AND_CHANGE_REINDEX,
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

// TODO: Maybe reconcile with createDefaultPreferences() in constants.ts.
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

export function createGenerateUrlEditViewParameters(): GenerateUrlEditViewParameters {
  return {
    view: GerritView.EDIT,
    changeNum: TEST_NUMERIC_CHANGE_ID,
    patchNum: EditPatchSetNum as PatchSetNum,
    path: 'foo/bar.baz',
    project: TEST_PROJECT_NAME,
  };
}

export function createRequirement(): Requirement {
  return {
    status: RequirementStatus.OK,
    fallbackText: '',
    type: 'wip' as RequirementType,
  };
}

export function createWebLinkInfo(): WebLinkInfo {
  return {
    name: 'gitiles',
    url: '#',
    image_url: 'gitiles.jpg',
  };
}

export function createComment(): UIComment {
  return {
    patch_set: 1 as PatchSetNum,
    id: '12345' as UrlEncodedCommentId,
    side: CommentSide.REVISION,
    line: 1,
    message: 'hello world',
    updated: '2018-02-13 22:48:48.018000000' as Timestamp,
    unresolved: false,
    path: 'abc.txt',
  };
}

export function createDraft(): UIDraft {
  return {
    ...createComment(),
    collapsed: false,
    __draft: true,
    __editing: false,
  };
}

export function createChangeComments(): ChangeComments {
  const comments = {
    '/COMMIT_MSG': [
      {
        ...createComment(),
        message: 'Done',
        updated: '2017-02-08 16:40:49' as Timestamp,
        id: '1' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        message: 'oh hay',
        updated: '2017-02-09 16:40:49' as Timestamp,
        id: '2' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        patch_set: 2 as PatchSetNum,
        message: 'hello',
        updated: '2017-02-10 16:40:49' as Timestamp,
        id: '3' as UrlEncodedCommentId,
      },
    ],
    'myfile.txt': [
      {
        ...createComment(),
        message: 'good news!',
        updated: '2017-02-08 16:40:49' as Timestamp,
        id: '4' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        patch_set: 2 as PatchSetNum,
        message: 'wat!?',
        updated: '2017-02-09 16:40:49' as Timestamp,
        id: '5' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        patch_set: 2 as PatchSetNum,
        message: 'hi',
        updated: '2017-02-10 16:40:49' as Timestamp,
        id: '6' as UrlEncodedCommentId,
      },
    ],
    'unresolved.file': [
      {
        ...createComment(),
        patch_set: 2 as PatchSetNum,
        message: 'wat!?',
        updated: '2017-02-09 16:40:49' as Timestamp,
        id: '7' as UrlEncodedCommentId,
        unresolved: true,
      },
      {
        ...createComment(),
        patch_set: 2 as PatchSetNum,
        message: 'hi',
        updated: '2017-02-10 16:40:49' as Timestamp,
        id: '8' as UrlEncodedCommentId,
        in_reply_to: '7' as UrlEncodedCommentId,
        unresolved: false,
      },
      {
        ...createComment(),
        patch_set: 2 as PatchSetNum,
        message: 'good news!',
        updated: '2017-02-08 16:40:49' as Timestamp,
        id: '9' as UrlEncodedCommentId,
        unresolved: true,
      },
    ],
  };
  const drafts = {
    '/COMMIT_MSG': [
      {
        ...createDraft(),
        message: 'hi',
        updated: '2017-02-15 16:40:49' as Timestamp,
        id: '10' as UrlEncodedCommentId,
        unresolved: true,
      },
      {
        ...createDraft(),
        message: 'fyi',
        updated: '2017-02-15 16:40:49' as Timestamp,
        id: '11' as UrlEncodedCommentId,
        unresolved: false,
      },
    ],
    'unresolved.file': [
      {
        ...createDraft(),
        message: 'hi',
        updated: '2017-02-11 16:40:49' as Timestamp,
        id: '12' as UrlEncodedCommentId,
        unresolved: false,
      },
    ],
  };
  return new ChangeComments(comments, {}, drafts, {}, {});
}

export function createCommentThread(comments: UIComment[]) {
  if (!comments.length) {
    throw new Error('comment is required to create a thread');
  }
  comments = comments.map(comment => {
    return {...createComment(), ...comment};
  });
  const threads = createCommentThreads(comments);
  return threads[0];
}

export function createRelatedChangeAndCommitInfo(): RelatedChangeAndCommitInfo {
  return {
    project: TEST_PROJECT_NAME,
    commit: createCommitInfoWithRequiredCommit(),
  };
}

export function createRelatedChangesInfo(): RelatedChangesInfo {
  return {
    changes: [],
  };
}

export function createSubmittedTogetherInfo(): SubmittedTogetherInfo {
  return {
    changes: [],
    non_visible_changes: 0,
  };
}

export function createFixSuggestionInfo(fixId = 'fix_1'): FixSuggestionInfo {
  return {
    fix_id: fixId as FixId,
    description: `Fix ${fixId}`,
    replacements: [],
  };
}

export function createGroupInfo(id = 'id'): GroupInfo {
  return {
    id: id as GroupId,
  };
}

export function createGroupAuditEventInfo(
  type: GroupAuditEventType
): GroupAuditEventInfo {
  if (
    type === GroupAuditEventType.ADD_USER ||
    type === GroupAuditEventType.REMOVE_USER
  ) {
    return {
      type,
      member: createAccountWithId(10),
      user: createAccountWithId(),
      date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
    };
  } else {
    return {
      type,
      member: createGroupInfo(),
      user: createAccountWithId(),
      date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
    };
  }
}

export function createSubmitRequirementExpressionInfo(): SubmitRequirementExpressionInfo {
  return {
    expression: 'label:Verified=MAX -label:Verified=MIN',
    fulfilled: true,
    passing_atoms: ['label2:verified=MAX'],
    failing_atoms: ['label2:verified=MIN'],
  };
}

export function createSubmitRequirementResultInfo(): SubmitRequirementResultInfo {
  return {
    name: 'Verified',
    status: SubmitRequirementStatus.SATISFIED,
    submittability_expression_result: createSubmitRequirementExpressionInfo(),
  };
}

export function createRunResult(): RunResult {
  return {
    attemptDetails: [],
    category: Category.INFO,
    checkName: 'test-name',
    internalResultId: 'test-internal-result-id',
    internalRunId: 'test-internal-run-id',
    isLatestAttempt: true,
    isSingleAttempt: true,
    pluginName: 'test-plugin-name',
    status: RunStatus.COMPLETED,
    summary: 'This is the test summary.',
    message: 'This is the test message.',
  };
}

export function createDetailedLabelInfo(): DetailedLabelInfo {
  return {
    values: {
      ' 0': 'No score',
      '+1': 'Style Verified',
      '-1': 'Wrong Style or Formatting',
    },
  };
}
