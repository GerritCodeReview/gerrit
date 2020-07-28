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
  FileInfoStatus,
  GpgKeyInfoStatus,
  ProblemInfoStatus,
  RequirementStatus,
  ReviewerState,
  RevisionKind,
} from '../constants/constants';

export type BrandType<T, BrandName extends string> = T &
  {[__brand in BrandName]: never};

export type PatchSetNum = BrandType<'edit' | number, '_patchSet'>;
export type ChangeId = BrandType<string, '_changeId'>;
export type ChangeMessageId = BrandType<string, '_changeMessageId'>;
export type LegacyChangeId = BrandType<number, '_legacyChangeId'>;
export type NumericChangeId = BrandType<number, '_numericChangeId'>;
export type ProjectName = BrandType<string, '_projectName'>;
export type TopicName = BrandType<string, '_topicName'>;
export type AccountId = BrandType<number, '_accountId'>;
export type HttpMethod = BrandType<string, '_httpMethod'>;
export type GitRef = BrandType<string, '_gitRef'>;
export type RequirementType = BrandType<string, '_requirementType'>;
export type TrackingId = BrandType<string, '_trackingId'>;
export type ReviewInputTag = BrandType<string, '_reviewInputTag'>;

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
