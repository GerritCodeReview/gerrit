/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {FlowInfo} from '../../api/rest-api';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  AccountExternalIdInfo,
  AccountInfo,
  AccountStateInfo,
  ActionNameToActionInfoMap,
  AuthTokenInfo,
  Base64FileContent,
  BlameInfo,
  BranchInfo,
  CapabilityInfoMap,
  ChangeInfo,
  CommentInfo,
  CommitInfo,
  ConfigInfo,
  ContributorAgreementInfo,
  DashboardInfo,
  DocResult,
  DraftInfo,
  EditInfo,
  EditPreferencesInfo,
  EmailInfo,
  FileNameToFileInfoMap,
  FilePathToDiffInfoMap,
  GpgKeyInfo,
  GroupAuditEventInfo,
  GroupId,
  GroupInfo,
  GroupName,
  GroupNameToGroupInfoMap,
  Hashtag,
  ImagesForDiff,
  IncludedInInfo,
  MergeableInfo,
  NameToProjectInfoMap,
  NumericChangeId,
  PluginInfo,
  PreferencesInfo,
  PreferencesInput,
  ProjectAccessInfo,
  ProjectInfo,
  ProjectInfoWithName,
  ProjectWatchInfo,
  RelatedChangesInfo,
  RepoAccessInfoMap,
  RepoName,
  ServerInfo,
  SshKeyInfo,
  SubmitRequirementInfo,
  SubmittedTogetherInfo,
  SuggestedReviewerInfo,
  TagInfo,
  TopMenuEntryInfo,
  UrlEncodedRepoName,
  ValidationOptionsInfo,
} from '../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../types/diff';
import {
  createAccountDetailWithId,
  createChange,
  createCommit,
  createConfig,
  createMergeable,
  createServerInfo,
  createSubmittedTogetherInfo,
} from '../test-data-generators';
import {
  createDefaultDiffPrefs,
  createDefaultEditPrefs,
  createDefaultPreferences,
} from '../../constants/constants';
import {ParsedChangeInfo} from '../../types/types';
import {ErrorCallback} from '../../api/rest';
import {LabelDefinitionInfo} from '../../api/rest-api';

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
  deleteAccount(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountEmail(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountGPGKey(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountIdentity(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteAccountSSHKey(): void {},
  deleteChangeCommitMessage(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteComment(): Promise<CommentInfo | undefined> {
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
  executeChangeAction(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  finalize(): void {},
  getAccountAuthTokens(): Promise<AuthTokenInfo[] | undefined> {
    return Promise.resolve([{id: 'tokenId', token: 'asdf'}]);
  },
  deleteAccountAuthToken(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  generateAccountAuthToken(
    tokenId: string
  ): Promise<AuthTokenInfo | undefined> {
    return Promise.resolve({id: tokenId, token: 'asdf'});
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
  getAccountEmailsFor(): Promise<EmailInfo[] | undefined> {
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
  getAccountState(): Promise<AccountStateInfo | undefined> {
    throw new Error('getAccountState() not implemented by RestApiMock.');
  },
  getAccountStatus(): Promise<string | undefined> {
    return Promise.resolve('');
  },
  getAllRevisionFiles() {
    return Promise.resolve(undefined);
  },
  getAvatarChangeUrl(): Promise<string | undefined> {
    return Promise.resolve('');
  },
  getBlame(): Promise<BlameInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getPatchContent(): Promise<string | undefined> {
    return Promise.resolve(undefined);
  },
  getCapabilities(): Promise<CapabilityInfoMap | undefined> {
    return Promise.resolve({});
  },
  getChange(): Promise<ChangeInfo | undefined> {
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
  getChangesForDashboard() {
    return Promise.resolve([]);
  },
  getChangesForMultipleQueries() {
    return Promise.resolve([]);
  },
  getChangesSubmittedTogether(): Promise<SubmittedTogetherInfo | undefined> {
    return Promise.resolve(createSubmittedTogetherInfo());
  },
  getDetailedChangesWithActions(changeNums: NumericChangeId[], _?: boolean) {
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
  getRepoName(): Promise<RepoName> {
    throw new Error('getRepoName() not implemented by RestApiMock.');
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
    return Promise.resolve(createMergeable());
  },
  getPlugins(): Promise<{[p: string]: PluginInfo} | undefined> {
    return Promise.resolve({});
  },
  getPortedComments(): Promise<{[path: string]: CommentInfo[]} | undefined> {
    return Promise.resolve({});
  },
  getPortedDrafts(): Promise<{[path: string]: DraftInfo[]} | undefined> {
    return Promise.resolve({});
  },
  getPreferences(): Promise<PreferencesInfo | undefined> {
    return Promise.resolve(createDefaultPreferences());
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
  getRepoAccess(): Promise<RepoAccessInfoMap | undefined> {
    return Promise.resolve({});
  },
  getRepoSubmitRequirements(): Promise<SubmitRequirementInfo[] | undefined> {
    return Promise.resolve([]);
  },
  createSubmitRequirement(): Promise<SubmitRequirementInfo | undefined> {
    return Promise.resolve(undefined);
  },
  updateSubmitRequirement(): Promise<SubmitRequirementInfo | undefined> {
    return Promise.resolve(undefined);
  },
  deleteSubmitRequirement(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveRepoSubmitRequirementsForReview(): Promise<ChangeInfo | undefined> {
    return Promise.resolve(undefined);
  },
  getRepoLabels(): Promise<LabelDefinitionInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getRepoLabel(): Promise<LabelDefinitionInfo | undefined> {
    return Promise.resolve(undefined);
  },
  createRepoLabel(): Promise<LabelDefinitionInfo | undefined> {
    return Promise.resolve(undefined);
  },
  updateRepoLabel(): Promise<LabelDefinitionInfo | undefined> {
    return Promise.resolve(undefined);
  },
  saveRepoLabelsForReview(): Promise<ChangeInfo | undefined> {
    return Promise.resolve(undefined);
  },
  deleteRepoLabel(): Promise<Response> {
    return Promise.resolve(new Response());
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
  getReviewedFiles(): Promise<string[] | undefined> {
    return Promise.resolve([]);
  },
  getFixPreview(): Promise<FilePathToDiffInfoMap | undefined> {
    return Promise.resolve({});
  },
  queryAccounts(): Promise<AccountInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getAccountSuggestions(): Promise<AccountInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getSuggestedGroups(): Promise<GroupNameToGroupInfoMap | undefined> {
    return Promise.resolve({});
  },
  getSuggestedRepos(): Promise<NameToProjectInfoMap | undefined> {
    return Promise.resolve({});
  },
  getTopMenus(): Promise<TopMenuEntryInfo[] | undefined> {
    return Promise.resolve([]);
  },
  getValidationOptions(): Promise<ValidationOptionsInfo | undefined> {
    return Promise.resolve(undefined);
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
  invalidateAccountsEmailCache(): void {},
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
    return Promise.resolve({});
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
  saveGroupMember(): Promise<AccountInfo | undefined> {
    return Promise.resolve({});
  },
  saveGroupName(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  saveGroupOptions(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  deleteGroup(): Promise<Response> {
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
  saveRepoConfigForReview(): Promise<ChangeInfo | undefined> {
    throw new Error('saveRepoConfigForReview() not implemented by mock.');
  },
  saveWatchedProjects(): Promise<ProjectWatchInfo[] | undefined> {
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
  setChangeHashtag(): Promise<Hashtag[] | undefined> {
    return Promise.resolve([]);
  },
  setChangeTopic(): Promise<string | undefined> {
    return Promise.resolve('');
  },
  removeChangeTopic(
    changeNum: NumericChangeId,
    errFn?: ErrorCallback
  ): Promise<string | undefined> {
    return this.setChangeTopic(changeNum, '', errFn);
  },
  setDescription(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  addRepoNameToCache(): void {},
  setPreferredAccountEmail(): Promise<void> {
    return Promise.resolve();
  },
  setRepoAccessRights(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  setRepoAccessRightsForReview(): Promise<ChangeInfo | undefined> {
    throw new Error('setRepoAccessRightsForReview() not implemented by mock.');
  },
  setRepoHead(): Promise<Response> {
    return Promise.resolve(new Response());
  },
  updateIdentityInChangeEdit(): Promise<Response | undefined> {
    return Promise.resolve(new Response());
  },
  getFlow(): Promise<FlowInfo | undefined> {
    return Promise.resolve(undefined);
  },
  listFlows(): Promise<FlowInfo[] | undefined> {
    return Promise.resolve([]);
  },
  createFlow(): Promise<FlowInfo | undefined> {
    return Promise.resolve(undefined);
  },
  deleteFlow(): Promise<Response> {
    return Promise.resolve(new Response());
  },
};
