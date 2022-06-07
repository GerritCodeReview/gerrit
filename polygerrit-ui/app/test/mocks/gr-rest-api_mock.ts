/* eslint-disable @typescript-eslint/no-unused-vars */
/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {
  AccountDetailInfo,
  AccountExternalIdInfo,
  AccountInfo,
  ServerInfo,
  ProjectInfo,
  AccountCapabilityInfo,
  SuggestedReviewerInfo,
  GroupNameToGroupInfoMap,
  ParsedJSON,
  EditPreferencesInfo,
  SshKeyInfo,
  RepoName,
  GpgKeyInfo,
  PreferencesInfo,
  EmailInfo,
  ProjectAccessInfo,
  CapabilityInfoMap,
  ChangeInfo,
  ProjectInfoWithName,
  GroupInfo,
  BranchInfo,
  ConfigInfo,
  EditInfo,
  DashboardInfo,
  ProjectAccessInfoMap,
  IncludedInInfo,
  CommentInfo,
  PathToCommentsInfoMap,
  PluginInfo,
  DocResult,
  ContributorAgreementInfo,
  Password,
  ProjectWatchInfo,
  NameToProjectInfoMap,
  GroupAuditEventInfo,
  Base64FileContent,
  TagInfo,
  RelatedChangesInfo,
  SubmittedTogetherInfo,
  FilePathToDiffInfoMap,
  BlameInfo,
  ImagesForDiff,
  ActionNameToActionInfoMap,
  Hashtag,
  FileNameToFileInfoMap,
  TopMenuEntryInfo,
  MergeableInfo,
  CommitInfo,
  GroupId,
  GroupName,
  UrlEncodedRepoName,
  NumericChangeId,
  PreferencesInput,
} from '../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../types/diff';
import {readResponsePayload} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  createAccountDetailWithId,
  createChange,
  createCommit,
  createConfig,
  createPreferences,
  createServerInfo,
  createSubmittedTogetherInfo,
} from '../test-data-generators';
import {
  createDefaultDiffPrefs,
  createDefaultEditPrefs,
} from '../../constants/constants';
import {ParsedChangeInfo} from '../../types/types';

export const grRestApiMock: RestApiService = {
  addAccountEmail(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  addAccountGPGKey(): Promise<Record<string, GpgKeyInfo>> {
    return Promise.resolve({});
  },
  addAccountSSHKey(): Promise<SshKeyInfo> {
    throw new Error('addAccountSSHKey() not implemented by RestApiMock.');
  },
  addToAttentionSet(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  applyFixSuggestion(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  awaitPendingDiffDrafts(): Promise<void> {
    return Promise.resolve();
  },
  confirmEmail(): Promise<string | null> {
    return Promise.resolve('');
  },
  createChange(): Promise<ChangeInfo | undefined> {
    throw new Error('createChange() not implemented by RestApiMock.');
  },
  createGroup(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  createRepo(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  createRepoBranch(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  createRepoTag(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountEmail(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountGPGKey(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountIdentity(): Promise<unknown> {
    return Promise.resolve(new Response());
  },
  deleteAccountSSHKey(): void {},
  deleteChangeCommitMessage(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteComment(): Promise<CommentInfo> {
    throw new Error('deleteComment() not implemented by RestApiMock.');
  },
  deleteDiffDraft(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteDraftComments(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteFileInChangeEdit(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  deleteGroupMember(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteIncludedGroup(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteRepoBranches(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteRepoTags(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteVote(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteWatchedProjects(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  executeChangeAction(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  finalize(): void {},
  generateAccountHttpPassword(): Promise<Password> {
    return Promise.resolve('asdf');
  },
  getAccount(): Promise<AccountDetailInfo | undefined> {
    return Promise.resolve(createAccountDetailWithId(1));
  },
  getAccountAgreements(): Promise<ContributorAgreementInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getAccountCapabilities(): Promise<AccountCapabilityInfo | undefined> {
    return Promise.resolve({});
  },
  getAccountDetails(): Promise<AccountDetailInfo | undefined> {
    return Promise.resolve(createAccountDetailWithId(1));
  },
  getAccountEmails(): Promise<EmailInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getAccountGPGKeys(): Promise<Record<string, GpgKeyInfo>> {
    return Promise.resolve({});
  },
  getAccountGroups(): Promise<GroupInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getAccountSSHKeys(): Promise<SshKeyInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getAccountStatus(): Promise<string | undefined> {
    return Promise.resolve('');
  },
  getAvatarChangeUrl(): Promise<string | undefined> {
    return Promise.resolve('');
  },
  getBlame(): Promise<BlameInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getCapabilities(): Promise<CapabilityInfoMap | undefined> {
    return Promise.resolve({});
  },
  getChange(): Promise<ChangeInfo | null> {
    throw new Error('getChange() not implemented by RestApiMock.');
  },
  getChangeActionURL(): Promise<string> {
    return Promise.resolve('');
  },
  getChangeCherryPicks(): Promise<ChangeInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getChangeCommitInfo(): Promise<CommitInfo | undefined> {
    return Promise.resolve(createCommit());
  },
  getChangeConflicts(): Promise<ChangeInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getChangeDetail(
    changeNum?: number | string
  ): Promise<ParsedChangeInfo | undefined> {
    if (changeNum === undefined) return Promise.resolve(undefined);
    return Promise.resolve(createChange() as ParsedChangeInfo);
  },
  getChangeEdit(): Promise<EditInfo | undefined> {
    return Promise.resolve(undefined);
  },
  getChangeFiles(): Promise<FileNameToFileInfoMap | undefined> {
    return Promise.resolve({});
  },
  getChangeIncludedIn(): Promise<IncludedInInfo | undefined> {
    throw new Error('getChangeIncludedIn() not implemented by RestApiMock.');
  },
  getChangeOrEditFiles(): Promise<FileNameToFileInfoMap | undefined> {
    return Promise.resolve({});
  },
  getChangeRevisionActions(): Promise<ActionNameToActionInfoMap | undefined> {
    return Promise.resolve({});
  },
  getChangeSuggestedCCs(): Promise<SuggestedReviewerInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getChangeSuggestedReviewers(): Promise<SuggestedReviewerInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getChanges() {
    return Promise.resolve([]);
  },
  getChangesForMultipleQueries() {
    return Promise.resolve([]);
  },
  getChangesSubmittedTogether(): Promise<SubmittedTogetherInfo | undefined> {
    return Promise.resolve(createSubmittedTogetherInfo());
  },
  getDetailedChangesWithActions(changeNums: NumericChangeId[]) {
    return Promise.resolve(
      changeNums.map(changeNum => {
        return {
          ...createChange(),
          actions: {},
          _number: changeNum,
          subject: `Subject ${changeNum}`,
        };
      })
    );
  },
  getChangesWithSameTopic(): Promise<ChangeInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getChangesWithSimilarTopic(): Promise<ChangeInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getChangesWithSimilarHashtag(): Promise<ChangeInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getConfig(): Promise<ServerInfo | undefined> {
    return Promise.resolve(createServerInfo());
  },
  getDashboard(): Promise<DashboardInfo | undefined> {
    return Promise.resolve(undefined);
  },
  getDefaultPreferences(): Promise<PreferencesInfo | undefined> {
    throw new Error('getDefaultPreferences() not implemented by RestApiMock.');
  },
  getDiff(): Promise<DiffInfo | undefined> {
    throw new Error('getDiff() not implemented by RestApiMock.');
  },
  getDiffComments() {
    // NOTE: This method can not be typed properly due to overloads.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return Promise.resolve({}) as any;
  },
  getDiffDrafts() {
    // NOTE: This method can not be typed properly due to overloads.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return Promise.resolve({}) as any;
  },
  getDiffPreferences(): Promise<DiffPreferencesInfo | undefined> {
    return Promise.resolve(createDefaultDiffPrefs());
  },
  getDiffRobotComments() {
    // NOTE: This method can not be typed properly due to overloads.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return Promise.resolve({}) as any;
  },
  getDocumentationSearches(): Promise<DocResult[] | undefined> {
    return Promise.resolve([]);
  },
  getEditPreferences(): Promise<EditPreferencesInfo | undefined> {
    return Promise.resolve(createDefaultEditPrefs());
  },
  getExternalIds(): Promise<AccountExternalIdInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getFileContent(): Promise<Response | Base64FileContent | undefined> {
    return Promise.resolve(new Response());
  },
  getFromProjectLookup(): Promise<RepoName | undefined> {
    throw new Error('getFromProjectLookup() not implemented by RestApiMock.');
  },
  getGroupAuditLog(): Promise<GroupAuditEventInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getGroupConfig(id: GroupId | GroupName): Promise<GroupInfo | undefined> {
    return Promise.resolve({
      id: id as GroupId,
    });
  },
  getGroupMembers(): Promise<AccountInfo[]> {
    return Promise.resolve([]);
  },
  getGroups(): Promise<GroupNameToGroupInfoMap | undefined> {
    return Promise.resolve({});
  },
  getImagesForDiff(): Promise<ImagesForDiff> {
    throw new Error('getImagesForDiff() not implemented by RestApiMock.');
  },
  getIncludedGroup(): Promise<GroupInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getIsAdmin(): Promise<boolean | undefined> {
    return Promise.resolve(false);
  },
  getIsGroupOwner(): Promise<boolean> {
    return Promise.resolve(false);
  },
  getLoggedIn(): Promise<boolean> {
    return Promise.resolve(true);
  },
  getMergeable(): Promise<MergeableInfo | undefined> {
    throw new Error('getMergeable() not implemented by RestApiMock.');
  },
  getPlugins(): Promise<{[p: string]: PluginInfo} | undefined> {
    return Promise.resolve({});
  },
  getPortedComments(): Promise<PathToCommentsInfoMap | undefined> {
    return Promise.resolve({});
  },
  getPortedDrafts(): Promise<PathToCommentsInfoMap | undefined> {
    return Promise.resolve({});
  },
  getPreferences(): Promise<PreferencesInfo | undefined> {
    return Promise.resolve(createPreferences());
  },
  getProjectConfig(): Promise<ConfigInfo | undefined> {
    return Promise.resolve(createConfig());
  },
  getRelatedChanges(): Promise<RelatedChangesInfo | undefined> {
    return Promise.resolve({changes: []});
  },
  getRepo(repo: RepoName): Promise<ProjectInfo | undefined> {
    return Promise.resolve({
      id: repo as string as UrlEncodedRepoName,
      name: repo,
    });
  },
  getRepoAccess(): Promise<ProjectAccessInfoMap | undefined> {
    return Promise.resolve({});
  },
  getRepoAccessRights(): Promise<ProjectAccessInfo | undefined> {
    return Promise.resolve(undefined);
  },
  getRepoBranches(): Promise<BranchInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getRepoDashboards(): Promise<DashboardInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getRepoTags(): Promise<TagInfo[]> {
    return Promise.resolve([]);
  },
  getRepos(): Promise<ProjectInfoWithName[] | undefined> {
    return Promise.resolve([]);
  },
  getResponseObject(response: Response): Promise<ParsedJSON> {
    return readResponsePayload(response).then(payload => payload.parsed);
  },
  getReviewedFiles(): Promise<string[] | undefined> {
    return Promise.resolve([]);
  },
  getRobotCommentFixPreview(): Promise<FilePathToDiffInfoMap | undefined> {
    return Promise.resolve({});
  },
  getSuggestedAccounts(): Promise<AccountInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getSuggestedGroups(): Promise<GroupNameToGroupInfoMap | undefined> {
    return Promise.resolve({});
  },
  getSuggestedProjects(): Promise<NameToProjectInfoMap | undefined> {
    return Promise.resolve({});
  },
  getTopMenus(): Promise<TopMenuEntryInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getVersion(): Promise<string | undefined> {
    return Promise.resolve('');
  },
  getWatchedProjects(): Promise<ProjectWatchInfo[] | undefined> {
    return Promise.resolve([]);
  },
  hasPendingDiffDrafts(): number {
    return 0;
  },
  invalidateAccountsCache(): void {},
  invalidateGroupsCache(): void {},
  invalidateReposCache(): void {},
  invalidateAccountsDetailCache(): void {},
  probePath(): Promise<boolean> {
    return Promise.resolve(true);
  },
  putChangeCommitMessage(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  queryChangeFiles(): Promise<string[] | undefined> {
    return Promise.resolve([]);
  },
  removeChangeReviewer(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  removeFromAttentionSet(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  renameFileInChangeEdit(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  restoreFileInChangeEdit(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  runRepoGC(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveAccountAgreement(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveChangeEdit(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveChangeReview() {
    return Promise.resolve(new Response());
  },
  saveChangeStarred(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveDiffDraft(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveDiffPreferences(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveEditPreferences(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveFileReviewed(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveFileUploadChangeEdit(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  saveGroupDescription(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveGroupMember(): Promise<AccountInfo> {
    return Promise.resolve({});
  },
  saveGroupName(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveGroupOptions(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveGroupOwner(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveIncludedGroup(): Promise<GroupInfo | undefined> {
    throw new Error('saveIncludedGroup() not implemented by RestApiMock.');
  },
  savePreferences(input: PreferencesInput): Promise<PreferencesInfo> {
    const info = input as PreferencesInfo;
    return Promise.resolve({...info});
  },
  saveRepoConfig(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveWatchedProjects(): Promise<ProjectWatchInfo[]> {
    return Promise.resolve([]);
  },
  send() {
    return Promise.resolve(new Response());
  },
  setAccountDisplayName(): Promise<void> {
    return Promise.resolve();
  },
  setAccountName(): Promise<void> {
    return Promise.resolve();
  },
  setAccountStatus(): Promise<void> {
    return Promise.resolve();
  },
  setAccountUsername(): Promise<void> {
    return Promise.resolve();
  },
  setChangeHashtag(): Promise<Hashtag[]> {
    return Promise.resolve([]);
  },
  setChangeTopic(): Promise<string> {
    return Promise.resolve('');
  },
  setDescription(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  setInProjectLookup(): void {},
  setPreferredAccountEmail(): Promise<void> {
    return Promise.resolve();
  },
  setRepoAccessRights(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  setRepoAccessRightsForReview(): Promise<ChangeInfo> {
    throw new Error('setRepoAccessRightsForReview() not implemented by mock.');
  },
  setRepoHead(): Promise<Response> {
    return Promise.resolve(new Response());
  },
};
