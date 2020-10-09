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
  ChangeStatus,
  DefaultDisplayNameConfig,
  FileInfoStatus,
  GpgKeyInfoStatus,
  ProblemInfoStatus,
  ProjectState,
  RequirementStatus,
  ReviewerState,
  RevisionKind,
  SubmitType,
  InheritedBooleanInfoConfiguredValue,
  ConfigParameterInfoType,
  AccountTag,
  PermissionAction,
  HttpMethod,
  CommentSide,
  AppTheme,
  DateFormat,
  TimeFormat,
  EmailStrategy,
  DefaultBase,
  IgnoreWhitespaceType,
  UserPriority,
  DiffViewMode,
  DraftsAction,
  NotifyType,
  EmailFormat,
} from '../constants/constants';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';

export type BrandType<T, BrandName extends string> = T &
  {[__brand in BrandName]: never};

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

export type PatchSetNum = BrandType<'PARENT' | 'edit' | number, '_patchSet'>;

export const EditPatchSetNum = 'edit' as PatchSetNum;
// TODO(TS): This is not correct, it is better to have a separate ApiPatchSetNum
// without 'parent'.
export const ParentPatchSetNum = 'PARENT' as PatchSetNum;

export type ChangeId = BrandType<string, '_changeId'>;
export type ChangeMessageId = BrandType<string, '_changeMessageId'>;
export type NumericChangeId = BrandType<number, '_numericChangeId'>;
export type RepoName = BrandType<string, '_repoName'>;
export type UrlEncodedRepoName = BrandType<string, '_urlEncodedRepoName'>;
export type TopicName = BrandType<string, '_topicName'>;
// TODO(TS): Probably, we should separate AccountId and EncodedAccountId
export type AccountId = BrandType<number, '_accountId'>;
export type GitRef = BrandType<string, '_gitRef'>;
export type RequirementType = BrandType<string, '_requirementType'>;
export type TrackingId = BrandType<string, '_trackingId'>;
export type ReviewInputTag = BrandType<string, '_reviewInputTag'>;
export type RobotId = BrandType<string, '_robotId'>;
export type RobotRunId = BrandType<string, '_robotRunId'>;

// RevisionId '0' is the same as 'current'. However, we want to avoid '0'
// in our code, so it is not added here as a possible value.
export type RevisionId = 'current' | CommitId | PatchSetNum;

// The UUID of the suggested fix.
export type FixId = BrandType<string, '_fixId'>;
export type EmailAddress = BrandType<string, '_emailAddress'>;

// The URL encoded UUID of the comment
export type UrlEncodedCommentId = BrandType<string, '_urlEncodedCommentId'>;

// The ID of the dashboard, in the form of '<ref>:<path>'
export type DashboardId = BrandType<string, '_dahsboardId'>;

// The 8-char hex GPG key ID.
export type GpgKeyId = BrandType<string, '_gpgKeyId'>;

// The 40-char (plus spaces) hex GPG key fingerprint
export type GpgKeyFingerprint = BrandType<string, '_gpgKeyFingerprint'>;

// OpenPGP User IDs (https://tools.ietf.org/html/rfc4880#section-5.11).
export type OpenPgpUserIds = BrandType<string, '_openPgpUserIds'>;

// This ID is equal to the numeric ID of the change that triggered the
// submission. If the change that triggered the submission also has a topic, it
// will be "<id>-<topic>" of the change that triggered the submission
// The callers must not rely on the format of the submission ID.
export type ChangeSubmissionId = BrandType<
  string | number,
  '_changeSubmissionId'
>;

// The refs/heads/ prefix is omitted in Branch name
export type BranchName = BrandType<string, '_branchName'>;

// The refs/tags/ prefix is omitted in Tag name
export type TagName = BrandType<string, '_tagName'>;

// The ID of the change in the format "'<project>~<branch>~<Change-Id>'"
export type ChangeInfoId = BrandType<string, '_changeInfoId'>;
export type Hashtag = BrandType<string, '_hashtag'>;
export type StarLabel = BrandType<string, '_startLabel'>;
export type CommitId = BrandType<string, '_commitId'>;
export type LabelName = BrandType<string, '_labelName'>;
export type GroupName = BrandType<string, '_groupName'>;

// The UUID of the group
export type GroupId = BrandType<string, '_groupId'>;

// The Encoded UUID of the group
export type EncodedGroupId = BrandType<string, '_encodedGroupId'>;

// The timezone offset from UTC in minutes
export type TimezoneOffset = BrandType<number, '_timezoneOffset'>;

// Timestamps are given in UTC and have the format
// "'yyyy-mm-dd hh:mm:ss.fffffffff'"
// where "'ffffffffff'" represents nanoseconds.
export type Timestamp = BrandType<string, '_timestamp'>;

export type IdToAttentionSetMap = {[accountId: string]: AttentionSetInfo};
export type LabelNameToInfoMap = {[labelName: string]: LabelInfo};

// {Verified: ["-1", " 0", "+1"]}
export type LabelNameToValueMap = {[labelName: string]: string[]};

// The map maps the values (“-2”, “-1”, " `0`", “+1”, “+2”) to the value descriptions.
export type LabelValueToDescriptionMap = {[labelValue: string]: string};

/**
 * The LabelInfo entity contains information about a label on a change, always
 * corresponding to the current patch set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#label-info
 */
export type LabelInfo =
  | QuickLabelInfo
  | DetailedLabelInfo
  | (QuickLabelInfo & DetailedLabelInfo);

interface LabelCommonInfo {
  optional?: boolean; // not set if false
}

export interface QuickLabelInfo extends LabelCommonInfo {
  approved?: AccountInfo;
  rejected?: AccountInfo;
  recommended?: AccountInfo;
  disliked?: AccountInfo;
  blocking?: boolean; // not set if false
  value?: number; // The voting value of the user who recommended/disliked this label on the change if it is not “+1”/“-1”.
  default_value?: number;
}

/**
 * LabelInfo when DETAILED_LABELS are requested.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#_fields_set_by_code_detailed_labels_code
 */
export interface DetailedLabelInfo extends LabelCommonInfo {
  // Docs claim that 'all' is optional, but it is actually always set.
  all: ApprovalInfo[];
  // Docs claim that 'values' is optional, but it is actually always set.
  values: LabelValueToDescriptionMap; // A map of all values that are allowed for this label
  default_value?: number;
}

export function isQuickLabelInfo(
  l: LabelInfo
): l is QuickLabelInfo | (QuickLabelInfo & DetailedLabelInfo) {
  const quickLabelInfo = l as QuickLabelInfo;
  return (
    quickLabelInfo.approved !== undefined ||
    quickLabelInfo.rejected !== undefined ||
    quickLabelInfo.recommended !== undefined ||
    quickLabelInfo.disliked !== undefined ||
    quickLabelInfo.blocking !== undefined ||
    quickLabelInfo.blocking !== undefined ||
    quickLabelInfo.value !== undefined
  );
}

export function isDetailedLabelInfo(
  label: LabelInfo
): label is DetailedLabelInfo | (QuickLabelInfo & DetailedLabelInfo) {
  return !!(label as DetailedLabelInfo).values;
}

// https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#contributor-agreement-input
export interface ContributorAgreementInput {
  name?: string;
}

// https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#contributor-agreement-info
export interface ContributorAgreementInfo {
  name: string;
  description: string;
  url: string;
  auto_verify_group?: GroupInfo;
}

/**
 * The ChangeInfo entity contains information about a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
 */
export interface ChangeInfo {
  id: ChangeInfoId;
  project: RepoName;
  branch: BranchName;
  topic?: TopicName;
  attention_set?: IdToAttentionSetMap;
  assignee?: AccountInfo;
  hashtags?: Hashtag[];
  change_id: ChangeId;
  subject: string;
  status: ChangeStatus;
  created: Timestamp;
  updated: Timestamp;
  submitted?: Timestamp;
  submitter: AccountInfo;
  starred?: boolean; // not set if false
  stars?: StarLabel[];
  reviewed?: boolean; // not set if false
  submit_type?: SubmitType;
  mergeable?: boolean;
  submittable?: boolean;
  insertions: number; // Number of inserted lines
  deletions: number; // Number of deleted lines
  total_comment_count?: number;
  unresolved_comment_count?: number;
  // TODO(TS): Use changed_id everywhere in code instead of (legacy) _number
  _number: NumericChangeId;
  owner: AccountInfo;
  actions?: ActionNameToActionInfoMap;
  requirements?: Requirement[];
  labels?: LabelNameToInfoMap;
  permitted_labels?: LabelNameToValueMap;
  removable_reviewers?: AccountInfo[];
  // This is documented as optional, but actually always set.
  reviewers: Reviewers;
  pending_reviewers?: AccountInfo[];
  reviewer_updates?: ReviewerUpdateInfo[];
  messages?: ChangeMessageInfo[];
  current_revision?: CommitId;
  revisions?: {[revisionId: string]: RevisionInfo};
  tracking_ids?: TrackingIdInfo[];
  _more_changes?: boolean; // not set if false
  problems?: ProblemInfo[];
  is_private?: boolean; // not set if false
  work_in_progress?: boolean; // not set if false
  has_review_started?: boolean; // not set if false
  revert_of?: NumericChangeId;
  submission_id?: ChangeSubmissionId;
  cherry_pick_of_change?: NumericChangeId;
  cherry_pick_of_patch_set?: PatchSetNum;
  contains_git_conflicts?: boolean;
  internalHost?: string; // TODO(TS): provide an explanation what is its
}

/**
 * The reviewers as a map that maps a reviewer state to a list of AccountInfo
 * entities. Possible reviewer states are REVIEWER, CC and REMOVED.
 * REVIEWER: Users with at least one non-zero vote on the change.
 * CC: Users that were added to the change, but have not voted.
 * REMOVED: Users that were previously reviewers on the change, but have been removed.
 */
export type Reviewers = Partial<Record<ReviewerState, AccountInfo[]>>;

/**
 * ChangeView request change detail with ALL_REVISIONS option set.
 * The response always contains current_revision and revisions.
 */
export type ChangeViewChangeInfo = RequireProperties<
  ChangeInfo,
  'current_revision' | 'revisions'
>;
/**
 * The AccountInfo entity contains information about an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-info
 */
export interface AccountInfo {
  // Normally _account_id is defined (for known Gerrit users), but users can
  // also be CCed just with their email address. So you have to be prepared that
  // _account_id is undefined, but then email must be set.
  _account_id?: AccountId;
  name?: string;
  display_name?: string;
  // Must be set, if _account_id is undefined.
  email?: EmailAddress;
  secondary_emails?: string[];
  username?: string;
  avatars?: AvatarInfo[];
  _more_accounts?: boolean; // not set if false
  status?: string; // status message of the account
  inactive?: boolean; // not set if false
  tags?: AccountTag[];
}

export function isAccount(x: AccountInfo | GroupInfo): x is AccountInfo {
  const account = x as AccountInfo;
  return account._account_id !== undefined || account.email !== undefined;
}

export function isGroup(x: AccountInfo | GroupInfo): x is GroupInfo {
  return (x as GroupInfo).id !== undefined;
}

/**
 * The AccountDetailInfo entity contains detailed information about an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-detail-info
 */
export interface AccountDetailInfo extends AccountInfo {
  registered_on: Timestamp;
}

/**
 * The AccountExternalIdInfo entity contains information for an external id of
 * an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-external-id-info
 */
export interface AccountExternalIdInfo {
  identity: string;
  email?: string;
  trusted?: boolean;
  can_delete?: boolean;
}

/**
 * The GroupAuditEventInfo entity contains information about an auditevent of a group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupAuditEventInfo {
  member: string;
  type: string;
  user: string;
  date: string;
}

/**
 * The GroupBaseInfo entity contains base information about the group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#group-base-info
 */
export interface GroupBaseInfo {
  id: GroupId;
  name: GroupName;
}

/**
 * The GroupInfo entity contains information about a group. This can be a
 * Gerrit internal group, or an external group that is known to Gerrit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html#group-info
 */
export interface GroupInfo {
  id: GroupId;
  name?: GroupName;
  url?: string;
  options?: GroupOptionsInfo;
  description?: string;
  group_id?: string;
  owner?: string;
  owner_id?: string;
  created_on?: string;
  _more_groups?: boolean;
  members?: AccountInfo[];
  includes?: GroupInfo[];
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
 * Options of the group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupOptionsInfo {
  visible_to_all: boolean;
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

/**
 * The ActionInfo entity describes a REST API call the client canmake to
 * manipulate a resource. These are frequently implemented by plugins and may
 * be discovered at runtime.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#action-info
 */
export interface ActionInfo {
  method?: HttpMethod; // Most actions use POST, PUT or DELETE to cause state changes.
  label?: string; // Short title to display to a user describing the action
  title?: string; // Longer text to display describing the action
  enabled?: boolean; // not set if false
}

export interface ActionNameToActionInfoMap {
  [actionType: string]: ActionInfo | undefined;
  // List of actions explicitly used in code:
  wip?: ActionInfo;
  publishEdit?: ActionInfo;
  rebaseEdit?: ActionInfo;
  deleteEdit?: ActionInfo;
  edit?: ActionInfo;
  stopEdit?: ActionInfo;
}

/**
 * The Requirement entity contains information about a requirement relative to
 * a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#requirement
 */
export interface Requirement {
  status: RequirementStatus;
  fallbackText: string; // A human readable reason
  type: RequirementType;
}

/**
 * The ReviewerUpdateInfo entity contains information about updates to change’s
 * reviewers set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-update-info
 */
export interface ReviewerUpdateInfo {
  updated: Timestamp;
  updated_by: AccountInfo;
  reviewer: AccountInfo;
  state: ReviewerState;
}

/**
 * The ChangeMessageInfo entity contains information about a messageattached
 * to a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-message-info
 */
export interface ChangeMessageInfo {
  id: ChangeMessageId;
  author?: AccountInfo;
  reviewer?: AccountInfo;
  updated_by?: AccountInfo;
  real_author?: AccountInfo;
  date: Timestamp;
  message: string;
  tag?: ReviewInputTag;
  _revision_number?: PatchSetNum;
}

/**
 * The RevisionInfo entity contains information about a patch set.Not all
 * fields are returned by default.  Additional fields can be obtained by
 * adding o parameters as described in Query Changes.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info
 * basePatchNum is present in case RevisionInfo is of type 'edit'
 */
export interface RevisionInfo {
  kind: RevisionKind;
  _number: PatchSetNum;
  created: Timestamp;
  uploader: AccountInfo;
  ref: GitRef;
  fetch?: {[protocol: string]: FetchInfo};
  commit?: CommitInfo;
  files?: {[filename: string]: FileInfo};
  actions?: ActionInfo[];
  reviewed?: boolean;
  commit_with_footers?: boolean;
  push_certificate?: PushCertificateInfo;
  description?: string;
  basePatchNum?: PatchSetNum;
}

/**
 * The TrackingIdInfo entity describes a reference to an external tracking
 * system.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#tracking-id-info
 */
export interface TrackingIdInfo {
  system: string;
  id: TrackingId;
}

/**
 * The ProblemInfo entity contains a description of a potential consistency
 * problem with a change. These are not related to the code review process,
 * but rather indicate some inconsistency in Gerrit’s database or repository
 * metadata related to the enclosing change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#problem-info
 */
export interface ProblemInfo {
  message: string;
  status?: ProblemInfoStatus; // Only set if a fix was attempted
  outcome?: string;
}

/**
 * The AttentionSetInfo entity contains details of users that are in the attention set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#attention-set-info
 */
export interface AttentionSetInfo {
  account: AccountInfo;
  last_update?: Timestamp;
  reason?: string;
}

/**
 * The ApprovalInfo entity contains information about an approval from auser
 * for a label on a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#approval-info
 */
export interface ApprovalInfo extends AccountInfo {
  value?: number;
  permitted_voting_range?: VotingRangeInfo;
  date?: Timestamp;
  tag?: ReviewInputTag;
  post_submit?: boolean; // not set if false
}

/**
 * The AvartarInfo entity contains information about an avatar image ofan
 * account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#avatar-info
 */
export interface AvatarInfo {
  url: string;
  height: number;
  width: number;
}

/**
 * The FetchInfo entity contains information about how to fetch a patchset via
 * a certain protocol.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#fetch-info
 */
export interface FetchInfo {
  url: string;
  ref: string;
  commands?: {[commandName: string]: string};
}

/**
 * The CommitInfo entity contains information about a commit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#commit-info
 */
export interface CommitInfo {
  commit?: CommitId;
  parents: ParentCommitInfo[];
  author: GitPersonInfo;
  committer: GitPersonInfo;
  subject: string;
  message: string;
  web_links?: WebLinkInfo[];
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
 * The parent commits of this commit as a list of CommitInfo entities.
 * In each parent only the commit and subject fields are populated.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#commit-info
 */
export interface ParentCommitInfo {
  commit: CommitId;
  subject: string;
}

/**
 * The FileInfo entity contains information about a file in a patch set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#file-info
 */
export interface FileInfo {
  status?: FileInfoStatus;
  binary?: boolean; // not set if false
  old_path?: string;
  lines_inserted?: number;
  lines_deleted?: number;
  size_delta: number; // in bytes
  size: number; // in bytes
}

/**
 * The PushCertificateInfo entity contains information about a pushcertificate
 * provided when the user pushed for review with git push
 * --signed HEAD:refs/for/<branch>. Only used when signed push is
 * enabled on the server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#push-certificate-info
 */
export interface PushCertificateInfo {
  certificate: string;
  key: GpgKeyInfo;
}

/**
 * The GpgKeyInfo entity contains information about a GPG public key.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#gpg-key-info
 */
export interface GpgKeyInfo {
  id?: GpgKeyId;
  fingerprint?: GpgKeyFingerprint;
  user_ids?: OpenPgpUserIds[];
  key?: string; // ASCII armored public key material
  status?: GpgKeyInfoStatus;
  problems?: string[];
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
 * The GitPersonInfo entity contains information about theauthor/committer of
 * a commit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#git-person-info
 */
export interface GitPersonInfo {
  name: string;
  email: string;
  date: Timestamp;
  tz: TimezoneOffset;
}

/**
 * The WebLinkInfo entity describes a link to an external site.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#web-link-info
 */
export interface WebLinkInfo {
  name: string;
  url: string;
  image_url: string;
}

/**
 * The VotingRangeInfo entity describes the continuous voting range from minto
 * max values.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#voting-range-info
 */
export interface VotingRangeInfo {
  min: number;
  max: number;
}

/**
 * The AccountsConfigInfo entity contains information about Gerrit configuration
 * from the accounts section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface AccountsConfigInfo {
  visibility: string;
  default_display_name: DefaultDisplayNameConfig;
}

/**
 * The AuthInfo entity contains information about the authentication
 * configuration of the Gerrit server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface AuthInfo {
  type: string;
  use_contributor_agreements: boolean;
  contributor_agreements?: ContributorAgreementInfo;
  editable_account_fields: string;
  login_url?: string;
  login_text?: string;
  switch_account_url?: string;
  register_url?: string;
  register_text?: string;
  edit_full_name_url?: string;
  http_password_url?: string;
  git_basic_auth_policy?: string;
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
 * The ChangeConfigInfo entity contains information about Gerrit configuration
 * from the change section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ChangeConfigInfo {
  allow_blame: boolean;
  large_change: string;
  reply_label: string;
  reply_tooltip: string;
  update_delay: string;
  submit_whole_topic: boolean;
  disable_private_changes: boolean;
  mergeability_computation_behavior: string;
  enable_attention_set: boolean;
  enable_assignee: boolean;
}

/**
 * The ChangeIndexConfigInfo entity contains information about Gerrit
 * configuration from the index.change section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ChangeIndexConfigInfo {
  index_mergeable: boolean;
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

export type SchemesInfoMap = {[name: string]: DownloadSchemeInfo};

/**
 * The DownloadInfo entity contains information about supported download
 * options.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface DownloadInfo {
  schemes: SchemesInfoMap;
  archives: string;
}

export type CloneCommandMap = {[name: string]: string};
/**
 * The DownloadSchemeInfo entity contains information about a supported download
 * scheme and its commands.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface DownloadSchemeInfo {
  url: string;
  is_auth_required: boolean;
  is_auth_supported: boolean;
  commands: string;
  clone_commands: CloneCommandMap;
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
 * The GerritInfo entity contains information about Gerrit configuration from
 * the gerrit section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#gerrit-info
 */
export interface GerritInfo {
  all_projects: string; // Doc contains incorrect name
  all_users: string; // Doc contains incorrect name
  doc_search: string;
  doc_url?: string;
  edit_gpg_keys: boolean;
  report_bug_url?: string;
  // The following property is missed in doc
  primary_weblink_name?: string;
}

/**
 * The IndexConfigInfo entity contains information about Gerrit configuration
 * from the index section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
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
 * The PluginConfigInfo entity contains information about Gerrit extensions by
 * plugins.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface PluginConfigInfo {
  has_avatars: boolean;
  js_resource_paths: string[];
  html_resource_paths: string[];
}

/**
 * The ReceiveInfo entity contains information about the configuration of
 * git-receive-pack behavior on the server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#receive-info
 */
export interface ReceiveInfo {
  enable_signed_push?: string;
}

/**
 * The ServerInfo entity contains information about the configuration of the
 * Gerrit server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#server-info
 */
export interface ServerInfo {
  accounts: AccountsConfigInfo;
  auth: AuthInfo;
  change: ChangeConfigInfo;
  download: DownloadInfo;
  gerrit: GerritInfo;
  index: IndexConfigInfo;
  note_db_enabled?: boolean;
  plugin: PluginConfigInfo;
  receive?: ReceiveInfo;
  sshd?: SshdInfo;
  suggest: SuggestInfo;
  user: UserConfigInfo;
  default_theme?: string;
}

/**
 * The SshdInfo entity contains information about Gerrit configuration from the sshd section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#sshd-info
 * This entity doesn’t contain any data, but the presence of this (empty) entity
 * in the ServerInfo entity means that SSHD is enabled on the server.
 */
export type SshdInfo = {};

/**
 * The SuggestInfo entity contains information about Gerritconfiguration from
 * the suggest section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html#suggest-info
 */
export interface SuggestInfo {
  from: string;
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
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface TopMenuEntryInfo {
  name: string;
  items: string;
}

/**
 * The TopMenuItemInfo entity contains information about a menu item ina top
 * menu entry.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface TopMenuItemInfo {
  url: string;
  name: string;
  target: string;
  id?: string;
}

/**
 * The UserConfigInfo entity contains information about Gerrit configuration
 * from the user section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface UserConfigInfo {
  anonymous_coward_name: string;
}

/*
 * The CommentInfo entity contains information about an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-info
 */
export interface CommentInfo {
  patch_set?: PatchSetNum;
  id: UrlEncodedCommentId;
  path?: string;
  side?: CommentSide;
  parent?: number;
  line?: number;
  range?: CommentRange;
  in_reply_to?: UrlEncodedCommentId;
  message?: string;
  updated: Timestamp;
  author?: AccountInfo;
  tag?: string;
  unresolved?: boolean;
  change_message_id?: string;
  commit_id?: string;
}

export type PathToCommentsInfoMap = {[path: string]: CommentInfo[]};

/**
 * The CommentRange entity describes the range of an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-range
 */
export interface CommentRange {
  start_line: number;
  start_character: number;
  end_line: number;
  end_character: number;
}

/**
 * The ProjectInfo entity contains information about a project
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#project-info
 */
export interface ProjectInfo {
  id: UrlEncodedRepoName;
  // name is not set if returned in a map where the project name is used as
  // map key
  name?: RepoName;
  // ?-<n> if the parent project is not visible (<n> is a number which
  // is increased for each non-visible project).
  parent?: RepoName;
  description?: string;
  state?: ProjectState;
  branches?: {[branchName: string]: CommitId};
  // labels is filled for Create Project and Get Project calls.
  labels?: LabelNameToLabelTypeInfoMap;
  // Links to the project in external sites
  web_links?: WebLinkInfo[];
}

export interface ProjectInfoWithName extends ProjectInfo {
  name: RepoName;
}

export type NameToProjectInfoMap = {[projectName: string]: ProjectInfo};
export type LabelNameToLabelTypeInfoMap = {[labelName: string]: LabelTypeInfo};

/**
 * The LabelTypeInfo entity contains metadata about the labels that a project
 * has.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#label-type-info
 */
export interface LabelTypeInfo {
  values: LabelTypeInfoValues;
  default_value: number;
}

export type LabelTypeInfoValues = {[value: string]: string};

/**
 * The DiffContent entity contains information about the content differences in a file.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-content
 */
export interface DiffContent {
  a?: string[];
  b?: string[];
  ab?: string[];
  // The inner array is always of length two. The first entry is the 'skip'
  // length. The second entry is the 'edit' length.
  edit_a?: number[][];
  edit_b?: number[][];
  due_to_rebase?: boolean;
  due_to_move?: boolean;
  skip?: string;
  common?: string;
  keyLocation?: boolean;
}

/**
 * The DiffFileMetaInfo entity contains meta information about a file diff.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-file-meta-info
 */
export interface DiffFileMetaInfo {
  name: string;
  content_type: string;
  lines: string;
  web_links?: WebLinkInfo[];
  language?: string;
}

/**
 * The DiffInfo entity contains information about the diff of a file in a revision.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-info
 */
export interface DiffInfo {
  meta_a: DiffFileMetaInfo;
  meta_b: DiffFileMetaInfo;
  change_type: string;
  intraline_status: string;
  diff_header: string[];
  content: DiffContent[];
  web_links?: DiffWebLinkInfo[];
  binary: boolean;
}

export type FilePathToDiffInfoMap = {[path: string]: DiffInfo};

/**
 * The DiffWebLinkInfo entity describes a link on a diff screen to an external site.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-web-link-info
 */
export interface DiffWebLinkInfo {
  name: string;
  url: string;
  image_url: string;
  show_on_side_by_side_diff_view: string;
  show_on_unified_diff_view: string;
}

/**
 * The DiffPreferencesInfo entity contains information about the diff preferences of a user.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#diff-preferences-info
 */
export interface DiffPreferencesInfo {
  context: number;
  expand_all_comments?: boolean;
  ignore_whitespace: IgnoreWhitespaceType;
  intraline_difference?: boolean;
  line_length: number;
  cursor_blink_rate: number;
  manual_review?: boolean;
  retain_header?: boolean;
  show_line_endings?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  skip_deleted?: boolean;
  skip_uncommented?: boolean;
  syntax_highlighting?: boolean;
  hide_top_menu?: boolean;
  auto_hide_diff_table_header?: boolean;
  hide_line_numbers?: boolean;
  tab_size: number;
  font_size: number;
  hide_empty_pane?: boolean;
  match_brackets?: boolean;
  line_wrapping?: boolean;
  // TODO(TS): show_file_comment_button exists in JS code, but doesn't exist in the doc.
  // Either remove or update doc
  show_file_comment_button?: boolean;
  // TODO(TS): theme exists in JS code, but doesn't exist in the doc.
  // Either remove or update doc
  theme?: string;
}
export type DiffPreferencesInfoKey = keyof DiffPreferencesInfo;

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
  _width?: number;
  _height?: number;
}

/**
 * A boolean value that can also be inherited.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#inherited-boolean-info
 */
export interface InheritedBooleanInfo {
  value: string;
  configured_value: InheritedBooleanInfoConfiguredValue;
  inherited_value?: string;
}

/**
 * The MaxObjectSizeLimitInfo entity contains information about themax object
 * size limit of a project.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#max-object-size-limit-info
 */
export interface MaxObjectSizeLimitInfo {
  value?: number;
  configured_value?: string;
  summary?: string;
}

/**
 * Information about the default submittype of a project, taking into account
 * project inheritance.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#submit-type-info
 */
export interface SubmitTypeInfo {
  value: Exclude<SubmitType, SubmitType.INHERIT>;
  configured_value: SubmitType;
  inherited_value: Exclude<SubmitType, SubmitType.INHERIT>;
}

/**
 * The CommentLinkInfo entity describes acommentlink.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#commentlink-info
 */
export interface CommentLinkInfo {
  match: string;
  link?: string;
  enabled?: boolean;
  html?: string;
}

/**
 * The ConfigParameterInfo entity describes a project configurationparameter.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#config-parameter-info
 */
export interface ConfigParameterInfoBase {
  display_name?: string;
  description?: string;
  warning?: string;
  type: ConfigParameterInfoType;
  value?: string;
  values?: string[];
  editable?: boolean;
  permitted_values?: string[];
  inheritable?: boolean;
  configured_value?: string;
  inherited_value?: string;
}

export interface ConfigArrayParameterInfo extends ConfigParameterInfoBase {
  type: ConfigParameterInfoType.ARRAY;
  values: string[];
}

export interface ConfigListParameterInfo extends ConfigParameterInfoBase {
  type: ConfigParameterInfoType.LIST;
  permitted_values?: string[];
}

export type ConfigParameterInfo =
  | ConfigParameterInfoBase
  | ConfigArrayParameterInfo
  | ConfigListParameterInfo;

export interface CommentLinks {
  [name: string]: CommentLinkInfo;
}

/**
 * The ConfigInfo entity contains information about the effective
 * projectconfiguration.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#config-info
 */
export interface ConfigInfo {
  description?: string;
  use_contributor_agreements?: InheritedBooleanInfo;
  use_content_merge?: InheritedBooleanInfo;
  use_signed_off_by?: InheritedBooleanInfo;
  create_new_change_for_all_not_in_target?: InheritedBooleanInfo;
  require_change_id?: InheritedBooleanInfo;
  enable_signed_push?: InheritedBooleanInfo;
  require_signed_push?: InheritedBooleanInfo;
  reject_implicit_merges?: InheritedBooleanInfo;
  private_by_default: InheritedBooleanInfo;
  work_in_progress_by_default: InheritedBooleanInfo;
  max_object_size_limit: MaxObjectSizeLimitInfo;
  default_submit_type: SubmitTypeInfo;
  submit_type: SubmitType;
  match_author_to_committer_date?: InheritedBooleanInfo;
  state?: ProjectState;
  commentlinks: CommentLinks;
  plugin_config?: PluginNameToPluginParametersMap;
  actions?: {[viewName: string]: ActionInfo};
  reject_empty_commit?: InheritedBooleanInfo;
}

/**
 * The ProjectAccessInfo entity contains information about the access rights for a project
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
  config_web_links: string[];
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
  description?: string;
  foreach?: string;
  url: string;
  is_default?: boolean;
  title?: boolean;
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
/**
 * Plugin configuration values as map which maps the plugin name to a map of parameter names to values
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#config-input
 */
export type PluginNameToPluginParametersMap = {
  [pluginName: string]: PluginParameterToConfigParameterInfoMap;
};

export type PluginParameterToConfigParameterInfoMap = {
  [parameterName: string]: ConfigParameterInfo;
};

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
}
/**
 * The DeleteDraftCommentsInput entity contains information specifying a set of draft comments that should be deleted
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#delete-draft-comments-input
 */
export interface DeleteDraftCommentsInput {
  query: string;
}

/**
 * The AssigneeInput entity contains the identity of the user to be set as assignee
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#assignee-input
 */
export interface AssigneeInput {
  assignee: AccountId;
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
 * Defines a patch ranges. Used as input for gr-rest-api-interface methods,
 * doesn't exist in Rest API
 */
export interface PatchRange {
  patchNum: PatchSetNum;
  basePatchNum: PatchSetNum;
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
  intraline_difference?: boolean;
  line_length?: number;
  manual_review?: boolean;
  retain_header?: boolean;
  show_line_endings?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  skip_deleted?: boolean;
  skip_uncommented?: boolean;
  syntax_highlighting?: boolean;
  hide_top_menu?: boolean;
  auto_hide_diff_table_header?: boolean;
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
  priority: UserPriority;
  queryLimit: QueryLimitInfo;
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
  work_in_progress_by_default?: boolean;
  // The email_format doesn't mentioned in doc, but exists in Java class GeneralPreferencesInfo
  email_format?: EmailFormat;
  // The following property doesn't exist in RestAPI, it is added by GrRestApiInterface
  default_diff_view?: DiffViewMode;
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
  labels?: LabelNameToValuesMap;
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
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#add-reviewer-result
 */
export interface AddReviewerResult {
  input: AccountId | GroupId | EmailAddress;
  reviewers?: AccountInfo[];
  ccs?: AccountInfo[];
  error?: string;
  confirm?: boolean;
}

export type LabelNameToValuesMap = {[labelName: string]: number};
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
  base_patch_set_number: PatchSetNum;
  base_revision: string;
  ref: GitRef;
  fetch: ProtocolToFetchInfoMap;
  files: FileNameToFileInfoMap;
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
