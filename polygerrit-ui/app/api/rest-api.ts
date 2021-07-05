/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

/**
 * rest-api.ts contains all entities from the Gerrit REST API that are also
 * relevant to plugins and gr-diff users. These entities should be exactly what
 * the backend defines and returns and should eventually be generated.
 *
 * Sorting order:
 * - types
 * - enums
 * - interfaces
 * - type checking functions
 */

/**
 * types =======================================================================
 */

export type BrandType<T, BrandName extends string> = T &
  {[__brand in BrandName]: never};
export type AccountId = BrandType<number, '_accountId'>;
export type BasePatchSetNum = BrandType<'PARENT' | number, '_patchSet'>;
// The refs/heads/ prefix is omitted in Branch name
export type BranchName = BrandType<string, '_branchName'>;
export type ChangeId = BrandType<string, '_changeId'>;
// The ID of the change in the format "'<project>~<branch>~<Change-Id>'"
export type ChangeInfoId = BrandType<string, '_changeInfoId'>;
export type ChangeMessageId = BrandType<string, '_changeMessageId'>;
// This ID is equal to the numeric ID of the change that triggered the
// submission. If the change that triggered the submission also has a topic, it
// will be "<id>-<topic>" of the change that triggered the submission
// The callers must not rely on the format of the submission ID.
export type ChangeSubmissionId = BrandType<
  string | number,
  '_changeSubmissionId'
>;
export type CommitId = BrandType<string, '_commitId'>;
export type EmailAddress = BrandType<string, '_emailAddress'>;
export type GitRef = BrandType<string, '_gitRef'>;
// The 40-char (plus spaces) hex GPG key fingerprint
export type GpgKeyFingerprint = BrandType<string, '_gpgKeyFingerprint'>;
// The 8-char hex GPG key ID.
export type GpgKeyId = BrandType<string, '_gpgKeyId'>;
export type Hashtag = BrandType<string, '_hashtag'>;
export type IdToAttentionSetMap = {[accountId: string]: AttentionSetInfo};
export type LabelNameToInfoMap = {[labelName: string]: LabelInfo};
// {Verified: ["-1", " 0", "+1"]}
export type LabelNameToValueMap = {[labelName: string]: string[]};
/**
 * The LabelInfo entity contains information about a label on a change, always
 * corresponding to the current patch set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#label-info
 */
export type LabelInfo =
  | QuickLabelInfo
  | DetailedLabelInfo
  | (QuickLabelInfo & DetailedLabelInfo);
// The map maps the values (“-2”, “-1”, " `0`", “+1”, “+2”) to the value descriptions.
export type LabelValueToDescriptionMap = {[labelValue: string]: string};
export type NumericChangeId = BrandType<number, '_numericChangeId'>;
// OpenPGP User IDs (https://tools.ietf.org/html/rfc4880#section-5.11).
export type OpenPgpUserIds = BrandType<string, '_openPgpUserIds'>;
export type PatchSetNum = BrandType<'PARENT' | 'edit' | number, '_patchSet'>;
export type RepoName = BrandType<string, '_repoName'>;
export type RequirementType = BrandType<string, '_requirementType'>;
/**
 * The reviewers as a map that maps a reviewer state to a list of AccountInfo
 * entities. Possible reviewer states are REVIEWER, CC and REMOVED.
 * REVIEWER: Users with at least one non-zero vote on the change.
 * CC: Users that were added to the change, but have not voted.
 * REMOVED: Users that were previously reviewers on the change, but have been removed.
 */
export type Reviewers = Partial<Record<ReviewerState, AccountInfo[]>>;
export type ReviewInputTag = BrandType<string, '_reviewInputTag'>;
export type StarLabel = BrandType<string, '_startLabel'>;
// Timestamps are given in UTC and have the format
// "'yyyy-mm-dd hh:mm:ss.fffffffff'"
// where "'ffffffffff'" represents nanoseconds.
export type Timestamp = BrandType<string, '_timestamp'>;
// The timezone offset from UTC in minutes
export type TimezoneOffset = BrandType<number, '_timezoneOffset'>;
export type TopicName = BrandType<string, '_topicName'>;
export type TrackingId = BrandType<string, '_trackingId'>;

/**
 * enums =======================================================================
 */

export enum AccountTag {
  SERVICE_USER = 'SERVICE_USER',
}

/**
 * @desc Specifies status for a change
 */
export enum ChangeStatus {
  ABANDONED = 'ABANDONED',
  MERGED = 'MERGED',
  NEW = 'NEW',
}

/**
 * @desc The status of the file
 */
export enum FileInfoStatus {
  ADDED = 'A',
  DELETED = 'D',
  RENAMED = 'R',
  COPIED = 'C',
  REWRITTEN = 'W',
  // Modifed = 'M', // but API not set it if the file was modified
  UNMODIFIED = 'U', // Not returned by BE, but added by UI for certain files
}

/**
 * @desc The status of the file
 */
export enum GpgKeyInfoStatus {
  BAD = 'BAD',
  OK = 'OK',
  TRUSTED = 'TRUSTED',
}

/**
 * Enum for all http methods used in Gerrit.
 */
export enum HttpMethod {
  HEAD = 'HEAD',
  POST = 'POST',
  GET = 'GET',
  DELETE = 'DELETE',
  PUT = 'PUT',
}

/**
 * @desc The status of fixing the problem
 */
export enum ProblemInfoStatus {
  FIXED = 'FIXED',
  FIX_FAILED = 'FIX_FAILED',
}

/**
 * @desc The reviewer state
 */
export enum RequirementStatus {
  OK = 'OK',
  NOT_READY = 'NOT_READY',
  RULE_ERROR = 'RULE_ERROR',
}

/**
 * @desc The reviewer state
 */
export enum ReviewerState {
  REVIEWER = 'REVIEWER',
  CC = 'CC',
  REMOVED = 'REMOVED',
}

/**
 * @desc The patchset kind
 */
export enum RevisionKind {
  REWORK = 'REWORK',
  TRIVIAL_REBASE = 'TRIVIAL_REBASE',
  MERGE_FIRST_PARENT_UPDATE = 'MERGE_FIRST_PARENT_UPDATE',
  NO_CODE_CHANGE = 'NO_CODE_CHANGE',
  NO_CHANGE = 'NO_CHANGE',
}

/**
 * All supported submit types.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#submit-type-info
 */
export enum SubmitType {
  MERGE_IF_NECESSARY = 'MERGE_IF_NECESSARY',
  FAST_FORWARD_ONLY = 'FAST_FORWARD_ONLY',
  REBASE_IF_NECESSARY = 'REBASE_IF_NECESSARY',
  REBASE_ALWAYS = 'REBASE_ALWAYS',
  MERGE_ALWAYS = 'MERGE_ALWAYS ',
  CHERRY_PICK = 'CHERRY_PICK',
  INHERIT = 'INHERIT',
}

/**
 * interfaces ==================================================================
 */

/**
 * The AccountInfo entity contains information about an account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-info
 */
export declare interface AccountInfo {
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

/**
 * The ActionInfo entity describes a REST API call the client can make to
 * manipulate a resource. These are frequently implemented by plugins and may
 * be discovered at runtime.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#action-info
 */
export declare interface ActionInfo {
  method?: HttpMethod; // Most actions use POST, PUT or DELETE to cause state changes.
  label?: string; // Short title to display to a user describing the action
  title?: string; // Longer text to display describing the action
  enabled?: boolean; // not set if false
}

export declare interface ActionNameToActionInfoMap {
  [actionType: string]: ActionInfo | undefined;
  // List of actions explicitly used in code:
  wip?: ActionInfo;
  publishEdit?: ActionInfo;
  rebaseEdit?: ActionInfo;
  deleteEdit?: ActionInfo;
  edit?: ActionInfo;
  stopEdit?: ActionInfo;
  download?: ActionInfo;
  rebase?: ActionInfo;
  cherrypick?: ActionInfo;
  move?: ActionInfo;
  revert?: ActionInfo;
  revert_submission?: ActionInfo;
  abandon?: ActionInfo;
  submit?: ActionInfo;
  topic?: ActionInfo;
  hashtags?: ActionInfo;
  assignee?: ActionInfo;
  ready?: ActionInfo;
  includedIn?: ActionInfo;
}

/**
 * The ApprovalInfo entity contains information about an approval from auser
 * for a label on a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#approval-info
 */
export declare interface ApprovalInfo extends AccountInfo {
  value?: number;
  permitted_voting_range?: VotingRangeInfo;
  date?: Timestamp;
  tag?: ReviewInputTag;
  post_submit?: boolean; // not set if false
}

/**
 * The AttentionSetInfo entity contains details of users that are in the attention set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#attention-set-info
 */
export declare interface AttentionSetInfo {
  account: AccountInfo;
  last_update?: Timestamp;
  reason?: string;
}

/**
 * The AvartarInfo entity contains information about an avatar image ofan
 * account.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#avatar-info
 */
export declare interface AvatarInfo {
  url: string;
  height: number;
  width: number;
}

/**
 * The ChangeInfo entity contains information about a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
 */
export declare interface ChangeInfo {
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
  submitter?: AccountInfo;
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
 * The ChangeMessageInfo entity contains information about a message attached
 * to a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-message-info
 */
export declare interface ChangeMessageInfo {
  id: ChangeMessageId;
  author?: AccountInfo;
  reviewer?: AccountInfo;
  updated_by?: AccountInfo;
  real_author?: AccountInfo;
  date: Timestamp;
  message: string;
  accounts_in_message?: AccountInfo[];
  tag?: ReviewInputTag;
  _revision_number?: PatchSetNum;
}

/**
 * The CommitInfo entity contains information about a commit.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#commit-info
 */
export declare interface CommitInfo {
  commit?: CommitId;
  parents: ParentCommitInfo[];
  author: GitPersonInfo;
  committer: GitPersonInfo;
  subject: string;
  message: string;
  web_links?: WebLinkInfo[];
  resolve_conflicts_web_links?: WebLinkInfo[];
}

/**
 * LabelInfo when DETAILED_LABELS are requested.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#_fields_set_by_code_detailed_labels_code
 */
export declare interface DetailedLabelInfo extends LabelCommonInfo {
  // This is not set when the change has no reviewers.
  all?: ApprovalInfo[];
  // Docs claim that 'values' is optional, but it is actually always set.
  values?: LabelValueToDescriptionMap; // A map of all values that are allowed for this label
  default_value?: number;
}

/**
 * The FetchInfo entity contains information about how to fetch a patchset via
 * a certain protocol.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#fetch-info
 */
export declare interface FetchInfo {
  url: string;
  ref: string;
  commands?: {[commandName: string]: string};
}

/**
 * The FileInfo entity contains information about a file in a patch set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#file-info
 */
export declare interface FileInfo {
  status?: FileInfoStatus;
  binary?: boolean; // not set if false
  old_path?: string;
  lines_inserted?: number;
  lines_deleted?: number;
  size_delta: number; // in bytes
  size: number; // in bytes
}

/**
 * The GpgKeyInfo entity contains information about a GPG public key.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#gpg-key-info
 */
export declare interface GpgKeyInfo {
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
export declare interface GitPersonInfo {
  name: string;
  email: string;
  date: Timestamp;
  tz: TimezoneOffset;
}

interface LabelCommonInfo {
  optional?: boolean; // not set if false
}

/**
 * The parent commits of this commit as a list of CommitInfo entities.
 * In each parent only the commit and subject fields are populated.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#commit-info
 */
export declare interface ParentCommitInfo {
  commit: CommitId;
  subject: string;
}

/**
 * The ProblemInfo entity contains a description of a potential consistency
 * problem with a change. These are not related to the code review process,
 * but rather indicate some inconsistency in Gerrit’s database or repository
 * metadata related to the enclosing change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#problem-info
 */
export declare interface ProblemInfo {
  message: string;
  status?: ProblemInfoStatus; // Only set if a fix was attempted
  outcome?: string;
}

/**
 * The PushCertificateInfo entity contains information about a pushcertificate
 * provided when the user pushed for review with git push
 * --signed HEAD:refs/for/<branch>. Only used when signed push is
 * enabled on the server.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#push-certificate-info
 */
export declare interface PushCertificateInfo {
  certificate: string;
  key: GpgKeyInfo;
}

export declare interface QuickLabelInfo extends LabelCommonInfo {
  approved?: AccountInfo;
  rejected?: AccountInfo;
  recommended?: AccountInfo;
  disliked?: AccountInfo;
  blocking?: boolean; // not set if false
  value?: number; // The voting value of the user who recommended/disliked this label on the change if it is not “+1”/“-1”.
  default_value?: number;
}

/**
 * The Requirement entity contains information about a requirement relative to
 * a change.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#requirement
 */
export declare interface Requirement {
  status: RequirementStatus;
  fallbackText: string; // A human readable reason
  type: RequirementType;
}

/**
 * The ReviewerUpdateInfo entity contains information about updates to change’s
 * reviewers set.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-update-info
 */
export declare interface ReviewerUpdateInfo {
  updated: Timestamp;
  updated_by: AccountInfo;
  reviewer: AccountInfo;
  state: ReviewerState;
}

/**
 * The RevisionInfo entity contains information about a patch set.Not all
 * fields are returned by default.  Additional fields can be obtained by
 * adding o parameters as described in Query Changes.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info
 * basePatchNum is present in case RevisionInfo is of type 'edit'
 */
export declare interface RevisionInfo {
  kind: RevisionKind;
  _number: PatchSetNum;
  created: Timestamp;
  uploader: AccountInfo;
  ref: GitRef;
  fetch?: {[protocol: string]: FetchInfo};
  commit?: CommitInfo;
  files?: {[filename: string]: FileInfo};
  actions?: ActionNameToActionInfoMap;
  reviewed?: boolean;
  commit_with_footers?: boolean;
  push_certificate?: PushCertificateInfo;
  description?: string;
  basePatchNum?: BasePatchSetNum;
}

/**
 * The TrackingIdInfo entity describes a reference to an external tracking
 * system.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#tracking-id-info
 */
export declare interface TrackingIdInfo {
  system: string;
  id: TrackingId;
}

/**
 * The VotingRangeInfo entity describes the continuous voting range from minto
 * max values.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#voting-range-info
 */
export declare interface VotingRangeInfo {
  min: number;
  max: number;
}

/**
 * The WebLinkInfo entity describes a link to an external site.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#web-link-info
 */
export declare interface WebLinkInfo {
  /** The link name. */
  name: string;
  /** The link URL. */
  url: string;
  /** URL to the icon of the link. */
  image_url: string;
}

/**
 * type checking functions =====================================================
 */

export function isDetailedLabelInfo(
  label: LabelInfo
): label is DetailedLabelInfo | (QuickLabelInfo & DetailedLabelInfo) {
  return !!(label as DetailedLabelInfo).values;
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
