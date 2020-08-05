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
} from '../constants/constants';

export type BrandType<T, BrandName extends string> = T &
  {[__brand in BrandName]: never};

export type PatchSetNum = BrandType<'edit' | number, '_patchSet'>;
export const EditPatchSetNum = 'edit' as PatchSetNum;

export type ChangeId = BrandType<string, '_changeId'>;
export type ChangeMessageId = BrandType<string, '_changeMessageId'>;
export type LegacyChangeId = BrandType<number, '_legacyChangeId'>;
export type NumericChangeId = BrandType<number, '_numericChangeId'>;
export type ProjectName = BrandType<string, '_projectName'>;
export type UrlEncodedProjectName = BrandType<string, '_urlEncodedProjectName'>;
export type TopicName = BrandType<string, '_topicName'>;
export type AccountId = BrandType<number, '_accountId'>;
export type HttpMethod = BrandType<string, '_httpMethod'>;
export type GitRef = BrandType<string, '_gitRef'>;
export type RequirementType = BrandType<string, '_requirementType'>;
export type TrackingId = BrandType<string, '_trackingId'>;
export type ReviewInputTag = BrandType<string, '_reviewInputTag'>;
export type RepositoryName = BrandType<string, '_repositoryName'>;

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

// The ID of the change in the format "'<project>~<branch>~<Change-Id>'"
export type ChangeInfoId = BrandType<string, '_changeInfoId'>;
export type Hashtag = BrandType<string, '_hashtag'>;
export type StarLabel = BrandType<string, '_startLabel'>;
export type SubmitType = BrandType<string, '_submitType'>;
export type CommitId = BrandType<string, '_commitId'>;

// The UUID of the group
export type GroupId = BrandType<string, '_groupId'>;

// The timezone offset from UTC in minutes
export type TimezoneOffset = BrandType<number, '_timezoneOffset'>;

// Timestamps are given in UTC and have the format
// "'yyyy-mm-dd hh:mm:ss.fffffffff'"
// where "'ffffffffff'" represents nanoseconds.
export type Timestamp = BrandType<string, '_timestamp'>;

export type IdToAttentionSetMap = {[accountId: string]: AttentionSetInfo};
export type LabelNameToInfoMap = {[labelName: string]: LabelInfo};

// The map maps the values (“-2”, “-1”, " `0`", “+1”, “+2”) to the value descriptions.
export type LabelValueToDescriptionMap = {[labelValue: string]: string};

/**
 * The LabelInfo entity contains information about a label on a change, always corresponding to the current patch set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#label-info
 */
type LabelInfo = QuickLabelInfo | DetailedLabelInfo;

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

export interface DetailedLabelInfo extends LabelCommonInfo {
  all?: ApprovalInfo[];
  values?: LabelValueToDescriptionMap; // A map of all values that are allowed for this label
}

/**
 * The ChangeInfo entity contains information about a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
 */
export interface ChangeInfo {
  id: ChangeInfoId;
  project: ProjectName;
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
  _number: LegacyChangeId;
  owner: AccountInfo;
  actions?: ActionInfo[];
  requirements?: Requirement[];
  labels?: LabelInfo[];
  permitted_labels?: LabelNameToInfoMap;
  removable_reviewers?: AccountInfo[];
  reviewers?: AccountInfo[];
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
 * The AccountInfo entity contains information about an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-info
 */
export interface AccountInfo {
  _account_id: AccountId;
  name?: string;
  display_name?: string;
  email?: string;
  secondary_emails?: string[];
  username?: string;
  avatars?: AvatarInfo[];
  _more_accounts?: boolean; // not set if false
  status?: string; // status message of the account
  inactive?: boolean; // not set if false
}

/**
 * The AccountDetailInfo entity contains detailed information about an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-detail-info
 */
export interface AccountDetailInfo extends AccountInfo {
  registered_on: Timestamp;
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
  name: string;
}

/**
 * The GroupInfo entity contains information about a group. This can be a
 * Gerrit internal group, or an external group that is known to Gerrit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupInfo {
  id: GroupId;
  name: string;
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

/**
 * The 'GroupInput' entity contains information for the creation of a new
 * internal group.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-groups.html
 */
export interface GroupInput {
  name?: string;
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
 * The ReviewerUpdateInfo entity contains information about updates tochange’s
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
  last_update: Timestamp;
}

/**
 * The ApprovalInfo entity contains information about an approval from auser
 * for a label on a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#approval-info
 */
export interface ApprovalInfo extends AccountInfo {
  value?: string;
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
  contributor_agreements: boolean;
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
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface CapabilityInfo {
  id: string;
  name: string;
}

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
  mergeability_computation_behavior: ChangeInfo;
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

/**
 * The DownloadInfo entity contains information about supported download
 * options.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface DownloadInfo {
  schemes: string;
  archives: string;
}

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
  clone_commands: string;
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
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface GerritInfo {
  all_projects_name: string;
  all_users_name: string;
  doc_search: string;
  doc_url?: string;
  edit_gpg_keys: boolean;
  report_bug_url?: string;
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
}

/**
 * The ReceiveInfo entity contains information about the configuration of
 * git-receive-pack behavior on the server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ReceiveInfo {
  enableSignedPush?: string;
}

/**
 * The ServerInfo entity contains information about the configuration of the
 * Gerrit server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
 */
export interface ServerInfo {
  accounts: AccountsConfigInfo;
  auth: AuthInfo;
  change: ChangeConfigInfo;
  download: DownloadInfo;
  gerrit: GerritInfo;
  index: IndexConfigInfo;
  note_db_enabled: boolean;
  plugin: PluginConfigInfo;
  receive?: ReceiveInfo;
  suggest: SuggestInfo;
  user: UserConfigInfo;
  default_theme?: string;
}

/**
 * The SuggestInfo entity contains information about Gerritconfiguration from
 * the suggest section.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-config.html
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
  side?: string;
  parent?: string;
  line?: string;
  range?: CommentRange;
  in_reply_to?: string;
  message?: string;
  updated: string;
  author?: AccountInfo;
  tag?: string;
  unresolved?: boolean;
  change_message_id?: string;
  commit_id?: string;
}

/**
 * The CommentRange entity describes the range of an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-range
 */
export interface CommentRange {
  start_line: string;
  start_character: string;
  end_line: string;
  end_character: string;
}

/**
 * The ProjectInfo entity contains information about a project
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#project-info
 */
export interface ProjectInfo {
  id: UrlEncodedProjectName;
  // name is not set if returned in a map where the project name is used as
  // map key
  name?: ProjectName;
  // ?-<n> if the parent project is not visible (<n> is a number which
  // is increased for each non-visible project).
  parent?: ProjectName;
  description?: string;
  state?: ProjectState;
  branches?: {[branchName: string]: CommitId};
  // labels is filled for Create Project and Get Project calls.
  labels?: {[labelName: string]: LabelTypeInfo};
  // Links to the project in external sites
  web_links?: WebLinkInfo[];
}

/**
 * The LabelTypeInfo entity contains metadata about the labels that a project
 * has.
 */
export interface LabelTypeInfo {
  values: {[value: string]: string};
  default_value: number;
}
