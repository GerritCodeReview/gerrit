/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HttpMethod} from '../../constants/constants';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  AccountExternalIdInfo,
  AccountId,
  AccountInfo,
  AccountStateInfo,
  ActionNameToActionInfoMap,
  Base64FileContent,
  BasePatchSetNum,
  BlameInfo,
  BranchInfo,
  BranchInput,
  BranchName,
  CapabilityInfoMap,
  ChangeId,
  ChangeInfo,
  ChangeMessageId,
  CommentInfo,
  CommentInput,
  CommitInfo,
  ConfigInfo,
  ConfigInput,
  ContributorAgreementInfo,
  ContributorAgreementInput,
  DashboardId,
  DashboardInfo,
  DiffPreferenceInput,
  DocResult,
  EditInfo,
  EditPreferencesInfo,
  EmailAddress,
  EmailInfo,
  EncodedGroupId,
  FileNameToFileInfoMap,
  FilePathToDiffInfoMap,
  FixId,
  GitRef,
  GpgKeyId,
  GpgKeyInfo,
  GpgKeysInput,
  GroupAuditEventInfo,
  GroupId,
  GroupInfo,
  GroupInput,
  GroupName,
  GroupNameToGroupInfoMap,
  GroupOptionsInput,
  Hashtag,
  HashtagsInput,
  ImagesForDiff,
  IncludedInInfo,
  MergeableInfo,
  NameToProjectInfoMap,
  NumericChangeId,
  Password,
  PatchRange,
  PatchSetNum,
  PathToRobotCommentsInfoMap,
  PluginInfo,
  PreferencesInfo,
  PreferencesInput,
  ProjectAccessInfo,
  RepoAccessInfoMap,
  ProjectAccessInput,
  ProjectInfo,
  ProjectInfoWithName,
  ProjectInput,
  ProjectWatchInfo,
  RelatedChangesInfo,
  RepoName,
  RequestPayload,
  ReviewInput,
  RevisionId,
  RobotCommentInfo,
  ServerInfo,
  SshKeyInfo,
  SubmittedTogetherInfo,
  SuggestedReviewerInfo,
  TagInfo,
  TagInput,
  TopMenuEntryInfo,
  UrlEncodedCommentId,
  UserId,
  DraftInfo,
  ReviewResult,
} from '../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  IgnoreWhitespaceType,
} from '../../types/diff';
import {Finalizable, ParsedChangeInfo} from '../../types/types';
import {ErrorCallback} from '../../api/rest';
import {FixReplacementInfo} from '../../api/rest-api';

export interface GetDiffCommentsOutput {
  baseComments: CommentInfo[];
  comments: CommentInfo[];
}

export interface GetDiffRobotCommentsOutput {
  baseComments: RobotCommentInfo[];
  comments: RobotCommentInfo[];
}

export interface RestApiService extends Finalizable {
  getConfig(
    noCache?: boolean,
    requestOrigin?: string
  ): Promise<ServerInfo | undefined>;
  getLoggedIn(): Promise<boolean>;
  getPreferences(): Promise<PreferencesInfo | undefined>;

  /**
   * Fetch the account state of the current user.
   * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#get-state
   */
  getAccountState(): Promise<AccountStateInfo | undefined>;

  getVersion(requestOrigin?: string): Promise<string | undefined>;
  getAccount(requestOrigin?: string): Promise<AccountDetailInfo | undefined>;
  getAccountCapabilities(
    params?: string[]
  ): Promise<AccountCapabilityInfo | undefined>;
  getExternalIds(): Promise<AccountExternalIdInfo[] | undefined>;
  deleteAccountIdentity(id: string[]): Promise<Response>;
  deleteAccount(): Promise<Response>;
  getRepos(
    filter: string | undefined,
    reposPerPage: number,
    offset?: number,
    errFn?: ErrorCallback,
    requestOrigin?: string
  ): Promise<ProjectInfoWithName[] | undefined>;

  send(
    method: HttpMethod,
    url: string,
    body?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string,
    requestOrigin?: string
  ): Promise<Response | void>;

  getChangeSuggestedReviewers(
    changeNum: NumericChangeId,
    input: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[] | undefined>;
  getChangeSuggestedCCs(
    changeNum: NumericChangeId,
    input: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[] | undefined>;
  /**
   * Request list of accounts via https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#query-account
   * Operators defined here https://gerrit-review.googlesource.com/Documentation/user-search-accounts.html#_search_operators
   */
  queryAccounts(
    input: string,
    n?: number,
    canSee?: NumericChangeId,
    filterActive?: boolean,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[] | undefined>;
  getAccountSuggestions(input: string): Promise<AccountInfo[] | undefined>;
  getSuggestedGroups(
    input: string,
    project?: RepoName,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<GroupNameToGroupInfoMap | undefined>;
  /**
   * Execute a change action or revision action on a change.
   */
  executeChangeAction(
    changeNum: NumericChangeId,
    method: HttpMethod | undefined,
    endpoint: string,
    patchNum?: PatchSetNum,
    payload?: RequestPayload,
    errFn?: ErrorCallback
  ): Promise<Response>;
  getRepoBranches(
    filter: string,
    repo: RepoName,
    reposBranchesPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<BranchInfo[] | undefined>;

  getChangeDetail(
    changeNum?: number | string,
    errFn?: ErrorCallback
  ): Promise<ParsedChangeInfo | undefined>;

  /**
   * Given a changeNum, gets the change.
   *
   * If the project is known for the specified changeNum uses
   * /changes/{project}~{change} api.
   * Otherwise, calls /changes/q={changeNum}. In this case the result can be
   * stale as this API uses index.
   */
  getChange(
    changeNum: ChangeId | NumericChangeId,
    errFn?: ErrorCallback,
    optionsHex?: string
  ): Promise<ChangeInfo | undefined>;

  savePreferences(
    prefs: PreferencesInput
  ): Promise<PreferencesInfo | undefined>;

  getDiffPreferences(): Promise<DiffPreferencesInfo | undefined>;

  saveDiffPreferences(prefs: DiffPreferenceInput): Promise<Response>;

  getEditPreferences(): Promise<EditPreferencesInfo | undefined>;

  saveEditPreferences(prefs: EditPreferencesInfo): Promise<Response>;

  getAccountEmails(): Promise<EmailInfo[] | undefined>;
  getAccountEmailsFor(
    email: string,
    errFn?: ErrorCallback
  ): Promise<EmailInfo[] | undefined>;
  deleteAccountEmail(email: string): Promise<Response>;
  setPreferredAccountEmail(email: string): Promise<void>;

  getAccountSSHKeys(): Promise<SshKeyInfo[] | undefined>;
  deleteAccountSSHKey(key: string): void;
  addAccountSSHKey(key: string): Promise<SshKeyInfo>;

  createRepoBranch(
    name: RepoName,
    branch: BranchName,
    revision: BranchInput
  ): Promise<Response>;

  createRepoTag(
    name: RepoName,
    tag: string,
    revision: TagInput
  ): Promise<Response>;
  addAccountGPGKey(key: GpgKeysInput): Promise<Record<string, GpgKeyInfo>>;
  deleteAccountGPGKey(id: GpgKeyId): Promise<Response>;
  getAccountGPGKeys(): Promise<Record<string, GpgKeyInfo>>;
  probePath(path: string): Promise<boolean>;

  saveFileUploadChangeEdit(
    changeNum: NumericChangeId,
    path: string,
    content: string
  ): Promise<Response | undefined>;

  deleteFileInChangeEdit(
    changeNum: NumericChangeId,
    path: string
  ): Promise<Response | undefined>;

  restoreFileInChangeEdit(
    changeNum: NumericChangeId,
    restore_path: string
  ): Promise<Response | undefined>;

  renameFileInChangeEdit(
    changeNum: NumericChangeId,
    old_path: string,
    new_path: string
  ): Promise<Response | undefined>;

  queryChangeFiles(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    query: string,
    errFn?: ErrorCallback
  ): Promise<string[] | undefined>;

  getRepoAccessRights(
    repoName: RepoName,
    errFn?: ErrorCallback
  ): Promise<ProjectAccessInfo | undefined>;

  createRepo(config: ProjectInput & {name: RepoName}): Promise<Response>;

  getRepo(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<ProjectInfo | undefined>;

  getRepoDashboards(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo[] | undefined>;

  getRepoAccess(repo: RepoName): Promise<RepoAccessInfoMap | undefined>;

  getProjectConfig(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<ConfigInfo | undefined>;

  getCapabilities(
    errFn?: ErrorCallback
  ): Promise<CapabilityInfoMap | undefined>;

  setRepoAccessRights(
    repoName: RepoName,
    repoInfo: ProjectAccessInput
  ): Promise<Response>;

  setRepoAccessRightsForReview(
    projectName: RepoName,
    projectInfo: ProjectAccessInput
  ): Promise<ChangeInfo | undefined>;

  getGroups(
    filter: string,
    groupsPerPage: number,
    offset?: number
  ): Promise<GroupNameToGroupInfoMap | undefined>;

  getGroupConfig(
    group: GroupId | GroupName,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | undefined>;

  getIsAdmin(): Promise<boolean | undefined>;

  getIsGroupOwner(groupName?: GroupName): Promise<boolean>;

  saveGroupName(
    groupId: GroupId | GroupName,
    name: GroupName
  ): Promise<Response>;

  saveGroupOwner(
    groupId: GroupId | GroupName,
    ownerId: string
  ): Promise<Response>;

  saveGroupDescription(
    groupId: GroupId,
    description: string
  ): Promise<Response>;

  saveGroupOptions(
    groupId: GroupId,
    options: GroupOptionsInput
  ): Promise<Response>;

  saveChangeReview(
    changeNum: ChangeId | NumericChangeId,
    patchNum: RevisionId,
    review: ReviewInput,
    errFn?: ErrorCallback,
    fetch_detail?: boolean
  ): Promise<ReviewResult | undefined>;

  getChangeEdit(changeNum?: NumericChangeId): Promise<EditInfo | undefined>;

  getChangeActionURL(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum | undefined,
    endpoint: string
  ): Promise<string>;

  createChange(
    repo: RepoName,
    branch: BranchName,
    subject: string,
    topic?: string,
    isPrivate?: boolean,
    workInProgress?: boolean,
    baseChange?: ChangeId,
    baseCommit?: string
  ): Promise<ChangeInfo | undefined>;

  getChangeIncludedIn(
    changeNum: NumericChangeId
  ): Promise<IncludedInInfo | undefined>;

  /**
   * Looks up repo name in which change is located.
   *
   * Change -> repo association is cached. This will only make restAPI call (and
   * cache the result) if the repo name for the change is not already known.
   *
   * addRepoNameToCache can be used to add entry to the cache manually.
   *
   * If the lookup fails the promise rejects and result is not cached.
   */
  getRepoName(changeNum: NumericChangeId): Promise<RepoName>;

  /**
   * Populates cache for the future getRepoName(changeNum) lookup.
   *
   * The repo name is used for constructing of url for all change-based
   * endpoints.
   */
  addRepoNameToCache(changeNum: NumericChangeId, repo: RepoName): void;

  saveDiffDraft(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: CommentInput
  ): Promise<Response>;

  getPortedComments(
    changeNum: NumericChangeId,
    revision: RevisionId
  ): Promise<{[path: string]: CommentInfo[]} | undefined>;

  getPortedDrafts(
    changeNum: NumericChangeId,
    revision: RevisionId
  ): Promise<{[path: string]: DraftInfo[]} | undefined>;

  getDiffComments(
    changeNum: NumericChangeId
  ): Promise<{[path: string]: CommentInfo[]} | undefined>;
  getDiffComments(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffCommentsOutput>;
  getDiffComments(
    changeNum: NumericChangeId,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ):
    | Promise<{[path: string]: CommentInfo[]} | undefined>
    | Promise<GetDiffCommentsOutput>;

  getDiffRobotComments(
    changeNum: NumericChangeId
  ): Promise<PathToRobotCommentsInfoMap | undefined>;
  getDiffRobotComments(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffRobotCommentsOutput>;
  getDiffRobotComments(
    changeNum: NumericChangeId,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ):
    | Promise<GetDiffRobotCommentsOutput>
    | Promise<PathToRobotCommentsInfoMap | undefined>;

  /**
   * If the user is logged in, fetch the user's draft diff comments. If there
   * is no logged in user, the request is not made and the promise yields an
   * empty object.
   */
  getDiffDrafts(
    changeNum: NumericChangeId
  ): Promise<{[path: string]: DraftInfo[]} | undefined>;

  createGroup(config: GroupInput & {name: string}): Promise<Response>;

  getPlugins(
    filter: string,
    pluginsPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<{[pluginName: string]: PluginInfo} | undefined>;

  getDetailedChangesWithActions(
    changeNums: NumericChangeId[],
    needsSubmitRequirements?: boolean
  ): Promise<ChangeInfo[] | undefined>;

  getChanges(
    changesPerPage?: number,
    query?: string,
    offset?: 'n,z' | number,
    options?: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo[] | undefined>;
  getChangesForDashboard(
    changesPerPage?: number,
    query?: string[],
    offset?: 'n,z' | number,
    options?: string
  ): Promise<ChangeInfo[][] | undefined>;
  getChangesForMultipleQueries(
    changesPerPage?: number,
    query?: string[],
    offset?: 'n,z' | number,
    options?: string
  ): Promise<ChangeInfo[][] | undefined>;

  getDocumentationSearches(filter: string): Promise<DocResult[] | undefined>;

  getAccountAgreements(): Promise<ContributorAgreementInfo[] | undefined>;

  /**
   * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#list-groups
   */
  getAccountGroups(): Promise<GroupInfo[] | undefined>;

  getAccountDetails(
    userId: UserId,
    errFn?: ErrorCallback
  ): Promise<AccountDetailInfo | undefined>;

  getAccountStatus(userId: AccountId): Promise<string | undefined>;

  saveAccountAgreement(name: ContributorAgreementInput): Promise<Response>;

  generateAccountHttpPassword(): Promise<Password | undefined>;

  setAccountName(name: string): Promise<void>;

  setAccountUsername(username: string): Promise<void>;

  getWatchedProjects(): Promise<ProjectWatchInfo[] | undefined>;

  saveWatchedProjects(
    projects: ProjectWatchInfo[]
  ): Promise<ProjectWatchInfo[] | undefined>;

  deleteWatchedProjects(projects: ProjectWatchInfo[]): Promise<Response>;

  getSuggestedRepos(
    inputVal: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<NameToProjectInfoMap | undefined>;

  invalidateGroupsCache(): void;
  invalidateReposCache(): void;
  invalidateAccountsCache(): void;
  invalidateAccountsDetailCache(): void;
  invalidateAccountsEmailCache(): void;
  removeFromAttentionSet(
    changeNum: NumericChangeId,
    user: AccountId,
    reason: string
  ): Promise<Response>;
  addToAttentionSet(
    changeNum: NumericChangeId,
    user: AccountId | undefined | null,
    reason: string
  ): Promise<Response>;
  setAccountDisplayName(displayName: string): Promise<void>;
  setAccountStatus(status: string): Promise<void>;
  getAvatarChangeUrl(): Promise<string | undefined>;
  setDescription(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    desc: string
  ): Promise<Response>;
  deleteVote(
    changeNum: NumericChangeId,
    account: AccountId,
    label: string
  ): Promise<Response>;

  deleteComment(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    commentID: UrlEncodedCommentId,
    reason: string
  ): Promise<CommentInfo | undefined>;
  deleteDiffDraft(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: {id: UrlEncodedCommentId}
  ): Promise<Response>;

  deleteChangeCommitMessage(
    changeNum: NumericChangeId,
    messageId: ChangeMessageId
  ): Promise<Response>;

  removeChangeReviewer(
    changeNum: NumericChangeId,
    reviewerID: AccountId | EmailAddress | GroupId
  ): Promise<Response | undefined>;

  getGroupAuditLog(
    group: EncodedGroupId,
    errFn?: ErrorCallback
  ): Promise<GroupAuditEventInfo[] | undefined>;

  getGroupMembers(groupName: GroupId | GroupName): Promise<AccountInfo[]>;

  getIncludedGroup(
    groupName: GroupId | GroupName
  ): Promise<GroupInfo[] | undefined>;

  saveGroupMember(
    groupName: GroupId | GroupName,
    groupMember: AccountId
  ): Promise<AccountInfo | undefined>;

  saveIncludedGroup(
    groupName: GroupId | GroupName,
    includedGroup: GroupId,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | undefined>;

  deleteGroupMember(
    groupName: GroupId | GroupName,
    groupMember: AccountId
  ): Promise<Response>;

  deleteIncludedGroup(
    groupName: GroupId | GroupName,
    includedGroup: GroupId
  ): Promise<Response>;

  runRepoGC(repo: RepoName): Promise<Response>;
  getFileContent(
    changeNum: NumericChangeId,
    path: string,
    patchNum: PatchSetNum
  ): Promise<Response | Base64FileContent | undefined>;

  saveChangeEdit(
    changeNum: NumericChangeId,
    path: string,
    contents: string
  ): Promise<Response>;
  getRepoTags(
    filter: string,
    repo: RepoName,
    reposTagsPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<TagInfo[]>;

  setRepoHead(repo: RepoName, ref: GitRef): Promise<Response>;
  deleteRepoTags(repo: RepoName, ref: GitRef): Promise<Response>;
  deleteRepoBranches(repo: RepoName, ref: GitRef): Promise<Response>;
  saveRepoConfig(repo: RepoName, config: ConfigInput): Promise<Response>;
  saveRepoConfigForReview(
    repo: RepoName,
    config: ConfigInput
  ): Promise<ChangeInfo | undefined>;

  getRelatedChanges(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<RelatedChangesInfo | undefined>;

  getChangesSubmittedTogether(
    changeNum: NumericChangeId,
    options?: string[]
  ): Promise<SubmittedTogetherInfo | undefined>;

  getChangeConflicts(
    changeNum: NumericChangeId
  ): Promise<ChangeInfo[] | undefined>;

  getChangeCherryPicks(
    repo: RepoName,
    changeID: ChangeId,
    branch: BranchName
  ): Promise<ChangeInfo[] | undefined>;

  getChangesWithSameTopic(
    topic: string,
    options?: {
      openChangesOnly?: boolean;
      changeToExclude?: NumericChangeId;
    }
  ): Promise<ChangeInfo[] | undefined>;
  getChangesWithSimilarTopic(
    topic: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo[] | undefined>;
  getChangesWithSimilarHashtag(
    hashtag: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo[] | undefined>;

  /**
   * @return Whether there are pending diff draft sends.
   */
  hasPendingDiffDrafts(): number;
  /**
   * @return A promise that resolves when all pending
   * diff draft sends have resolved.
   */
  awaitPendingDiffDrafts(): Promise<void>;

  /**
   * Preview Stored Fix
   * Gets the diffs of all files for a certain {fix-id} associated with apply fix.
   * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#preview-stored-fix
   */
  getRobotCommentFixPreview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixId: FixId
  ): Promise<FilePathToDiffInfoMap | undefined>;

  /**
   * Preview Provided fix
   * Gets the diffs of all files for a provided fix replacements infos
   * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#preview-provided-fix
   */
  getFixPreview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixReplacementInfos: FixReplacementInfo[]
  ): Promise<FilePathToDiffInfoMap | undefined>;

  /**
   * Apply Provided Fix
   * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#apply-provided-fix
   */
  applyFixSuggestion(
    changeNum: NumericChangeId,
    fixPatchNum: PatchSetNum,
    fixReplacementInfos: FixReplacementInfo[],
    targetPatchNum?: PatchSetNum,
    errFn?: ErrorCallback
  ): Promise<Response>;

  /**
   * Apply Stored Fix
   * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#apply-stored-fix
   */
  applyRobotFixSuggestion(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixId: string
  ): Promise<Response>;

  /**
   * @param basePatchNum Negative values specify merge parent
   * index.
   * @param whitespace the ignore-whitespace level for the diff
   * algorithm.
   */
  getDiff(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string,
    whitespace?: IgnoreWhitespaceType,
    errFn?: ErrorCallback
  ): Promise<DiffInfo | undefined>;

  /**
   * Get blame information for the given diff.
   *
   * @param base If true, requests blame for the base of the
   *     diff, rather than the revision.
   */
  getBlame(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    base?: boolean
  ): Promise<BlameInfo[] | undefined>;

  getImagesForDiff(
    changeNum: NumericChangeId,
    diff: DiffInfo,
    patchRange: PatchRange
  ): Promise<ImagesForDiff>;

  getChangeRevisionActions(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<ActionNameToActionInfoMap | undefined>;

  confirmEmail(token: string): Promise<string | null>;

  getDefaultPreferences(): Promise<PreferencesInfo | undefined>;

  addAccountEmail(email: string): Promise<Response>;

  saveChangeStarred(
    changeNum: NumericChangeId,
    starred: boolean
  ): Promise<Response>;

  /**
   * Fetch a project dashboard definition.
   * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#get-dashboard
   */
  getDashboard(
    repo: RepoName,
    dashboard: DashboardId,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo | undefined>;

  deleteDraftComments(query: string): Promise<Response>;

  setChangeHashtag(
    changeNum: NumericChangeId,
    hashtag: HashtagsInput,
    errFn?: ErrorCallback
  ): Promise<Hashtag[] | undefined>;

  /**
   * Set change topic.
   *
   * Returns topic that the change has after the requests.
   */
  setChangeTopic(
    changeNum: NumericChangeId,
    topic?: string,
    errFn?: ErrorCallback
  ): Promise<string | undefined>;

  /**
   * Remove change topic.
   *
   * Returns topic that the change has after the requests. (ie. '' on success)
   */
  removeChangeTopic(
    changeNum: NumericChangeId,
    errFn?: ErrorCallback
  ): Promise<string | undefined>;

  getChangeFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined>;

  getChangeOrEditFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined>;

  getReviewedFiles(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<string[] | undefined>;

  saveFileReviewed(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    reviewed: boolean
  ): Promise<Response>;

  getTopMenus(): Promise<TopMenuEntryInfo[] | undefined>;

  getMergeable(changeNum: NumericChangeId): Promise<MergeableInfo | undefined>;

  putChangeCommitMessage(
    changeNum: NumericChangeId,
    message: string,
    committerEmail: string | null
  ): Promise<Response>;

  updateIdentityInChangeEdit(
    changeNum: NumericChangeId,
    name: string,
    email: string,
    type: string
  ): Promise<Response | undefined>;

  getChangeCommitInfo(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<CommitInfo | undefined>;
}
