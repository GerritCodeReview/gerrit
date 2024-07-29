/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ChangeStatus,
  RepoState,
  SubmitType,
  InheritedBooleanInfoConfiguredValue,
  PermissionAction,
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
  CommentInfo,
  CommentLinkInfo,
  CommentLinks,
  CommentSide,
  CommitId,
  CommitInfo,
  ConfigArrayParameterInfo,
  ConfigInfo,
  ConfigListParameterInfo,
  ConfigParameterInfo,
  ConfigParameterInfoBase,
  ContextLine,
  ContributorAgreementInfo,
  DetailedLabelInfo,
  DownloadInfo,
  DownloadSchemeInfo,
  EDIT,
  EditPatchSet,
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
  PARENT,
  PatchSetNum,
  PatchSetNumber,
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
  RevisionPatchSetNum,
  SchemesInfoMap,
  ServerInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  TopicName,
  UrlEncodedCommentId,
  UrlEncodedRepoName,
  UserConfigInfo,
  VotingRangeInfo,
  WebLinkInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  Base64FileContent,
  CommentRange,
  FixReplacementInfo,
  FixSuggestionInfo,
  FixId,
} from '../api/rest-api';
import {DiffInfo, IgnoreWhitespaceType} from './diff';
import {PatchRange, LineNumber} from '../api/diff';

export type {
  AccountId,
  AccountDetailInfo,
  AccountInfo,
  AccountsConfigInfo,
  ActionInfo,
  ActionNameToActionInfoMap,
  ApprovalInfo,
  AuthInfo,
  AvatarInfo,
  Base64FileContent,
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
  CommentInfo,
  CommentLinks,
  CommentRange,
  CommitId,
  CommitInfo,
  ConfigArrayParameterInfo,
  ConfigInfo,
  ConfigListParameterInfo,
  ConfigParameterInfo,
  ConfigParameterInfoBase,
  ContextLine,
  ContributorAgreementInfo,
  DetailedLabelInfo,
  DownloadInfo,
  DownloadSchemeInfo,
  EditPatchSet,
  EmailAddress,
  FileInfo,
  FixId,
  FixSuggestionInfo,
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
  PatchRange,
  PatchSetNum,
  PatchSetNumber,
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
  RevisionPatchSetNum,
  SchemesInfoMap,
  ServerInfo,
  SubmitTypeInfo,
  SuggestInfo,
  Timestamp,
  TopicName,
  UrlEncodedCommentId,
  UrlEncodedRepoName,
  UserConfigInfo,
  VotingRangeInfo,
  WebLinkInfo,
};
export {EDIT, PARENT, isDetailedLabelInfo, isQuickLabelInfo};

/*
 * In T, make a set of properties whose keys are in the union K required
 */
export type RequireProperties<T, K extends keyof T> = Omit<T, K> &
  Required<Pick<T, K>>;

export type PropertyType<T, K extends keyof T> = ReturnType<() => T[K]>;

/**
 * Type alias for parsed json object to make code cleaner
 */
export type ParsedJSON = BrandType<unknown, '_parsedJSON'>;

export type RobotId = BrandType<string, '_robotId'>;

export type RobotRunId = BrandType<string, '_robotRunId'>;

// RevisionId '0' is the same as 'current'. However, we want to avoid '0'
// in our code, so it is not added here as a possible value.
export type RevisionId = 'current' | CommitId | PatchSetNum;

// The ID of the dashboard, in the form of '<ref>:<path>'
export type DashboardId = BrandType<string, '_dahsboardId'>;

// The refs/tags/ prefix is omitted in Tag name
export type TagName = BrandType<string, '_tagName'>;

export type LabelName = BrandType<string, '_labelName'>;

// The Encoded UUID of the group
export type EncodedGroupId = BrandType<string, '_encodedGroupId'>;

export type UserId = AccountId | GroupId | EmailAddress;

export type DiffPageSidebar = 'NONE' | `plugin-${string}`;

// Must be kept in sync with the ListChangesOption enum.
// See: java/com/google/gerrit/extensions/client/ListChangesOption.java
export const ListChangesOption = {
  LABELS: 0,
  DETAILED_LABELS: 8,

  // Return information on the current patch set of the change.
  CURRENT_REVISION: 1,
  ALL_REVISIONS: 2,

  // If revisions are included, parse the commit object.
  CURRENT_COMMIT: 3,
  ALL_COMMITS: 4,

  // If a patch set is included, include the files of the patch set.
  CURRENT_FILES: 5,
  ALL_FILES: 6,

  // If accounts are included, include detailed account info.
  DETAILED_ACCOUNTS: 7,

  // Include messages associated with the change.
  MESSAGES: 9,

  // Include allowed actions client could perform.
  CURRENT_ACTIONS: 10,

  // Set the reviewed boolean for the caller.
  REVIEWED: 11,

  // Include download commands for the caller.
  DOWNLOAD_COMMANDS: 13,

  // Include patch set weblinks.
  WEB_LINKS: 14,

  // Include consistency check results.
  CHECK: 15,

  // Include allowed change actions client could perform.
  CHANGE_ACTIONS: 16,

  // Include a copy of commit messages including review footers.
  COMMIT_FOOTERS: 17,

  // Include push certificate information along with any patch sets.
  PUSH_CERTIFICATES: 18,

  // Include change's reviewer updates.
  REVIEWER_UPDATES: 19,

  // Set the submittable boolean.
  SUBMITTABLE: 20,

  // If tracking ids are included, include detailed tracking ids info.
  TRACKING_IDS: 21,

  // Skip mergeability data.
  SKIP_MERGEABLE: 22,

  // Skip diffstat computation that compute the insertions field (number of lines inserted) and
  // deletions field (number of lines deleted)
  SKIP_DIFFSTAT: 23,

  // Include the evaluated submit requirements for the caller.
  SUBMIT_REQUIREMENTS: 24,

  // Include custom keyed values.
  CUSTOM_KEYED_VALUES: 25,

  // Include the 'starred' field, that is if the change is starred by the
  // current user.
  STAR: 26,

  // Include the `parents_data` field in each revision, e.g. if it's merged in the target branch and
  // whether it points to a patch-set of another change.
  PARENTS: 27,
};

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

export interface DropdownLink {
  url?: string;
  name?: string;
  external?: boolean;
  target?: string | null;
  download?: boolean;
  id?: string;
  tooltip?: string;
}

/**
 * New options for a group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupOptionsInput {
  visible_to_all: boolean;
}

export interface CommitInfoWithRequiredCommit extends CommitInfo {
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
 * The CapabilityInfo entity contains information about a capability.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#capability-info
 */
export interface CapabilityInfo {
  id: string;
  name: string;
}

export type CapabilityInfoMap = {[id: string]: CapabilityInfo};

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

export enum ChangeStates {
  ABANDONED = 'Abandoned',
  ACTIVE = 'Active',
  MERGE_CONFLICT = 'Merge Conflict',
  GIT_CONFLICT = 'Git Conflict',
  MERGED = 'Merged',
  PRIVATE = 'Private',
  READY_TO_SUBMIT = 'Ready to submit',
  /** This change is a revert of another change. */
  REVERT = 'Revert',
  /** A revert of this change was created. */
  REVERT_CREATED = 'Revert Created',
  /** A revert of this change was submitted. */
  REVERT_SUBMITTED = 'Revert Submitted',
  WIP = 'WIP',
}

export enum SavingState {
  /**
   * Currently not saving. Not yet saved or last saving attempt successful.
   */
  // Possible prior states: SAVING
  // Possible subsequent states: SAVING
  OK = 'OK',
  /**
   * Currently saving to the backend.
   */
  // Possible prior states: OK, ERROR
  // Possible subsequent states: OK, ERROR
  SAVING = 'SAVING',
  /**
   * Latest saving attempt failed with an error.
   */
  // Possible prior states: SAVING
  // Possible subsequent states: SAVING
  ERROR = 'ERROR',
}

export type DraftInfo = Omit<CommentInfo, 'id' | 'updated'> & {
  // Must be set for all drafts.
  // Drafts received from the backend will be modified immediately with
  // `state: OK` before allowing them to get into the model.
  savingState: SavingState;
  // Must be set for new drafts created in this session.
  // Use the id() utility function for uniquely identifying drafts.
  client_id?: UrlEncodedCommentId;
  // Must be set for new drafts created in this session.
  // Timestamp in milliseconds (Date.now()) of when this draft was created in
  // this session. Allows stable sorting of new comments on the same range.
  client_created_ms?: number;
  // Must be set for drafts known to the backend.
  // Use the id() utility function for uniquely identifying drafts.
  id?: UrlEncodedCommentId;
  // Set, iff `id` is set. Reflects the time when the draft was last saved to
  // the backend.
  updated?: Timestamp;
};

export interface NewDraftInfo extends DraftInfo {
  client_id: UrlEncodedCommentId;
  client_created_ms: number;
  id: undefined;
  updated: undefined;
}

/**
 * This is what human, robot and draft comments can agree upon.
 *
 * Note that `id` and `updated` must be considered optional, because we might
 * be dealing with unsaved draft comments.
 */
export type Comment = DraftInfo | CommentInfo | RobotCommentInfo;

// TODO: Replace the CommentMap type with just an array of paths.
export type CommentMap = {[path: string]: boolean};

export function isRobot<T extends Comment>(
  x: T | RobotCommentInfo | undefined
): x is RobotCommentInfo {
  return !!x && !!(x as RobotCommentInfo).robot_id;
}

export function isDraft<T extends Comment>(
  x: T | DraftInfo | undefined
): x is DraftInfo {
  return !!x && (x as DraftInfo).savingState !== undefined;
}

export function isSaving<T extends Comment>(
  x: T | DraftInfo | undefined
): boolean {
  return !!x && (x as DraftInfo).savingState === SavingState.SAVING;
}

export function isError<T extends Comment>(
  x: T | DraftInfo | undefined
): boolean {
  return !!x && (x as DraftInfo).savingState === SavingState.ERROR;
}

/**
 * A new draft comment is a comment that was created by the user in this session
 * and has not yet been saved to the backend. Such a comment must have a
 * `client_id`, but it must not have an `id`.
 */
export function isNew<T extends Comment>(
  x: T | DraftInfo | undefined
): x is NewDraftInfo {
  return !!x && !!(x as DraftInfo).client_id && !(x as DraftInfo).id;
}

export interface CommentThread {
  /**
   * This can only contain at most one draft. And if so, then it is the last
   * comment in this list. This must not contain unsaved drafts.
   */
  comments: Array<Comment>;
  /**
   * Identical to the id of the first comment. If this is undefined, then the
   * thread only contains an unsaved draft.
   */
  rootId?: UrlEncodedCommentId;
  /**
   * Note that all location information is typically identical to that of the
   * first comment, but not for ported comments!
   */
  path: string;
  commentSide: CommentSide;
  /* mergeParentNum is the merge parent number only valid for merge commits
     when commentSide is PARENT.
     mergeParentNum is undefined for auto merge commits
     Same as `parent` in CommentInfo.
  */
  mergeParentNum?: number;
  patchNum?: RevisionPatchSetNum;
  /* Different from CommentInfo, which just keeps the line undefined for
     FILE comments. */
  line?: LineNumber;
  range?: CommentRange;
  /**
   * Was the thread ported over from its original location to a newer patchset?
   * If yes, then the location information above contains the ported location,
   * but the comments still have the original location set.
   */
  ported?: boolean;
  /**
   * Only relevant when ported:true. Means that no ported range could be
   * computed. `line` and `range` can be undefined then.
   */
  rangeInfoLost?: boolean;
}

export type CommentIdToCommentThreadMap = {
  [urlEncodedCommentId: string]: CommentThread;
};

export interface ChangeMessage extends ChangeMessageInfo {
  // TODO(TS): maybe should be an enum instead
  type: string;
  expanded: boolean;
  commentThreads: CommentThread[];
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
  type: string;
  _name?: string;
  _expectedType?: string;
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
  require_change_for_config_update?: boolean;
  groups: RepoAccessGroups;
  config_web_links: WebLinkInfo[];
}

export type RepoAccessInfoMap = {[projectName: string]: ProjectAccessInfo};
export type LocalAccessSectionInfo = {[ref: string]: AccessSectionInfo};
export type RepoAccessGroups = {[uuid: string]: GroupInfo};

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
  state?: RepoState;
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
  remove?: RefToRepoAccessInfoMap;
  add?: RefToRepoAccessInfoMap;
  message?: string;
  parent?: string;
}

export type RefToRepoAccessInfoMap = {[refName: string]: ProjectAccessInfo};

/**
 * Represent a file in a base64 encoding
 */
export interface Base64File {
  body: string;
  type: string | null;
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
  fix_suggestions?: FixSuggestionInfo[];
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
  viewSecondaryEmails?: boolean;
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
  // Do not use directly, but use changeTablePrefs() in user model to map/filter legacy columns.
  change_table: string[];
  email_strategy: EmailStrategy;
  default_base_for_merges: DefaultBase;
  publish_comments_on_push?: boolean;
  disable_keyboard_shortcuts?: boolean;
  disable_token_highlighting?: boolean;
  work_in_progress_by_default?: boolean;
  // The email_format doesn't mentioned in doc, but exists in Java class GeneralPreferencesInfo
  email_format?: EmailFormat;
  allow_browser_notifications?: boolean;
  allow_suggest_code_while_commenting?: boolean;
  allow_autocompleting_comments?: boolean;
  diff_page_sidebar?: DiffPageSidebar;
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
  response_format_options?: string[];
}

/**
 * The ReviewResult entity contains information regarding the updates that were
 * made to a review.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-result
 */
export interface ReviewResult {
  labels?: unknown;
  reviewers?: {[key: UserId]: AddReviewerResult};
  ready?: boolean;
  change_info?: ChangeInfo;
}

/**
 * The AddReviewerResult entity describes the result of adding a reviewer to a
 * change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#reviewer-result
 * TODO(paiking): update this to ReviewerResult while considering removals.
 */
export interface AddReviewerResult {
  input: UserId;
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
 * The RobotCommentInfo entity contains information about a robot inline comment
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#robot-comment-info
 */
export interface RobotCommentInfo extends CommentInfo {
  robot_id: RobotId;
  robot_run_id: RobotRunId;
  url?: string;
  properties: {[propertyName: string]: string};
}
export type PathToRobotCommentsInfoMap = {[path: string]: RobotCommentInfo[]};

/**
 * The ApplyProvidedFixInput entity contains information for applying fixes, provided in the
 * request body, to a revision.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#apply-provided-fix
 */
export interface ApplyProvidedFixInput {
  fix_replacement_infos: FixReplacementInfo[];
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
  reviewer: UserId;
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
  user: UserId;
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
export declare interface RelatedChangesInfo {
  changes: RelatedChangeAndCommitInfo[];
}

/**
 * The RelatedChangeAndCommitInfo entity contains information about a related change and commit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#related-change-and-commit-info
 */
export declare interface RelatedChangeAndCommitInfo {
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

export interface ChangeActionDialog extends HTMLElement {
  resetFocus?(): void;
  init?(): void;
}
