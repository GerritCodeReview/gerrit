/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountDetailInfo,
  AccountId,
  AccountInfo,
  AccountsConfigInfo,
  ApprovalInfo,
  AuthInfo,
  BasePatchSetNum,
  BlameInfo,
  BranchName,
  ChangeConfigInfo,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  ChangeMessageId,
  ChangeMessageInfo,
  ChangeViewChangeInfo,
  CommentInfo,
  CommentLinkInfo,
  CommentLinks,
  CommentRange,
  CommitId,
  CommitInfo,
  ConfigInfo,
  DownloadInfo,
  EditInfo,
  EDIT,
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
  PARENT,
  PatchRange,
  PluginConfigInfo,
  PreferencesInfo,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  RepoName,
  Requirement,
  RequirementType,
  Reviewers,
  RevisionInfo,
  RevisionPatchSetNum,
  RobotCommentInfo,
  RobotId,
  RobotRunId,
  SchemesInfoMap,
  ServerInfo,
  SubmittedTogetherInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  UrlEncodedCommentId,
  UserConfigInfo,
  CommentThread,
  DraftInfo,
  ChangeMessage,
  SavingState,
} from '../types/common';
import {
  AccountsVisibility,
  AccountTag,
  AuthType,
  ChangeStatus,
  CommentSide,
  createDefaultPreferences,
  DefaultDisplayNameConfig,
  EmailStrategy,
  InheritedBooleanInfoConfiguredValue,
  MergeabilityComputationBehavior,
  RequirementStatus,
  RevisionKind,
  SubmitType,
} from '../constants/constants';
import {formatDate} from '../utils/date-util';
import {GetDiffCommentsOutput} from '../services/gr-rest-api/gr-rest-api';
import {CommitInfoWithRequiredCommit} from '../elements/change/gr-change-metadata/gr-change-metadata';
import {WebLinkInfo} from '../types/diff';
import {createCommentThreads, createNew} from '../utils/comment-util';
import {GerritView} from '../services/router/router-model';
import {ChangeComments} from '../elements/diff/gr-comment-api/gr-comment-api';
import {EditRevisionInfo, ParsedChangeInfo} from '../types/types';
import {
  DetailedLabelInfo,
  QuickLabelInfo,
  SubmitRequirementExpressionInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../api/rest-api';
import {CheckResult, CheckRun, RunResult} from '../models/checks/checks-model';
import {Category, Fix, Link, LinkIcon, RunStatus} from '../api/checks';
import {DiffInfo} from '../api/diff';
import {SearchViewState} from '../models/views/search';
import {ChangeChildView, ChangeViewState} from '../models/views/change';
import {NormalizedFileInfo} from '../models/change/files-model';
import {GroupViewState} from '../models/views/group';
import {RepoDetailView, RepoViewState} from '../models/views/repo';
import {AdminChildView, AdminViewState} from '../models/views/admin';
import {DashboardType, DashboardViewState} from '../models/views/dashboard';

const TEST_DEFAULT_EXPRESSION = 'label:Verified=MAX -label:Verified=MIN';
export const TEST_PROJECT_NAME: RepoName = 'test-project' as RepoName;
export const TEST_BRANCH_ID: BranchName = 'test-branch' as BranchName;
export const TEST_CHANGE_ID: ChangeId = 'TestChangeId' as ChangeId;
export const TEST_CHANGE_INFO_ID: ChangeInfoId =
  `${TEST_PROJECT_NAME}~${TEST_BRANCH_ID}~${TEST_CHANGE_ID}` as ChangeInfoId;
export const TEST_SUBJECT = 'Test subject';
export const TEST_NUMERIC_CHANGE_ID = 42 as NumericChangeId;

export const TEST_CHANGE_CREATED = new Date(2020, 1, 1, 1, 2, 3);
export const TEST_CHANGE_UPDATED = new Date(2020, 10, 6, 5, 12, 34);

export function dateToTimestamp(date: Date): Timestamp {
  const nanosecondSuffix = '.000000000';
  return (formatDate(date, 'YYYY-MM-DD HH:mm:ss') +
    nanosecondSuffix) as Timestamp;
}

export function createCommentLink(
  match = 'test',
  link = 'http://test.com'
): CommentLinkInfo {
  return {
    match,
    link,
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
    enable_reviewer_by_email: createInheritedBoolean(),
    submit_type: SubmitType.INHERIT,
    commentlinks: createCommentLinks(),
  };
}

export function createAccountWithId(id = 5): AccountInfo {
  return {
    _account_id: id as AccountId,
    email: `${id}` as EmailAddress,
  };
}

export function createServiceUserWithId(id = 5): AccountInfo {
  return {
    ...createAccountWithId(id),
    tags: [AccountTag.SERVICE_USER],
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
    _account_id: 1 as AccountId,
  };
}

export function createAccountWithEmailOnly(email = 'test@'): AccountInfo {
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

export function createAccountDetailWithIdNameAndEmail(
  id = 5
): AccountDetailInfo {
  return {
    _account_id: id as AccountId,
    email: `user-${id}@` as EmailAddress,
    name: `User-${id}`,
    registered_on: dateToTimestamp(new Date(2020, 10, 15, 14, 5, 8)),
  };
}

export function createReviewers(): Reviewers {
  return {};
}

export function createGitPerson(name = 'Test name'): GitPersonInfo {
  return {
    name,
    email: `${name}@` as EmailAddress,
    date: dateToTimestamp(new Date(2019, 11, 6, 14, 5, 8)),
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

export function createPatchRange(
  basePatchNum?: number,
  patchNum?: number
): PatchRange {
  return {
    basePatchNum: (basePatchNum ?? PARENT) as BasePatchSetNum,
    patchNum: (patchNum ?? 1) as RevisionPatchSetNum,
  };
}

export function createRevision(
  patchSetNum: number | RevisionPatchSetNum = 1,
  description = ''
): RevisionInfo {
  return {
    _number: patchSetNum as RevisionPatchSetNum,
    commit: createCommit(),
    created: dateToTimestamp(TEST_CHANGE_CREATED),
    kind: RevisionKind.REWORK,
    ref: 'refs/changes/5/6/1' as GitRef,
    uploader: createAccountWithId(),
    description,
  };
}

export function createEditInfo(): EditInfo {
  return {
    commit: {...createCommit(), commit: 'commit-id-of-edit-ps' as CommitId},
    base_patch_set_number: 1 as BasePatchSetNum,
    base_revision: 'base-revision-of-edit',
    ref: 'refs/changes/5/6/1' as GitRef,
    fetch: {},
    files: {},
  };
}

export function createEditRevision(basePatchNum = 1): EditRevisionInfo {
  return {
    _number: EDIT,
    basePatchNum: basePatchNum as BasePatchSetNum,
    commit: {
      ...createCommit(),
      commit: 'test-commit-id-of-edit-rev' as CommitId,
    },
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
  let revisionDate = TEST_CHANGE_CREATED;
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
    revisionDate = new Date(revisionDate);
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
  let messageDate = TEST_CHANGE_CREATED;
  for (let i = 0; i < count; i++) {
    messages.push({
      ...createChangeMessageInfo((i + messageIdStart).toString(16)),
      date: dateToTimestamp(messageDate),
      author: createAccountDetailWithId(i),
    });
    messageDate = new Date(messageDate);
    messageDate.setDate(messageDate.getDate() + 1);
  }
  return messages;
}

export function createFileInfo(
  path = 'test-path/test-file.txt'
): NormalizedFileInfo {
  return {
    size: 314,
    size_delta: 7,
    lines_deleted: 0,
    lines_inserted: 0,
    __path: path,
  };
}

export function createChange(partial: Partial<ChangeInfo> = {}): ChangeInfo {
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
    ...partial,
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

export function createEmptyDiff(): DiffInfo {
  return {
    meta_a: {
      name: 'empty-left.txt',
      content_type: 'text/plain',
      lines: 1,
    },
    meta_b: {
      name: 'empty-right.txt',
      content_type: 'text/plain',
      lines: 1,
    },
    intraline_status: 'OK',
    change_type: 'MODIFIED',
    content: [],
  };
}

export function createDiff(): DiffInfo {
  return {
    meta_a: {
      name: 'lorem-ipsum.txt',
      content_type: 'text/plain',
      lines: 45,
    },
    meta_b: {
      name: 'lorem-ipsum.txt',
      content_type: 'text/plain',
      lines: 48,
    },
    intraline_status: 'OK',
    change_type: 'MODIFIED',
    diff_header: [
      'diff --git a/lorem-ipsum.txt b/lorem-ipsum.txt',
      'index b2adcf4..554ae49 100644',
      '--- a/lorem-ipsum.txt',
      '+++ b/lorem-ipsum.txt',
    ],
    content: [
      {
        ab: [
          'Lorem ipsum dolor sit amet, suspendisse inceptos vehicula.',
          'Mattis lectus.',
          'Sodales duis.',
          'Orci a faucibus.',
        ],
      },
      {
        b: [
          'Nullam neque, ligula ac, id blandit.',
          'Sagittis tincidunt torquent, tempor nunc amet.',
          'At rhoncus id.',
        ],
      },
      {
        ab: [
          'Sem nascetur, erat ut, non in.',
          'A donec, venenatis pellentesque dis.',
          'Mauris mauris.',
          'Quisque nisl duis, facilisis viverra.',
          'Justo purus, semper eget et.',
        ],
      },
      {
        a: [
          'Est amet, vestibulum pellentesque.',
          'Erat ligula.',
          'Justo eros.',
          'Fringilla quisque.',
        ],
      },
      {
        a: [
          'Arcu eget, rhoncus amet cursus, ipsum elementum.  ',
          'Eros suspendisse.  ',
        ],
        b: [
          'Arcu eget, rhoncus amet cursus, ipsum elementum.',
          'Eros suspendisse.',
        ],
        common: true,
      },
      {
        a: ['Rhoncus tempor, ultricies aliquam ipsum.'],
        b: ['Rhoncus tempor, ultricies praesent ipsum.'],
        edit_a: [[26, 7]],
        edit_b: [[26, 8]],
      },
      {
        ab: [
          'Sollicitudin duis.',
          'Blandit blandit, ante nisl fusce.',
          'Felis ac at, tellus consectetuer.',
          'Sociis ligula sapien, egestas leo.',
          'Cum pulvinar, sed mauris, cursus neque velit.',
          'Augue porta lobortis.',
          'Nibh lorem, amet fermentum turpis, vel pulvinar diam.',
          'Id quam ipsum, id urna et, massa suspendisse.',
          'Ac nec, nibh praesent.',
          'Rutrum vestibulum.',
          'Est tellus, bibendum habitasse.',
          'Justo facilisis, vel nulla.',
          'Donec eu, vulputate neque aliquam, nulla dui.',
          'Risus adipiscing in.',
          'Lacus arcu arcu.',
          'Urna velit.',
          'Urna a dolor.',
          'Lectus magna augue, convallis mattis tortor, sed tellus ' +
            'consequat.',
          'Etiam dui, blandit wisi.',
          'Mi nec.',
          'Vitae eget vestibulum.',
          'Ullamcorper nunc ante, nec imperdiet felis, consectetur.',
          'Ac eget.',
          'Vel fringilla, interdum pellentesque placerat, proin ante.',
        ],
      },
      {
        b: [
          'Eu congue risus.',
          'Enim ac, quis elementum.',
          'Non et elit.',
          'Etiam aliquam, diam vel nunc.',
        ],
      },
      {
        ab: [
          'Nec at.',
          'Arcu mauris, venenatis lacus fermentum, praesent duis.',
          'Pellentesque amet et, tellus duis.',
          'Ipsum arcu vitae, justo elit, sed libero tellus.',
          'Metus rutrum euismod, vivamus sodales, vel arcu nisl.',
        ],
      },
    ],
  };
}

export function createBlame(): BlameInfo {
  return {
    author: 'test-author',
    id: 'test-id',
    time: 123,
    commit_msg: 'test-commit-message',
    ranges: [],
  };
}

export function createMergeable(mergeable = false): MergeableInfo {
  return {
    submit_type: SubmitType.MERGE_IF_NECESSARY,
    mergeable,
  };
}

// TODO: Do not change the values of createDefaultPreferences() here.
export function createPreferences(): PreferencesInfo {
  return {
    ...createDefaultPreferences(),
    changes_per_page: 10,
    email_strategy: EmailStrategy.ENABLED,
    allow_browser_notifications: true,
  };
}

export function createApproval(account?: AccountInfo): ApprovalInfo {
  return account ?? createAccountWithId();
}

export function createChangeViewState(): ChangeViewState {
  return {
    view: GerritView.CHANGE,
    childView: ChangeChildView.OVERVIEW,
    changeNum: TEST_NUMERIC_CHANGE_ID,
    repo: TEST_PROJECT_NAME,
  };
}

export function createAppElementSearchViewParams(): SearchViewState {
  return {
    view: GerritView.SEARCH,
    query: TEST_NUMERIC_CHANGE_ID.toString(),
    offset: '0',
    changes: [],
    loading: false,
  };
}

export function createEditViewState(): ChangeViewState {
  return {
    view: GerritView.CHANGE,
    childView: ChangeChildView.EDIT,
    changeNum: TEST_NUMERIC_CHANGE_ID,
    patchNum: EDIT,
    repo: TEST_PROJECT_NAME,
    editView: {path: 'foo/bar.baz'},
  };
}

export function createDiffViewState(): ChangeViewState {
  return {
    view: GerritView.CHANGE,
    childView: ChangeChildView.DIFF,
    changeNum: TEST_NUMERIC_CHANGE_ID,
    repo: TEST_PROJECT_NAME,
  };
}

export function createSearchViewState(): SearchViewState {
  return {
    view: GerritView.SEARCH,
    query: '',
    offset: '0',
    loading: false,
  };
}

export function createDashboardViewState(): DashboardViewState {
  return {
    view: GerritView.DASHBOARD,
    type: DashboardType.USER,
    user: 'self',
  };
}

export function createAdminReposViewState(): AdminViewState {
  return {
    view: GerritView.ADMIN,
    adminView: AdminChildView.REPOS,
    offset: '0',
    filter: '',
    openCreateModal: false,
  };
}

export function createAdminPluginsViewState(): AdminViewState {
  return {
    view: GerritView.ADMIN,
    adminView: AdminChildView.PLUGINS,
    offset: '0',
    filter: '',
  };
}

export function createGroupViewState(): GroupViewState {
  return {
    view: GerritView.GROUP,
    groupId: 'test-group-id' as GroupId,
  };
}

export function createRepoViewState(): RepoViewState {
  return {
    view: GerritView.REPO,
  };
}

export function createRepoBranchesViewState(): RepoViewState {
  return {
    view: GerritView.REPO,
    detail: RepoDetailView.BRANCHES,
    offset: '0',
    filter: '',
  };
}

export function createRepoTagsViewState(): RepoViewState {
  return {
    view: GerritView.REPO,
    detail: RepoDetailView.TAGS,
    offset: '0',
    filter: '',
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

export function createRange(): CommentRange {
  return {
    start_line: 1,
    start_character: 0,
    end_line: 1,
    end_character: 1,
  };
}

export function createComment(
  extra: Partial<CommentInfo | DraftInfo> = {}
): CommentInfo {
  return {
    patch_set: 1 as RevisionPatchSetNum,
    id: '12345' as UrlEncodedCommentId,
    side: CommentSide.REVISION,
    line: 1,
    message: 'hello world',
    updated: '2018-02-13 22:48:48.018000000' as Timestamp,
    unresolved: false,
    path: 'abc.txt',
    ...extra,
  };
}

export function createDraft(extra: Partial<CommentInfo> = {}): DraftInfo {
  return {
    ...createComment(),
    savingState: SavingState.OK,
    ...extra,
  };
}

export function createNewDraft(extra: Partial<CommentInfo> = {}): DraftInfo {
  return {
    ...createComment(),
    ...extra,
    ...createNew(),
  };
}

export function createRobotComment(
  extra: Partial<CommentInfo> = {}
): RobotCommentInfo {
  return {
    ...createComment(),
    robot_id: 'robot-id-123' as RobotId,
    robot_run_id: 'robot-run-id-456' as RobotRunId,
    properties: {},
    fix_suggestions: [
      {
        fix_id: 'robot-run-id-456-fix' as FixId,
        description: 'Robot suggestion',
        replacements: [
          {
            path: 'abc.txt'!,
            range: {
              start_line: 0,
              start_character: 0,
              end_line: 1,
              end_character: 10,
            },
            replacement: 'replacement',
          },
        ],
      },
    ],
    ...extra,
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
        patch_set: 2 as RevisionPatchSetNum,
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
        patch_set: 2 as RevisionPatchSetNum,
        message: 'wat!?',
        updated: '2017-02-09 16:40:49' as Timestamp,
        id: '5' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        patch_set: 2 as RevisionPatchSetNum,
        message: 'hi',
        updated: '2017-02-10 16:40:49' as Timestamp,
        id: '6' as UrlEncodedCommentId,
      },
    ],
    'unresolved.file': [
      {
        ...createComment(),
        patch_set: 2 as RevisionPatchSetNum,
        message: 'wat!?',
        updated: '2017-02-09 16:40:49' as Timestamp,
        id: '7' as UrlEncodedCommentId,
        unresolved: true,
      },
      {
        ...createComment(),
        patch_set: 2 as RevisionPatchSetNum,
        message: 'hi',
        updated: '2017-02-10 16:40:49' as Timestamp,
        id: '8' as UrlEncodedCommentId,
        in_reply_to: '7' as UrlEncodedCommentId,
        unresolved: false,
      },
      {
        ...createComment(),
        patch_set: 2 as RevisionPatchSetNum,
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

export function createThread(
  ...comments: Partial<CommentInfo | DraftInfo>[]
): CommentThread {
  if (comments.length === 0) {
    comments = [createComment()];
  }
  return {
    comments: comments.map(c => createComment(c)),
    rootId: 'test-root-id-comment-thread' as UrlEncodedCommentId,
    path: 'test-path-comment-thread',
    commentSide: CommentSide.REVISION,
    patchNum: 1 as RevisionPatchSetNum,
    line: 314,
  };
}

export function createCommentThread(
  comments: Array<Partial<CommentInfo | DraftInfo>>
) {
  if (!comments.length) {
    throw new Error('comment is required to create a thread');
  }
  const filledComments = comments.map(comment => {
    return {...createComment(), ...comment};
  });
  const threads = createCommentThreads(filledComments);
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

export function createSubmitRequirementExpressionInfo(
  expression = TEST_DEFAULT_EXPRESSION
): SubmitRequirementExpressionInfo {
  return {
    expression,
    fulfilled: true,
    passing_atoms: ['label:Verified=MAX'],
    failing_atoms: ['label:Verified=MIN'],
  };
}

export function createSubmitRequirementResultInfo(
  expression = TEST_DEFAULT_EXPRESSION
): SubmitRequirementResultInfo {
  return {
    name: 'Verified',
    status: SubmitRequirementStatus.SATISFIED,
    submittability_expression_result:
      createSubmitRequirementExpressionInfo(expression),
    is_legacy: false,
  };
}

export function createNonApplicableSubmitRequirementResultInfo(): SubmitRequirementResultInfo {
  return {
    name: 'Verified',
    status: SubmitRequirementStatus.NOT_APPLICABLE,
    applicability_expression_result: createSubmitRequirementExpressionInfo(),
    submittability_expression_result: createSubmitRequirementExpressionInfo(),
    is_legacy: false,
  };
}

export function createRun(partial: Partial<CheckRun> = {}): CheckRun {
  return {
    attemptDetails: [],
    checkName: 'test-name',
    internalRunId: 'test-internal-run-id',
    isLatestAttempt: true,
    isSingleAttempt: true,
    pluginName: 'test-plugin-name',
    status: RunStatus.COMPLETED,
    ...partial,
  };
}

export function createRunResult(): RunResult {
  return {
    category: Category.INFO,
    checkName: 'test-name',
    internalResultId: 'test-internal-result-id',
    isLatestAttempt: true,
    pluginName: 'test-plugin-name',
    summary: 'This is the test summary.',
    message: 'This is the test message.',
    status: RunStatus.COMPLETED,
    attemptDetails: [{attempt: 'latest'}],
  };
}

export function createCheckResult(
  partial: Partial<CheckResult> = {}
): CheckResult {
  return {
    category: Category.ERROR,
    summary: 'error',
    internalResultId: 'test-internal-result-id',
    ...partial,
  };
}

export function createCheckFix(partial: Partial<Fix> = {}): Fix {
  return {
    description: 'this is a test fix',
    replacements: [
      {
        path: 'testpath',
        range: createRange(),
        replacement: 'testreplacement',
      },
    ],
    ...partial,
  };
}

export function createCheckLink(partial: Partial<Link> = {}): Link {
  return {
    url: 'http://test/url',
    primary: true,
    icon: LinkIcon.EXTERNAL,
    ...partial,
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

export function createQuickLabelInfo(): QuickLabelInfo {
  return {};
}
