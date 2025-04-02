// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.mail;

import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.mail.send.AttentionSetChangeEmailDecorator;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;
import com.google.gerrit.server.mail.send.CommentChangeEmailDecorator;
import com.google.gerrit.server.mail.send.DeleteReviewerChangeEmailDecorator;
import com.google.gerrit.server.mail.send.InboundEmailRejectionEmailDecorator.InboundEmailError;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import com.google.gerrit.server.mail.send.RegisterNewEmailDecorator;
import com.google.gerrit.server.mail.send.ReplacePatchSetChangeEmailDecorator;
import com.google.gerrit.server.mail.send.StartReviewChangeEmailDecorator;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Set of factories for default email notifications.
 *
 * <p>To send a change related email, first {@link ChangeEmailDecorator} needs to be constructed and
 * configured. The decorator is then passed in {@link #createChangeEmail(NameKey, Change.Id,
 * ChangeEmailDecorator)} and finally the result to {@link #createOutgoingEmail(String,
 * EmailDecorator)} to be sent.
 */
public interface EmailFactories {
  // messageClass names used for logging and email filtering in email clients.
  String CHANGE_ABANDONED = "abandon";
  String ATTENTION_SET_ADDED = "addToAttentionSet";
  String ATTENTION_SET_REMOVED = "removeFromAttentionSet";
  String COMMENTS_ADDED = "comment";
  String REVIEWER_DELETED = "deleteReviewer";
  String VOTE_DELETED = "deleteVote";
  String CHANGE_MERGED = "merged";
  String NEW_PATCHSET_ADDED = "newpatchset";
  String CHANGE_RESTORED = "restore";
  String CHANGE_REVERTED = "revert";
  String REVIEW_REQUESTED = "newchange";
  String KEY_ADDED = "addkey";
  String KEY_DELETED = "deletekey";
  String AUTH_TOKEN_UPDATED = "AuthTokenUpdate";
  String PASSWORD_UPDATED = "HttpPasswordUpdate";
  String INBOUND_EMAIL_REJECTED = "error";
  String NEW_EMAIL_REGISTERED = "registernewemail";

  public static String messageClassDisplay(String messageClass) {
    return switch (messageClass) {
      case CHANGE_ABANDONED -> "Abandoned";
      case ATTENTION_SET_ADDED -> "Added to Attention Set";
      case ATTENTION_SET_REMOVED -> "Removed from Attention Set";
      case COMMENTS_ADDED -> "Comments";
      case REVIEWER_DELETED -> "Reviewer Deleted";
      case VOTE_DELETED -> "Vote Deleted";
      case CHANGE_MERGED -> "Merged";
      case NEW_PATCHSET_ADDED -> "New Patchset";
      case CHANGE_RESTORED -> "Restored";
      case CHANGE_REVERTED -> "Reverted";
      case REVIEW_REQUESTED -> "Review Request";
      case KEY_ADDED -> "Key Added";
      case KEY_DELETED -> "Key Deleted";
      case AUTH_TOKEN_UPDATED -> "Authentication Token Updated";
      case PASSWORD_UPDATED -> "Password Updated";
      case INBOUND_EMAIL_REJECTED -> "Error";
      case NEW_EMAIL_REGISTERED -> "Email Registered";
      default -> messageClass;
    };
  }

  /** ChangeEmail decorator that adds information about change being abandoned to the email. */
  ChangeEmailDecorator createAbandonedChangeEmail();

  /** ChangeEmail decorator that adds information about attention set change to the email. */
  AttentionSetChangeEmailDecorator createAttentionSetChangeEmail();

  /** ChangeEmail decorator that adds information about an iteration of review to the email. */
  CommentChangeEmailDecorator createCommentChangeEmail(
      Project.NameKey project,
      Change.Id changeId,
      ObjectId preUpdateMetaId,
      Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults);

  /** ChangeEmail decorator that adds information about deleted reviewer to the email. */
  DeleteReviewerChangeEmailDecorator createDeleteReviewerChangeEmail();

  /** ChangeEmail decorator that adds information about deleted vote to the email. */
  ChangeEmailDecorator createDeleteVoteChangeEmail();

  /** ChangeEmail decorator that adds information about change being merged to the email. */
  ChangeEmailDecorator createMergedChangeEmail(
      Optional<String> stickyApprovalDiff, List<FileDiffOutput> modifiedFiles);

  /** ChangeEmail decorator that adds information about a new patchset added to the change. */
  ReplacePatchSetChangeEmailDecorator createReplacePatchSetChangeEmail(
      Project.NameKey project,
      Change.Id changeId,
      ChangeKind changeKind,
      ObjectId preUpdateMetaId,
      Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults);

  /** ChangeEmail decorator that adds information about change being restored to the email. */
  ChangeEmailDecorator createRestoredChangeEmail();

  /** ChangeEmail decorator that adds information about change being reverted to the email. */
  ChangeEmailDecorator createRevertedChangeEmail();

  /**
   * ChangeEmail decorator that adds information when change is uploaded or first sent to review.
   */
  StartReviewChangeEmailDecorator createStartReviewChangeEmail();

  /** Base email decorator for change-related emails. */
  ChangeEmail createChangeEmail(
      Project.NameKey project, Change.Id changeId, ChangeEmailDecorator changeEmailDecorator);

  /** Email decorator for adding a key to the account. */
  EmailDecorator createAddKeyEmail(IdentifiedUser user, AccountSshKey sshKey);

  /** Email decorator for adding gpg keys to the account. */
  EmailDecorator createAddKeyEmail(IdentifiedUser user, List<String> gpgKeys);

  /** Email decorator for adding a key to the account. */
  EmailDecorator createDeleteKeyEmail(IdentifiedUser user, AccountSshKey sshKey);

  /** Email decorator for adding gpg keys to the account. */
  EmailDecorator createDeleteKeyEmail(IdentifiedUser user, List<String> gpgKeys);

  /** Email decorator for auth token modification operations. */
  EmailDecorator createAuthTokenUpdateEmail(IdentifiedUser user, String operation, String tokenId);

  /** Email decorator for password modification operations. */
  EmailDecorator createHttpPasswordUpdateEmail(IdentifiedUser user, String operation);

  /** Email decorator for inbound email errors. */
  EmailDecorator createInboundEmailRejectionEmail(
      Address to, String threadId, InboundEmailError reason);

  /** Email decorator for the "new email address added" notification. */
  RegisterNewEmailDecorator createRegisterNewEmail(String address);

  /** Base class for any outgoing email. */
  OutgoingEmail createOutgoingEmail(String messageClass, EmailDecorator emailDecorator);
}
