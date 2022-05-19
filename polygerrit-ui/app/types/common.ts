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
import {CommentRange} from '../api/core';
import {
  ChangeStatus,
  ProjectState,
  SubmitType,
  InheritedBooleanInfoConfiguredValue,
  PermissionAction,
  CommentSide,
  AppTheme,
  DateFormat,
  TimeFormat,
  EmailStrategy,
  DefaultBase,
  UserPriority,
  DiffViewMode,
  DraftsAction,
  NotifyType,
  EmailFormat,
  MergeStrategy,
} from '../constants/constants';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {
  AccountId,
  AccountDetailInfo,
  AccountInfo,
  AccountsConfigInfo,
  ActionInfo,
  ActionNameToActionInfoMap,
  ApprovalInfo,
  AuthInfo,
  AvatarInfo,
  BasePatchSetNum,
  BranchName,
  BrandType,
  ChangeConfigInfo,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  ChangeMessageId,
  ChangeMessageInfo,
  ChangeSubmissionId,
  CommentLinkInfo,
  CommentLinks,
  CommitId,
  CommitInfo,
  ConfigArrayParameterInfo,
  ConfigInfo,
  ConfigListParameterInfo,
  ConfigParameterInfo,
  ConfigParameterInfoBase,
  ContributorAgreementInfo,
  DetailedLabelInfo,
  DownloadInfo,
  DownloadSchemeInfo,
  EmailAddress,
  FetchInfo,
  FileInfo,
  GerritInfo,
  GitPersonInfo,
  GitRef,
  GpgKeyId,
  GpgKeyInfo,
  GroupId,
  GroupInfo,
  GroupName,
  GroupOptionsInfo,
  Hashtag,
  InheritedBooleanInfo,
  LabelInfo,
  LabelNameToInfoMap,
  LabelNameToLabelTypeInfoMap,
  LabelNameToValuesMap,
  LabelTypeInfo,
  LabelTypeInfoValues,
  LabelValueToDescriptionMap,
  MaxObjectSizeLimitInfo,
  NumericChangeId,
  ParentCommitInfo,
  PatchSetNum,
  PluginConfigInfo,
  PluginNameToPluginParametersMap,
  PluginParameterToConfigParameterInfoMap,
  ProjectInfo,
  ProjectInfoWithName,
  QuickLabelInfo,
  ReceiveInfo,
  RepoName,
  Requirement,
  RequirementType,
  ReviewInputTag,
  ReviewerState,
  ReviewerUpdateInfo,
  Reviewers,
  RevisionInfo,
  SchemesInfoMap,
  ServerInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  TimezoneOffset,
  TopicName,
  UrlEncodedRepoName,
  UserConfigInfo,
  VotingRangeInfo,
  WebLinkInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
} from '../api/rest-api';
import {DiffInfo, IgnoreWhitespaceType} from './diff';

export {
  AccountId,
  AccountDetailInfo,
  AccountInfo,
  AccountsConfigInfo,
  ActionInfo,
  ActionNameToActionInfoMap,
  ApprovalInfo,
  AuthInfo,
  AvatarInfo,
  BasePatchSetNum,
  BranchName,
  BrandType,
  ChangeConfigInfo,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  ChangeMessageId,
  ChangeMessageInfo,
  ChangeSubmissionId,
  CommentLinkInfo,
  CommentLinks,
  CommentRange,
  CommitId,
  CommitInfo,
  ConfigArrayParameterInfo,
  ConfigInfo,
  ConfigListParameterInfo,
  ConfigParameterInfo,
  ConfigParameterInfoBase,
  ContributorAgreementInfo,
  DetailedLabelInfo,
  DownloadInfo,
  DownloadSchemeInfo,
  EmailAddress,
  FileInfo,
  GerritInfo,
  GitPersonInfo,
  GitRef,
  GpgKeyId,
  GpgKeyInfo,
  GroupId,
  GroupInfo,
  GroupName,
  GroupOptionsInfo,
  Hashtag,
  InheritedBooleanInfo,
  LabelInfo,
  LabelNameToInfoMap,
  LabelNameToLabelTypeInfoMap,
  LabelNameToValuesMap,
  LabelTypeInfo,
  LabelTypeInfoValues,
  LabelValueToDescriptionMap,
  MaxObjectSizeLimitInfo,
  NumericChangeId,
  ParentCommitInfo,
  PatchSetNum,
  PluginConfigInfo,
  PluginNameToPluginParametersMap,
  PluginParameterToConfigParameterInfoMap,
  ProjectInfo,
  ProjectInfoWithName,
  QuickLabelInfo,
  ReceiveInfo,
  RepoName,
  Requirement,
  RequirementType,
  ReviewInputTag,
  ReviewerUpdateInfo,
  Reviewers,
  RevisionInfo,
  SchemesInfoMap,
  ServerInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  TimezoneOffset,
  TopicName,
  UrlEncodedRepoName,
  UserConfigInfo,
  VotingRangeInfo,
  WebLinkInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
};

/*
 * In T, make a set of properties whose keys are in the union K required
 */
export type RequireProperties<T, K extends keyof T> = Omit<T, K> &
  Required<Pick<T, K>>;

export type PropertyType<T, K extends keyof T> = ReturnType<() => T[K]>;

export type ElementPropertyDeepChange<
  T,
  K extends keyof T
> = PolymerDeepPropertyChange<PropertyType<T, K>, PropertyType<T, K>>;

/**
 * Type alias for parsed json object to make code cleaner
 */
export type ParsedJSON = BrandType<unknown, '_parsedJSON'>;

export type RevisionPatchSetNum = BrandType<'edit' | number, '_patchSet'>;

export type PatchSetNumber = BrandType<number, '_patchSet'>;

export const EditPatchSetNum = 'edit' as RevisionPatchSetNum;

// TODO(TS): This is not correct, it is better to have a separate ApiPatchSetNum
// without 'parent'.
export const ParentPatchSetNum = 'PARENT' as BasePatchSetNum;

export type RobotId = BrandType<string, '_robotId'>;

export type RobotRunId = BrandType<string, '_robotRunId'>;

// RevisionId '0' is the same as 'current'. However, we want to avoid '0'
// in our code, so it is not added here as a possible value.
export type RevisionId = 'current' | CommitId | PatchSetNum;

// The UUID of the suggested fix.
export type FixId = BrandType<string, '_fixId'>;

// The URL encoded UUID of the comment
export type UrlEncodedCommentId = BrandType<string, '_urlEncodedCommentId'>;

// The ID of the dashboard, in the form of '<ref>:<path>'
export type DashboardId = BrandType<string, '_dahsboardId'>;

// The refs/tags/ prefix is omitted in Tag name
export type TagName = BrandType<string, '_tagName'>;

export type LabelName = BrandType<string, '_labelName'>;

// The Encoded UUID of the group
export type EncodedGroupId = BrandType<string, '_encodedGroupId'>;

// https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#contributor-agreement-input
export interface ContributorAgreementInput {
  name?: string;
}

/**
 * ChangeView request change detail with ALL_REVISIONS option set.
 * The response always contains current_revision and revisions.
 */
export type ChangeViewChangeInfo = RequireProperties<
  ChangeInfo,
  'current_revision' | 'revisions'
>;

export function isAccount(
  x: AccountInfo | GroupInfo | GitPersonInfo
): x is AccountInfo {
  const account = x as AccountInfo;
  return account._account_id !== undefined || account.email !== undefined;
}

export function isGroup(x: AccountInfo | GroupInfo): x is GroupInfo {
  return (x as GroupInfo).id !== undefined;
}

/**
 * The AccountExternalIdInfo entity contains information for an external id of
 * an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-external-id-info
 */
export interface AccountExternalIdInfo {
  identity: string;
  email_address?: string;
  trusted?: boolean;
  can_delete?: boolean;
}

/**
 * The GroupAuditEventInfo entity contains information about an auditevent of a group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html#group-audit-event-info
 */
export type GroupAuditEventInfo =
  | GroupAuditAccountEventInfo
  | GroupAuditGroupEventInfo;

export enum GroupAuditEventType {
  ADD_USER = 'ADD_USER',
  REMOVE_USER = 'REMOVE_USER',
  ADD_GROUP = 'ADD_GROUP',
  REMOVE_GROUP = 'REMOVE_GROUP',
}

export interface GroupAuditEventInfoBase {
  user: AccountInfo;
  date: Timestamp;
}

export interface GroupAuditAccountEventInfo extends GroupAuditEventInfoBase {
  type: GroupAuditEventType.ADD_USER | GroupAuditEventType.REMOVE_USER;
  member: AccountInfo;
}

export interface GroupAuditGroupEventInfo extends GroupAuditEventInfoBase {
  type: GroupAuditEventType.ADD_GROUP | GroupAuditEventType.REMOVE_GROUP;
  member: GroupInfo;
}

export function isGroupAuditAccountEventInfo(
  x: GroupAuditEventInfo
): x is GroupAuditAccountEventInfo {
  return (
    x.type === GroupAuditEventType.ADD_USER ||
    x.type === GroupAuditEventType.REMOVE_USER
  );
}

export function isGroupAuditGroupEventInfo(
  x: GroupAuditEventInfo
): x is GroupAuditGroupEventInfo {
  return (
    x.type === GroupAuditEventType.ADD_GROUP ||
    x.type === GroupAuditEventType.REMOVE_GROUP
  );
}

/**
 * The GroupBaseInfo entity contains base information about the group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#group-base-info
 */
export interface GroupBaseInfo {
  id: GroupId;
  name: GroupName;
}

export type GroupNameToGroupInfoMap = {[groupName: string]: GroupInfo};

/**
 * The 'GroupInput' entity contains information for the creation of a new
 * internal group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html#group-input
 */
export interface GroupInput {
  name?: GroupName;
  uuid?: string;
  description?: string;
  visible_to_all?: string;
  owner_id?: string;
  members?: string[];
}

/**
 * New options for a group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupOptionsInput {
  visible_to_all: boolean;
}

/**
 * The GroupsInput entity contains information about groups that should be
 * included into a group or that should be deleted from a group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupsInput {
  _one_group?: string;
  groups?: string[];
}

/**
 * The MembersInput entity contains information about accounts that should be
 * added as members to a group or that should be deleted from the group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface MembersInput {
  _one_member?: string;
  members?: string[];
}

export interface CommitInfoWithRequiredCommit extends CommitInfo {
  commit: CommitId;
}

/**
 * Standalone Commit Info.
 * Same as CommitInfo, except `commit` is required
 * as it is only optional when used inside of the RevisionInfo.
 */
export interface StandaloneCommitInfo extends CommitInfo {
  commit: CommitId;
}

/**
 * The GpgKeysInput entity contains information for adding/deleting GPG keys.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#gpg-keys-input
 */
export interface GpgKeysInput {
  add?: string[];
  delete?: string[];
}

/**
 * The CacheInfo entity contains information about a cache.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface CacheInfo {
  name: string;
  type: string;
  entries: EntriesInfo;
  average_get?: string;
  hit_ratio: HitRatioInfo;
}

/**
 * The CacheOperationInput entity contains information about an operation that
 * should be executed on caches.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface CacheOperationInput {
  operation: string;
  caches?: string[];
}

/**
 * The CapabilityInfo entity contains information about a capability.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#capability-info
 */
export interface CapabilityInfo {
  id: string;
  name: string;
}

export type CapabilityInfoMap = {[id: string]: CapabilityInfo};

/**
 * The ChangeIndexConfigInfo entity contains information about Gerrit
 * configuration from the index.change section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#change-index-config-info
 */
export interface ChangeIndexConfigInfo {
  index_mergeable?: boolean;
}

/**
 * The CheckAccountExternalIdsResultInfo entity contains the result of running
 * the account external ID consistency check.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface CheckAccountExternalIdsResultInfo {
  problems: string;
}

/**
 * The CheckAccountsResultInfo entity contains the result of running the account
 * consistency check.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface CheckAccountsResultInfo {
  problems: string;
}

/**
 * The CheckGroupsResultInfo entity contains the result of running the group
 * consistency check.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface CheckGroupsResultInfo {
  problems: string;
}

/**
 * The ConsistencyCheckInfo entity contains the results of running consistency
 * checks.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ConsistencyCheckInfo {
  check_accounts_result?: CheckAccountsResultInfo;
  check_account_external_ids_result?: CheckAccountExternalIdsResultInfo;
  check_groups_result?: CheckGroupsResultInfo;
}

/**
 * The ConsistencyCheckInput entity contains information about which consistency
 * checks should be run.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ConsistencyCheckInput {
  check_accounts?: string;
  check_account_external_ids?: string;
  check_groups?: string;
}

/**
 * The ConsistencyProblemInfo entity contains information about a consistency
 * problem.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ConsistencyProblemInfo {
  status: string;
  message: string;
}

/**
 * The entity describes the result of a reload of gerrit.config.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ConfigUpdateInfo {
  applied: string;
  rejected: string;
}

/**
 * The entity describes an updated config value.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ConfigUpdateEntryInfo {
  config_key: string;
  old_value: string;
  new_value: string;
}

/**
 * The EmailConfirmationInput entity contains information for confirming an
 * email address.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface EmailConfirmationInput {
  token: string;
}

/**
 * The EntriesInfo entity contains information about the entries in acache.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface EntriesInfo {
  mem?: string;
  disk?: string;
  space?: string;
}

/**
 * The IndexConfigInfo entity contains information about Gerrit configuration
 * from the index section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#index-config-info
 */
export interface IndexConfigInfo {
  change: ChangeIndexConfigInfo;
}

/**
 * The HitRatioInfo entity contains information about the hit ratio of a cache.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface HitRatioInfo {
  mem: string;
  disk?: string;
}

/**
 * The IndexChangesInput contains a list of numerical changes IDs to index.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface IndexChangesInput {
  changes: string;
}

/**
 * The JvmSummaryInfo entity contains information about the JVM.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface JvmSummaryInfo {
  vm_vendor: string;
  vm_name: string;
  vm_version: string;
  os_name: string;
  os_version: string;
  os_arch: string;
  user: string;
  host?: string;
  current_working_directory: string;
  site: string;
}

/**
 * The MemSummaryInfo entity contains information about the current memory
 * usage.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface MemSummaryInfo {
  total: string;
  used: string;
  free: string;
  buffers: string;
  max: string;
  open_files?: string;
}

/**
 * The SummaryInfo entity contains information about the current state of the
 * server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface SummaryInfo {
  task_summary: TaskSummaryInfo;
  mem_summary: MemSummaryInfo;
  thread_summary: ThreadSummaryInfo;
  jvm_summary?: JvmSummaryInfo;
}

/**
 * The TaskInfo entity contains information about a task in a background work
 * queue.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface TaskInfo {
  id: string;
  state: string;
  start_time: string;
  delay: string;
  command: string;
  remote_name?: string;
  project?: string;
}

/**
 * The TaskSummaryInfo entity contains information about the current tasks.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface TaskSummaryInfo {
  total?: string;
  running?: string;
  ready?: string;
  sleeping?: string;
}

/**
 * The ThreadSummaryInfo entity contains information about the current threads.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ThreadSummaryInfo {
  cpus: string;
  threads: string;
  counts: string;
}

/**
 * The TopMenuEntryInfo entity contains information about a top menu entry.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#top-menu-entry-info
 */
export interface TopMenuEntryInfo {
  name: string;
  items: TopMenuItemInfo[];
}

/**
 * The TopMenuItemInfo entity contains information about a menu item ina top
 * menu entry.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#top-menu-item-info
 */
export interface TopMenuItemInfo {
  url: string;
  name: string;
  target: string;
  id?: string;
}

/**
 * The CommentInfo entity contains information about an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-info
 */
export interface CommentInfo {
  id: UrlEncodedCommentId;
  updated: Timestamp;
  // TODO(TS): Make this required. Every comment must have patch_set set.
  patch_set?: PatchSetNum;
  path?: string;
  side?: CommentSide;
  parent?: number;
  line?: number;
  range?: CommentRange;
  in_reply_to?: UrlEncodedCommentId;
  message?: string;
  author?: AccountInfo;
  tag?: string;
  unresolved?: boolean;
  change_message_id?: string;
  commit_id?: string;
  context_lines?: ContextLine[];
  source_content_type?: string;
}

export type PathToCommentsInfoMap = {[path: string]: CommentInfo[]};

/**
 * The ContextLine entity contains the line number and line text of a single line of the source file content..
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#context-line
 */
export interface ContextLine {
  line_number: number;
  context_line: string;
}

export type NameToProjectInfoMap = {[projectName: string]: ProjectInfo};

export type FilePathToDiffInfoMap = {[path: string]: DiffInfo};

/**
 * The RangeInfo entity stores the coordinates of a range.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#range-info
 */
export interface RangeInfo {
  start: number;
  end: number;
}

/**
 * The BlameInfo entity stores the commit metadata with the row coordinates where it applies.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#blame-info
 */
export interface BlameInfo {
  author: string;
  id: string;
  time: number;
  commit_msg: string;
  ranges: RangeInfo[];
}

/**
 * Images are retrieved by using the file content API and the body is just the
 * HTML response.
 * TODO(TS): where is the source of this type ? I don't find it in doc
 */
export interface ImageInfo {
  body: string;
  type: string | undefined;
  _name?: string;
  _expectedType?: string;
  _width?: number;
  _height?: number;
}

/**
 * The ProjectAccessInfo entity contains information about the access rights for
 * a project.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-access.html#project-access-info
 */
export interface ProjectAccessInfo {
  revision: string; // The revision of the refs/meta/config branch from which the access rights were loaded
  inherits_from?: ProjectInfo; // not set for the All-Project project
  local: LocalAccessSectionInfo;
  is_owner?: boolean;
  owner_of: GitRef[];
  can_upload?: boolean;
  can_add?: boolean;
  can_add_tags?: boolean;
  config_visible?: boolean;
  groups: ProjectAccessGroups;
  config_web_links: WebLinkInfo[];
}

export type ProjectAccessInfoMap = {[projectName: string]: ProjectAccessInfo};
export type LocalAccessSectionInfo = {[ref: string]: AccessSectionInfo};
export type ProjectAccessGroups = {[uuid: string]: GroupInfo};

/**
 * The AccessSectionInfo describes the access rights that are assigned on a ref.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-access.html#access-section-info
 */
export interface AccessSectionInfo {
  permissions: AccessPermissionsMap;
}

export type AccessPermissionsMap = {[permissionName: string]: PermissionInfo};

/**
 * The PermissionInfo entity contains information about an assigned permission
 * https://gerrit-review.googlesource.com/Documentation/rest-api-access.html#permission-info
 */
export interface PermissionInfo {
  label?: string; // The name of the label. Not set if it’s not a label permission.
  exclusive?: boolean;
  rules: PermissionInfoRules;
}

export type PermissionInfoRules = {[groupUUID: string]: PermissionRuleInfo};

/**
 * The PermissionRuleInfo entity contains information about a permission rule that is assigned to group
 * https://gerrit-review.googlesource.com/Documentation/rest-api-access.html#permission-info
 */
export interface PermissionRuleInfo {
  action: PermissionAction;
  force?: boolean;
  min?: number; // not set if range is empty (from 0 to 0) or not set
  max?: number; // not set if range is empty (from 0 to 0) or not set
}

/**
 * The DashboardInfo entity contains information about a project dashboard
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#dashboard-info
 */
export interface DashboardInfo {
  id: DashboardId;
  project: RepoName;
  defining_project: RepoName;
  ref: string; // The name of the ref in which the dashboard is defined, without the refs/meta/dashboards/ prefix
  path: string;
  description?: string;
  foreach?: string;
  url: string;
  is_default?: boolean;
  title?: string;
  sections: DashboardSectionInfo[];
}

/**
 * The DashboardSectionInfo entity contains information about a section in a dashboard.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#dashboard-section-info
 */
export interface DashboardSectionInfo {
  name: string;
  query: string;
}

/**
 * The ConfigInput entity describes a new project configuration
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#config-input
 */
export interface ConfigInput {
  description?: string;
  use_contributor_agreements?: InheritedBooleanInfoConfiguredValue;
  use_content_merge?: InheritedBooleanInfoConfiguredValue;
  use_signed_off_by?: InheritedBooleanInfoConfiguredValue;
  create_new_change_for_all_not_in_target?: InheritedBooleanInfoConfiguredValue;
  require_change_id?: InheritedBooleanInfoConfiguredValue;
  enable_signed_push?: InheritedBooleanInfoConfiguredValue;
  require_signed_push?: InheritedBooleanInfoConfiguredValue;
  private_by_default?: InheritedBooleanInfoConfiguredValue;
  work_in_progress_by_default?: InheritedBooleanInfoConfiguredValue;
  enable_reviewer_by_email?: InheritedBooleanInfoConfiguredValue;
  match_author_to_committer_date?: InheritedBooleanInfoConfiguredValue;
  reject_implicit_merges?: InheritedBooleanInfoConfiguredValue;
  reject_empty_commit?: InheritedBooleanInfoConfiguredValue;
  max_object_size_limit?: MaxObjectSizeLimitInfo;
  submit_type?: SubmitType;
  state?: ProjectState;
  plugin_config_values?: PluginNameToPluginParametersMap;
  commentlinks?: ConfigInfoCommentLinks;
}

export type ConfigInfoCommentLinks = {
  [commentLinkName: string]: CommentLinkInfo;
};

/**
 * The ProjectInput entity contains information for the creation of a new project
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#project-input
 */
export interface ProjectInput {
  name?: RepoName;
  parent?: RepoName;
  description?: string;
  permissions_only?: boolean;
  create_empty_commit?: boolean;
  submit_type?: SubmitType;
  branches?: BranchName[];
  owners?: GroupId[];
  use_contributor_agreements?: InheritedBooleanInfoConfiguredValue;
  use_signed_off_by?: InheritedBooleanInfoConfiguredValue;
  create_new_change_for_all_not_in_target?: InheritedBooleanInfoConfiguredValue;
  use_content_merge?: InheritedBooleanInfoConfiguredValue;
  require_change_id?: InheritedBooleanInfoConfiguredValue;
  enable_signed_push?: InheritedBooleanInfoConfiguredValue;
  require_signed_push?: InheritedBooleanInfoConfiguredValue;
  max_object_size_limit?: string;
  reject_empty_commit?: InheritedBooleanInfoConfiguredValue;
}

/**
 * The BranchInfo entity contains information about a branch
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#branch-info
 */
export interface BranchInfo {
  ref: GitRef;
  revision: string;
  can_delete?: boolean;
  web_links?: WebLinkInfo[];
}

/**
 * The ProjectAccessInput describes changes that should be applied to a project access config
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#project-access-input
 */
export interface ProjectAccessInput {
  remove?: RefToProjectAccessInfoMap;
  add?: RefToProjectAccessInfoMap;
  message?: string;
  parent?: string;
}

export type RefToProjectAccessInfoMap = {[refName: string]: ProjectAccessInfo};

/**
 * Represent a file in a base64 encoding
 */
export interface Base64File {
  body: string;
  type: string | null;
}

/**
 * Represent a file in a base64 encoding; GrRestApiInterface returns it from some
 * methods
 */
export interface Base64FileContent {
  content: string | null;
  type: string | null;
  ok: true;
}

/**
 * The WatchedProjectsInfo entity contains information about a project watch for a user
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#project-watch-info
 */
export interface ProjectWatchInfo {
  project: RepoName;
  filter?: string;
  notify_new_changes?: boolean;
  notify_new_patch_sets?: boolean;
  notify_all_comments?: boolean;
  notify_submitted_changes?: boolean;
  notify_abandoned_changes?: boolean;
  _is_local?: boolean; // Added manually
}
/**
 * The DeleteDraftCommentsInput entity contains information specifying a set of draft comments that should be deleted
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#delete-draft-comments-input
 */
export interface DeleteDraftCommentsInput {
  query: string;
}

/**
 * The SshKeyInfo entity contains information about an SSH key of a user
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#ssh-key-info
 */
export interface SshKeyInfo {
  seq: number;
  ssh_public_key: string;
  encoded_key: string;
  algorithm: string;
  comment?: string;
  valid: boolean;
}

/**
 * The HashtagsInput entity contains information about hashtags to add to, and/or remove from, a change
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#hashtags-input
 */
export interface HashtagsInput {
  add?: Hashtag[];
  remove?: Hashtag[];
}

/**
 * Defines a patch ranges. Used as input for gr-rest-api methods,
 * doesn't exist in Rest API
 */
export interface PatchRange {
  patchNum: RevisionPatchSetNum;
  basePatchNum: BasePatchSetNum;
}

/**
 * The CommentInput entity contains information for creating an inline comment
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-input
 */
export interface CommentInput {
  id?: UrlEncodedCommentId;
  path?: string;
  side?: CommentSide;
  line?: number;
  range?: CommentRange;
  in_reply_to?: UrlEncodedCommentId;
  updated?: Timestamp;
  message?: string;
  tag?: string;
  unresolved?: boolean;
}

/**
 * The EditPreferencesInfo entity contains information about the edit preferences of a user
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#edit-preferences-info
 */
export interface EditPreferencesInfo {
  tab_size: number;
  line_length: number;
  indent_unit: number;
  cursor_blink_rate: number;
  hide_top_menu?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  syntax_highlighting?: boolean;
  hide_line_numbers?: boolean;
  match_brackets?: boolean;
  line_wrapping?: boolean;
  indent_with_tabs?: boolean;
  auto_close_brackets?: boolean;
  show_base?: boolean;
  // TODO(TS): the following proeprties doesn't exist in RestAPI doc
  key_map_type?: string;
  theme?: string;
}

/**
 * The PreferencesInput entity contains information for setting the user preferences. Fields which are not set will not be updated
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#preferences-input
 *
 * Note: the doc missed several properties. Java code uses the same class (GeneralPreferencesInfo)
 * both for input data and for response data.
 */
export type PreferencesInput = Partial<PreferencesInfo>;

/**
 * The DiffPreferencesInput entity contains information for setting the diff preferences of a user. Fields which are not set will not be updated
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#diff-preferences-input
 */
export interface DiffPreferenceInput {
  context?: number;
  expand_all_comments?: boolean;
  ignore_whitespace: IgnoreWhitespaceType;
  line_length?: number;
  manual_review?: boolean;
  retain_header?: boolean;
  show_line_endings?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  skip_deleted?: boolean;
  syntax_highlighting?: boolean;
  hide_top_menu?: boolean;
  hide_line_numbers?: boolean;
  tab_size?: number;
  font_size?: number;
  line_wrapping?: boolean;
  indent_with_tabs?: boolean;
}

/**
 * The EmailInfo entity contains information about an email address of a user
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#email-info
 */
export interface EmailInfo {
  email: string;
  preferred?: boolean;
  pending_confirmation?: boolean;
}

/**
 * The CapabilityInfo entity contains information about the global capabilities of a user
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#capability-info
 */
export interface AccountCapabilityInfo {
  accessDatabase?: boolean;
  administrateServer?: boolean;
  createAccount?: boolean;
  createGroup?: boolean;
  createProject?: boolean;
  emailReviewers?: boolean;
  flushCaches?: boolean;
  killTask?: boolean;
  maintainServer?: boolean;
  priority?: UserPriority;
  queryLimit?: QueryLimitInfo;
  runAs?: boolean;
  runGC?: boolean;
  streamEvents?: boolean;
  viewAllAccounts?: boolean;
  viewCaches?: boolean;
  viewConnections?: boolean;
  viewPlugins?: boolean;
  viewQueue?: boolean;
}

/**
 * The QueryLimitInfo entity contains information about the Query Limit of a user
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#query-limit-info
 */
export interface QueryLimitInfo {
  min: number;
  max: number;
}

/**
 * The PreferencesInfo entity contains information about a user’s preferences
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#preferences-info
 */
export interface PreferencesInfo {
  changes_per_page: 10 | 25 | 50 | 100;
  theme: AppTheme;
  expand_inline_diffs?: boolean;
  download_scheme?: string;
  date_format: DateFormat;
  time_format: TimeFormat;
  relative_date_in_change_table?: boolean;
  diff_view: DiffViewMode;
  size_bar_in_change_table?: boolean;
  legacycid_in_change_table?: boolean;
  mute_common_path_prefixes?: boolean;
  signed_off_by?: boolean;
  my: TopMenuItemInfo[];
  change_table: string[];
  email_strategy: EmailStrategy;
  default_base_for_merges: DefaultBase;
  publish_comments_on_push?: boolean;
  disable_keyboard_shortcuts?: boolean;
  disable_token_highlighting?: boolean;
  work_in_progress_by_default?: boolean;
  // The email_format doesn't mentioned in doc, but exists in Java class GeneralPreferencesInfo
  email_format?: EmailFormat;
}

/**
 * Contains information about diff images
 * There is no RestAPI interface for it
 */
export interface ImagesForDiff {
  baseImage: Base64ImageFile | null;
  revisionImage: Base64ImageFile | null;
}

/**
 * Contains information about diff image
 * There is no RestAPI interface for it
 */
export interface Base64ImageFile extends Base64File {
  _expectedType: string;
  _name: string;
}

/**
 * The ReviewInput entity contains information for adding a review to a revision
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input
 */
export interface ReviewInput {
  message?: string;
  tag?: ReviewInputTag;
  labels?: LabelNameToValueMap;
  comments?: PathToCommentsInputMap;
  robot_comments?: PathToRobotCommentsMap;
  drafts?: DraftsAction;
  notify?: NotifyType;
  notify_details?: RecipientTypeToNotifyInfoMap;
  omit_duplicate_comments?: boolean;
  on_behalf_of?: AccountId;
  reviewers?: ReviewerInput[];
  ready?: boolean;
  work_in_progress?: boolean;
  add_to_attention_set?: AttentionSetInput[];
  remove_from_attention_set?: AttentionSetInput[];
  ignore_automatic_attention_set_rules?: boolean;
}

/**
 * The ReviewResult entity contains information regarding the updates that were
 * made to a review.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-result
 */
export interface ReviewResult {
  labels?: unknown;
  // type of key is (AccountId | GroupId | EmailAddress)
  reviewers?: {[key: string]: AddReviewerResult};
  ready?: boolean;
}

/**
 * The AddReviewerResult entity describes the result of adding a reviewer to a
 * change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#reviewer-result
 * TODO(paiking): update this to ReviewerResult while considering removals.
 */
export interface AddReviewerResult {
  input: AccountId | GroupId | EmailAddress;
  reviewers?: AccountInfo[];
  ccs?: AccountInfo[];
  error?: string;
  confirm?: boolean;
}

export type LabelNameToValueMap = {[labelName: string]: number};
export type PathToCommentsInputMap = {[path: string]: CommentInput[]};
export type PathToRobotCommentsMap = {[path: string]: RobotCommentInput[]};
export type RecipientTypeToNotifyInfoMap = {
  [recepientType: string]: NotifyInfo;
};

/**
 * The RobotCommentInput entity contains information for creating an inline robot comment
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#robot-comment-input
 */
export type RobotCommentInput = RobotCommentInfo;

/**
 * This is what human, robot and draft comments can agree upon.
 *
 * Human, robot and saved draft comments all have a required id, but unsaved
 * drafts do not. That is why the id is omitted from CommentInfo, such that it
 * can be optional in Draft, but required in CommentInfo and RobotCommentInfo.
 */
export interface CommentBasics extends Omit<CommentInfo, 'id' | 'updated'> {
  id?: UrlEncodedCommentId;
  updated?: Timestamp;
}

/**
 * The RobotCommentInfo entity contains information about a robot inline comment
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#robot-comment-info
 */
export interface RobotCommentInfo extends CommentInfo {
  robot_id: RobotId;
  robot_run_id: RobotRunId;
  url?: string;
  properties: {[propertyName: string]: string};
  fix_suggestions: FixSuggestionInfo[];
}
export type PathToRobotCommentsInfoMap = {[path: string]: RobotCommentInfo[]};

/**
 * The FixSuggestionInfo entity represents a suggested fix
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#fix-suggestion-info
 */
export interface FixSuggestionInfoInput {
  description: string;
  replacements: FixReplacementInfo[];
}

export interface FixSuggestionInfo extends FixSuggestionInfoInput {
  fix_id: FixId;
  description: string;
  replacements: FixReplacementInfo[];
}

/**
 * The FixReplacementInfo entity describes how the content of a file should be replaced by another content
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#fix-replacement-info
 */
export interface FixReplacementInfo {
  path: string;
  range: CommentRange;
  replacement: string;
}

/**
 * The NotifyInfo entity contains detailed information about who should be notified about an update
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#notify-info
 */
export interface NotifyInfo {
  accounts?: AccountId[];
}

/**
 * The ReviewerInput entity contains information for adding a reviewer to a change
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#reviewer-input
 */
export interface ReviewerInput {
  reviewer: AccountId | GroupId | EmailAddress;
  state?: ReviewerState;
  confirmed?: boolean;
  notify?: NotifyType;
  notify_details?: RecipientTypeToNotifyInfoMap;
}

/**
 * The AttentionSetInput entity contains details for adding users to the attention set and removing them from it
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#attention-set-input
 */
export interface AttentionSetInput {
  user: AccountId;
  reason: string;
  notify?: NotifyType;
  notify_details?: RecipientTypeToNotifyInfoMap;
}

/**
 * The EditInfo entity contains information about a change edit
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#edit-info
 */
export interface EditInfo {
  commit: CommitInfo;
  base_patch_set_number: BasePatchSetNum;
  base_revision: string;
  ref: GitRef;
  fetch?: ProtocolToFetchInfoMap;
  files?: FileNameToFileInfoMap;
}

export type ProtocolToFetchInfoMap = {[protocol: string]: FetchInfo};
export type FileNameToFileInfoMap = {[name: string]: FileInfo};

/**
 * Contains information about an account that can be added to a change
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#suggested-reviewer-info
 */
export interface SuggestedReviewerAccountInfo {
  account: AccountInfo;
  /**
   * The total number of accounts in the suggestion - always 1
   */
  count: 1;
}

/**
 * Contains information about a group that can be added to a change
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#suggested-reviewer-info
 */
export interface SuggestedReviewerGroupInfo {
  group: GroupBaseInfo;
  /**
   * The total number of accounts that are members of the group is returned
   * (this count includes members of nested groups)
   */
  count: number;
  /**
   * True if group is present and count is above the threshold where the
   * confirmed flag must be passed to add the group as a reviewer
   */
  confirm?: boolean;
}

/**
 * The SuggestedReviewerInfo entity contains information about a reviewer that can be added to a change (an account or a group)
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#suggested-reviewer-info
 */
export type SuggestedReviewerInfo =
  | SuggestedReviewerAccountInfo
  | SuggestedReviewerGroupInfo;

export type Suggestion = SuggestedReviewerInfo | AccountInfo;

export function isReviewerAccountSuggestion(
  s: Suggestion
): s is SuggestedReviewerAccountInfo {
  return (s as SuggestedReviewerAccountInfo).account !== undefined;
}

export function isReviewerGroupSuggestion(
  s: Suggestion
): s is SuggestedReviewerGroupInfo {
  return (s as SuggestedReviewerGroupInfo).group !== undefined;
}

export type RequestPayload = string | object;

export type Password = string;

/**
 * The BranchInput entity contains information for the creation of a new branch
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#branch-input
 */
export interface BranchInput {
  ref?: BranchName; // refs/heads prefix is allowed, but can be omitted
  revision?: string;
}

/**
 * The TagInput entity contains information for creating a tag
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#tag-input
 */
export interface TagInput {
  // ref: string; mentoined as required in doc, but it doesn't used anywher
  revision?: string;
  message?: string;
}

/**
 * The IncludedInInfo entity contains information about the branches a change was merged into and tags it was tagged with
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#included-in-info
 */
export interface IncludedInInfo {
  branches: BranchName[];
  tags: TagName[];
  external?: NameToExternalSystemsMap;
}

// It is unclear what is name here
export type NameToExternalSystemsMap = {[name: string]: string[]};

/**
 * The PluginInfo entity describes a plugin.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-plugins.html#plugin-info
 */
export interface PluginInfo {
  id: string;
  version: string;
  api_version?: string;
  index_url?: string;
  filename?: string;
  disabled: boolean;
}
/**
 * The PluginInput entity describes a plugin that should be installed.
 */
export interface PluginInput {
  url: string;
}

/**
 * The DocResult entity contains information about a document.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-documentation.html#doc-result
 */
export interface DocResult {
  title: string;
  url: string;
}

/**
 * The TagInfo entity contains information about a tag.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#tag-info
 **/
export interface TagInfo {
  ref: GitRef;
  revision: string;
  object?: string;
  message?: string;
  tagger?: GitPersonInfo;
  created?: string;
  can_delete: boolean;
  web_links?: WebLinkInfo[];
}

/**
 * The RelatedChangesInfo entity contains information about related changes.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#related-changes-info
 */
export interface RelatedChangesInfo {
  changes: RelatedChangeAndCommitInfo[];
}

/**
 * The RelatedChangeAndCommitInfo entity contains information about a related change and commit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#related-change-and-commit-info
 */
export interface RelatedChangeAndCommitInfo {
  project: RepoName;
  change_id?: ChangeId;
  commit: CommitInfoWithRequiredCommit;
  _change_number?: NumericChangeId;
  _revision_number?: number;
  _current_revision_number?: number;
  status?: ChangeStatus;
  // The submittable property doesn't exist in the Gerrit API, but in the future
  // we can bring this feature back. There is a frontend code and CSS styles for
  // it and this property is added here to keep related frontend code unchanged.
  submittable?: boolean;
}

/**
 * The SubmittedTogetherInfo entity contains information about a collection of changes that would be submitted together.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#submitted-together-info
 */
export interface SubmittedTogetherInfo {
  changes: ChangeInfo[];
  non_visible_changes: number;
}

/**
 * The RevertSubmissionInfo entity describes the revert changes.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revert-submission-info
 */
export interface RevertSubmissionInfo {
  revert_changes: ChangeInfo[];
}

/**
 * The CherryPickInput entity contains information for cherry-picking a change to a new branch.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#cherrypick-input
 */
export interface CherryPickInput {
  message?: string;
  destination: BranchName;
  base?: CommitId;
  parent?: number;
  notify?: NotifyType;
  notify_details: RecipientTypeToNotifyInfoMap;
  keep_reviewers?: boolean;
  allow_conflicts?: boolean;
  topic?: TopicName;
  allow_empty?: boolean;
}

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#mergeable-info
 */
export interface MergeableInfo {
  submit_type: SubmitType;
  strategy?: MergeStrategy;
  mergeable: boolean;
  commit_merged?: boolean;
  content_merged?: boolean;
  conflicts?: string[];
  mergeable_into?: string[];
}
