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
  ChangeMessage,
  ChangeMessageId,
  ChangeMessageInfo,
  ChangeViewChangeInfo,
  CommentInfo,
  CommentLinkInfo,
  CommentLinks,
  CommentRange,
  CommentThread,
  CommitId,
  CommitInfo,
  ConfigInfo,
  DownloadInfo,
  DraftInfo,
  EDIT,
  EditInfo,
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
  GroupsConfigInfo,
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
  SavingState,
  SchemesInfoMap,
  ServerInfo,
  SubmittedTogetherInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  UrlEncodedCommentId,
  UserConfigInfo,
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
  FixReplacementInfo,
  PatchSetNumber,
  QuickLabelInfo,
  SubmitRequirementExpressionInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../api/rest-api';
import {
  CheckResult,
  CheckRun,
  ChecksModel,
  ChecksPatchset,
  RunResult,
} from '../models/checks/checks-model';
import {DiffInfo, GrDiffLineType} from '../api/diff';
import {SearchViewState} from '../models/views/search';
import {ChangeChildView, ChangeViewState} from '../models/views/change';
import {NormalizedFileInfo} from '../models/change/files-model';
import {GroupViewState} from '../models/views/group';
import {RepoDetailView, RepoViewState} from '../models/views/repo';
import {AdminChildView, AdminViewState} from '../models/views/admin';
import {DashboardType, DashboardViewState} from '../models/views/dashboard';
import {GrDiffLine} from '../embed/diff/gr-diff/gr-diff-line';
import {
  GrDiffGroup,
  GrDiffGroupType,
} from '../embed/diff/gr-diff/gr-diff-group';
import {
  Actions,
  AiCodeReviewProvider,
  ChatRequest,
  ChatResponseListener,
  ContextItemType,
  Models,
} from '../api/ai-code-review';
import {
  Action,
  ActionResult,
  Category,
  Fix,
  Link,
  LinkIcon,
  RunStatus,
  TagColor,
} from '../api/checks';

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
    current_revision_number: 1 as PatchSetNumber,
    ...partial,
  };
}

export function createChangeWithStatus(
  status: ChangeStatus,
  mergeable: boolean | undefined = undefined
): ChangeInfo {
  return {
    ...createChange(),
    revisions: createRevisions(1),
    current_revision: 'rev1' as CommitId,
    status,
    mergeable,
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
    allow_blame: true,
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
    project_state_predicate_enabled: true,
  };
}

export function createGroupsInfo(): GroupsConfigInfo {
  return {};
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
    groups: createGroupsInfo(),
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

export function createContextGroup(options: {offset?: number; count?: number}) {
  const offset = options.offset || 0;
  const numLines = options.count || 10;
  const lines = [];
  for (let i = 0; i < numLines; i++) {
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.beforeNumber = offset + i + 1;
    line.afterNumber = offset + i + 1;
    line.text = 'lorem upsum';
    lines.push(line);
  }
  return new GrDiffGroup({
    type: GrDiffGroupType.CONTEXT_CONTROL,
    contextGroups: [new GrDiffGroup({type: GrDiffGroupType.BOTH, lines})],
  });
}

export function createContextGroupWithDelta() {
  return new GrDiffGroup({
    type: GrDiffGroupType.CONTEXT_CONTROL,
    contextGroups: [
      new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [
          new GrDiffLine(GrDiffLineType.REMOVE, 8),
          new GrDiffLine(GrDiffLineType.ADD, 0, 10),
          new GrDiffLine(GrDiffLineType.REMOVE, 9),
          new GrDiffLine(GrDiffLineType.ADD, 0, 11),
          new GrDiffLine(GrDiffLineType.REMOVE, 10),
          new GrDiffLine(GrDiffLineType.ADD, 0, 12),
          new GrDiffLine(GrDiffLineType.REMOVE, 11),
          new GrDiffLine(GrDiffLineType.ADD, 0, 13),
        ],
      }),
    ],
  });
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
    allow_suggest_code_while_commenting: true,
    allow_autocompleting_comments: true,
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
    id: 'draft-12345' as UrlEncodedCommentId,
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
  return new ChangeComments(comments, drafts, {}, {});
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

export function createFixReplacementInfo(): FixReplacementInfo {
  return {
    path: 'test/path',
    range: createRange(),
    replacement: 'replacement',
  };
}

export const chatModels: Models = {
  models: [
    {
      model_id: 'gemini-pro',
      short_text: 'Gemini Pro',
      full_display_text: 'Gemini Pro',
    },
    {
      model_id: 'gemini-ultra',
      short_text: 'Gemini Ultra',
      full_display_text: 'Gemini Ultra',
    },
  ],
  default_model_id: 'gemini-pro',
  documentation_url: 'http://doc.url',
  citation_url: 'http://citation.url',
  privacy_url: 'http://privacy.url',
};

export const chatActions: Actions = {
  actions: [
    {
      id: 'freeform',
      display_text: 'Free form chat',
    },
    {
      id: 'summarize',
      display_text: 'Summarize',
      initial_user_prompt: 'Summarize the change',
      icon: 'summarize',
      enable_splash_page_card: true,
      enable_send_without_input: true,
    },
  ],
  default_action_id: 'freeform',
};

export const chatContextItemTypes: ContextItemType[] = [
  {
    id: 'google',
    name: 'google',
    icon: 'bug_report',
    placeholder: 'google.com',
    regex: /http:\/\/www.google.com/,

    parse(link: string) {
      const match = link.match(this.regex);
      if (!match) return undefined;
      return {
        type_id: this.id,
        identifier: 'google-id',
        link,
        title: 'google-title',
      };
    },
  },
];

export const chatProvider: AiCodeReviewProvider = {
  chat: (_req: ChatRequest, _listener: ChatResponseListener) => {},
  listChatConversations: (_change: ChangeInfo) => Promise.resolve([]),
  getChatConversation: (_change: ChangeInfo, _conversation_id: string) =>
    Promise.resolve([]),
  getModels: (_change: ChangeInfo) => Promise.resolve(chatModels),
  getActions: (_change: ChangeInfo) => Promise.resolve(chatActions),
  getContextItemTypes: () => Promise.resolve(chatContextItemTypes),
};

export const checkRun0: CheckRun = {
  pluginName: 'f0',
  internalRunId: 'f0',
  patchset: 1,
  checkName: 'FAKE Error Finder Finder Finder Finder Finder Finder Finder',
  labelName: 'Presubmit',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  worstCategory: Category.ERROR,
  results: [
    {
      internalResultId: 'f0r0',
      category: Category.ERROR,
      summary: 'I would like to point out this error: 1 is not equal to 2!',
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
      tags: [{name: 'OBSOLETE'}, {name: 'E2E'}],
    },
    {
      internalResultId: 'f0r1',
      category: Category.ERROR,
      summary: 'Running the mighty test has failed by crashing.',
      message: 'Btw, 1 is also not equal to 3. Did you know?',
      actions: [
        {
          name: 'Ignore',
          tooltip: 'Ignore this result',
          primary: true,
          callback: () => Promise.resolve({message: 'fake "ignore" triggered'}),
        },
        {
          name: 'Flag',
          tooltip: 'Flag this result as totally absolutely really not useful',
          primary: true,
          disabled: true,
          callback: () => Promise.resolve({message: 'flag "flag" triggered'}),
        },
        {
          name: 'Upload',
          tooltip: 'Upload the result to the super cloud.',
          primary: false,
          callback: () => Promise.resolve({message: 'fake "upload" triggered'}),
        },
        {
          name: 'useful',
          callback: () =>
            Promise.resolve({message: 'fake "useful report" triggered'}),
        },
        {
          name: 'not-useful',
          callback: () =>
            Promise.resolve({message: 'fake "not useful report" triggered'}),
        },
      ],
      tags: [{name: 'INTERRUPTED', color: TagColor.BROWN}, {name: 'WINDOWS'}],
      links: [
        {primary: false, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: false, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.REPORT_BUG},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HELP_PAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HISTORY},
      ],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const checkRun1: CheckRun = {
  pluginName: 'f1',
  internalRunId: 'f1',
  checkName: 'FAKE Super Check',
  startedTimestamp: new Date(new Date().getTime() - 5 * 60 * 1000),
  finishedTimestamp: new Date(new Date().getTime() + 5 * 60 * 1000),
  patchset: 1,
  labelName: 'Verified',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  worstCategory: Category.ERROR,
  results: [
    {
      internalResultId: 'f1r0',
      category: Category.WARNING,
      summary: 'We think that you could improve this.',
      message: `There is a lot to be said. A lot. I say, a lot.
                So please keep reading.`,
      tags: [{name: 'INTERRUPTED', color: TagColor.PURPLE}, {name: 'WINDOWS'}],
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 7,
            start_character: 5,
            end_line: 9,
            end_character: 20,
          },
        },
      ],
      links: [
        {primary: true, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {
          primary: false,
          url: 'https://google.com',
          tooltip: 'look at this',
          icon: LinkIcon.IMAGE,
        },
        {
          primary: false,
          url: 'https://google.com',
          tooltip: 'not at this',
          icon: LinkIcon.IMAGE,
        },
      ],
    },
    {
      internalResultId: 'f1r1',
      category: Category.INFO,
      summary: 'Suspicious Author',
      message: 'Do you personally know this person?',
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 2,
            start_character: 0,
            end_line: 2,
            end_character: 0,
          },
        },
      ],
      links: [],
    },
    {
      internalResultId: 'f1r2',
      category: Category.ERROR,
      summary: 'Test Size Checker',
      message: 'The test seems to be of large size, not medium.',
      codePointers: [
        {
          path: 'plugins/BUILD',
          range: {
            start_line: 186,
            start_character: 12,
            end_line: 186,
            end_character: 18,
          },
        },
      ],
      actions: [
        {
          name: 'useful',
          tooltip: 'This check result was helpful',
          callback: () =>
            new Promise(resolve => {
              setTimeout(
                () => resolve({message: 'Feedback recorded.'} as ActionResult),
                1000
              );
            }),
        },
        {
          name: 'not-useful',
          tooltip: 'This check result was not helpful',
          callback: () =>
            new Promise(resolve => {
              setTimeout(
                () => resolve({message: 'Feedback recorded.'} as ActionResult),
                1000
              );
            }),
        },
      ],
      fixes: [
        {
          description: 'This is the way to do it.',
          replacements: [
            {
              path: 'plugins/BUILD',
              range: {
                start_line: 186,
                start_character: 12,
                end_line: 186,
                end_character: 18,
              },
              replacement: 'large',
            },
          ],
        },
      ],
      links: [],
    },
  ],
  status: RunStatus.RUNNING,
};

export const checkRun2: CheckRun = {
  pluginName: 'f2',
  internalRunId: 'f2',
  patchset: 1,
  checkName: 'FAKE Mega Analysis',
  statusDescription: 'This run is nearly completed, but not quite.',
  statusLink: 'https://www.google.com/',
  checkDescription:
    'From what the title says you can tell that this check analyses.',
  checkLink: 'https://www.google.com/',
  scheduledTimestamp: new Date('2021-04-01T03:14:15'),
  startedTimestamp: new Date('2021-04-01T04:24:25'),
  finishedTimestamp: new Date('2021-04-01T04:44:44'),
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  actions: [
    {
      name: 'Re-Run',
      tooltip: 'More powerful run than before',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
    },
    {
      name: 'Monetize',
      primary: true,
      disabled: true,
      callback: () => Promise.resolve({message: 'fake "monetize" triggered'}),
    },
    {
      name: 'Delete',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "delete" triggered'}),
    },
  ],
  worstCategory: Category.INFO,
  results: [
    {
      internalResultId: 'f2r0',
      category: Category.INFO,
      summary: 'This is looking a bit too large.',
      message: `We are still looking into how large exactly. Stay tuned.
And have a look at https://www.google.com!

Or have a look at change 30000.
Example code:
  const constable = '';
  var variable = '';`,
      tags: [{name: 'FLAKY'}, {name: 'MAC-OS'}],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const checkRun3: CheckRun = {
  pluginName: 'f3',
  internalRunId: 'f3',
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
};

export const checkRun4_1: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.RUNNABLE,
  attempt: 1,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
};

export const checkRun4_2: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.COMPLETED,
  attempt: 2,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
  worstCategory: Category.INFO,
  results: [
    {
      internalResultId: 'f42r0',
      category: Category.INFO,
      summary: 'Please eliminate all the TODOs!',
    },
  ],
};

export const checkRun4_3: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.COMPLETED,
  attempt: 3,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
  worstCategory: Category.ERROR,
  results: [
    {
      internalResultId: 'f43r0',
      category: Category.ERROR,
      summary: 'Without eliminating all the TODOs your change will break!',
    },
  ],
};

export const checkRun4_4: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  patchset: 1,
  checkName: 'FAKE Elimination Long Long Long Long Long',
  checkDescription: 'Shows you the possible eliminations.',
  checkLink: 'https://www.google.com',
  status: RunStatus.COMPLETED,
  statusDescription: 'Everything was eliminated already.',
  statusLink: 'https://www.google.com',
  attempt: 40,
  scheduledTimestamp: new Date('2021-04-02T03:14:15'),
  startedTimestamp: new Date('2021-04-02T04:24:25'),
  finishedTimestamp: new Date('2021-04-02T04:25:44'),
  isSingleAttempt: false,
  isLatestAttempt: true,
  attemptDetails: [],
  worstCategory: Category.INFO,
  results: [
    {
      internalResultId: 'f44r0',
      category: Category.INFO,
      summary: 'Dont be afraid. All TODOs will be eliminated.',
      fixes: [
        {
          description: 'This is the way to do it.',
          replacements: [
            {
              path: 'BUILD',
              range: {
                start_line: 1,
                start_character: 0,
                end_line: 1,
                end_character: 0,
              },
              replacement: '# This is now fixed.\n',
            },
          ],
        },
      ],
      actions: [
        {
          name: 'Re-Run',
          tooltip: 'More powerful run than before with a long tooltip, really.',
          primary: true,
          callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
        },
      ],
    },
  ],
  actions: [
    {
      name: 'Re-Run',
      tooltip: 'small',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
    },
  ],
};

export function checkRun4CreateAttempts(from: number, to: number): CheckRun[] {
  const runs: CheckRun[] = [];
  for (let i = from; i < to; i++) {
    runs.push(checkRun4CreateAttempt(i));
  }
  return runs;
}

export function checkRun4CreateAttempt(attempt: number): CheckRun {
  return {
    pluginName: 'f4',
    internalRunId: 'f4',
    checkName: 'FAKE Elimination Long Long Long Long Long',
    status: RunStatus.COMPLETED,
    attempt,
    isSingleAttempt: false,
    isLatestAttempt: false,
    attemptDetails: [],
    worstCategory: Category.ERROR,
    results:
      attempt % 2 === 0
        ? [
            {
              internalResultId: 'f43r0',
              category: Category.ERROR,
              summary:
                'Without eliminating all the TODOs your change will break!',
            },
          ]
        : [],
  };
}

export const checkRun4Att = [
  checkRun4_1,
  checkRun4_2,
  checkRun4_3,
  ...checkRun4CreateAttempts(5, 40),
  checkRun4_4,
];

export const fakeActions: Action[] = [
  {
    name: 'Fake Action 1',
    primary: true,
    disabled: true,
    tooltip: 'Tooltip for Fake Action 1',
    callback: () => Promise.resolve({message: 'fake action 1 triggered'}),
  },
  {
    name: 'Fake Action 2',
    primary: false,
    disabled: true,
    tooltip: 'Tooltip for Fake Action 2',
    callback: () => Promise.resolve({message: 'fake action 2 triggered'}),
  },
  {
    name: 'Fake Action 3',
    summary: true,
    primary: false,
    tooltip: 'Tooltip for Fake Action 3',
    callback: () => Promise.resolve({message: 'fake action 3 triggered'}),
  },
];

export const fakeLinks: Link[] = [
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Bug Report 1',
    icon: LinkIcon.REPORT_BUG,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Bug Report 2',
    icon: LinkIcon.REPORT_BUG,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Link 1',
    icon: LinkIcon.EXTERNAL,
  },
  {
    url: 'https://www.google.com',
    primary: false,
    tooltip: 'Fake Link 2',
    icon: LinkIcon.EXTERNAL,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Code Link',
    icon: LinkIcon.CODE,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Image Link',
    icon: LinkIcon.IMAGE,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Help Link',
    icon: LinkIcon.HELP_PAGE,
  },
];

export const checkRun5: CheckRun = {
  pluginName: 'f5',
  internalRunId: 'f5',
  checkName: 'FAKE Of Tomorrow',
  status: RunStatus.SCHEDULED,
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
};

export function setAllcheckRuns(model: ChecksModel) {
  model.updateStateSetProvider('f0', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f1', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f2', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f3', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f4', ChecksPatchset.LATEST);
  model.updateStateSetProvider('f5', ChecksPatchset.LATEST);
  model.updateStateSetResults(
    'f0',
    [checkRun0],
    fakeActions,
    fakeLinks,
    'ETA: 1 min',
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f1',
    [checkRun1],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f2',
    [checkRun2],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f3',
    [checkRun3],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f4',
    checkRun4Att,
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
  model.updateStateSetResults(
    'f5',
    [checkRun5],
    [],
    [],
    undefined,
    ChecksPatchset.LATEST
  );
}
