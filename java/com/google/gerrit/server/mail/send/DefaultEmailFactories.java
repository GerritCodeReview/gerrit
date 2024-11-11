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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;
import com.google.gerrit.server.mail.send.InboundEmailRejectionEmailDecorator.InboundEmailError;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.ObjectId;

/** Default versions of Gerrit email notifications. */
@Singleton
public class DefaultEmailFactories implements EmailFactories {
  private final CommentChangeEmailDecoratorImplFactory commentChangeEmailFactory;
  private final MergedChangeEmailDecoratorFactory mergedChangeEmailFactory;
  private final ReplacePatchSetChangeEmailDecoratorImplFactory replacePatchSetChangeEmailFactory;
  private final ChangeEmailImplFactory changeEmailFactory;
  private final AddKeyEmailDecoratorFactory addKeyEmailFactory;
  private final DeleteKeyEmailDecoratorFactory deleteKeyEmailFactory;
  private final HttpPasswordUpdateEmailDecoratorFactory httpPasswordUpdateEmailFactory;
  private final RegisterNewEmailDecoratorImplFactory registerNewEmailFactory;
  private final OutgoingEmailFactory outgoingEmailFactory;

  @Inject
  DefaultEmailFactories(
      CommentChangeEmailDecoratorImplFactory commentChangeEmailFactory,
      MergedChangeEmailDecoratorFactory mergedChangeEmailFactory,
      ReplacePatchSetChangeEmailDecoratorImplFactory replacePatchSetChangeEmailFactory,
      ChangeEmailImplFactory changeEmailFactory,
      AddKeyEmailDecoratorFactory addKeyEmailFactory,
      DeleteKeyEmailDecoratorFactory deleteKeyEmailFactory,
      HttpPasswordUpdateEmailDecoratorFactory httpPasswordUpdateEmailFactory,
      RegisterNewEmailDecoratorImplFactory registerNewEmailFactory,
      OutgoingEmailFactory outgoingEmailFactory) {
    this.commentChangeEmailFactory = commentChangeEmailFactory;
    this.mergedChangeEmailFactory = mergedChangeEmailFactory;
    this.replacePatchSetChangeEmailFactory = replacePatchSetChangeEmailFactory;
    this.changeEmailFactory = changeEmailFactory;
    this.addKeyEmailFactory = addKeyEmailFactory;
    this.deleteKeyEmailFactory = deleteKeyEmailFactory;
    this.httpPasswordUpdateEmailFactory = httpPasswordUpdateEmailFactory;
    this.registerNewEmailFactory = registerNewEmailFactory;
    this.outgoingEmailFactory = outgoingEmailFactory;
  }

  @Override
  public ChangeEmailDecorator createAbandonedChangeEmail() {
    return new AbandonedChangeEmailDecorator();
  }

  @Override
  public AttentionSetChangeEmailDecorator createAttentionSetChangeEmail() {
    return new AttentionSetChangeEmailDecoratorImpl();
  }

  @Override
  public CommentChangeEmailDecorator createCommentChangeEmail(
      Project.NameKey project,
      Change.Id changeId,
      ObjectId preUpdateMetaId,
      Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults) {
    return commentChangeEmailFactory.create(
        project, changeId, preUpdateMetaId, postUpdateSubmitRequirementResults);
  }

  @Override
  public DeleteReviewerChangeEmailDecorator createDeleteReviewerChangeEmail() {
    return new DeleteReviewerChangeEmailDecoratorImpl();
  }

  @Override
  public ChangeEmailDecorator createDeleteVoteChangeEmail() {
    return new DeleteVoteChangeEmailDecorator();
  }

  @Override
  public ChangeEmailDecorator createMergedChangeEmail(
      Optional<String> stickyApprovalDiff, List<FileDiffOutput> modifiedFiles) {
    return mergedChangeEmailFactory.create(stickyApprovalDiff, modifiedFiles);
  }

  @Override
  public ReplacePatchSetChangeEmailDecorator createReplacePatchSetChangeEmail(
      Project.NameKey project,
      Change.Id changeId,
      ChangeKind changeKind,
      ObjectId preUpdateMetaId,
      Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults) {
    return replacePatchSetChangeEmailFactory.create(
        project, changeId, changeKind, preUpdateMetaId, postUpdateSubmitRequirementResults);
  }

  @Override
  public ChangeEmailDecorator createRestoredChangeEmail() {
    return new RestoredChangeEmailDecorator();
  }

  @Override
  public ChangeEmailDecorator createRevertedChangeEmail() {
    return new RevertedChangeEmailDecorator();
  }

  @Override
  public StartReviewChangeEmailDecorator createStartReviewChangeEmail() {
    return new StartReviewChangeEmailDecoratorImpl();
  }

  @Override
  public ChangeEmail createChangeEmail(
      Project.NameKey project, Change.Id changeId, ChangeEmailDecorator changeEmailDecorator) {
    return changeEmailFactory.create(project, changeId, changeEmailDecorator);
  }

  @Override
  public EmailDecorator createAddKeyEmail(IdentifiedUser user, AccountSshKey sshKey) {
    return addKeyEmailFactory.create(user, sshKey);
  }

  @Override
  public EmailDecorator createAddKeyEmail(IdentifiedUser user, List<String> gpgKeys) {
    return addKeyEmailFactory.create(user, gpgKeys);
  }

  @Override
  public EmailDecorator createDeleteKeyEmail(IdentifiedUser user, AccountSshKey sshKey) {
    return deleteKeyEmailFactory.create(user, sshKey);
  }

  @Override
  public EmailDecorator createDeleteKeyEmail(IdentifiedUser user, List<String> gpgKeys) {
    return deleteKeyEmailFactory.create(user, gpgKeys);
  }

  @Override
  public EmailDecorator createHttpPasswordUpdateEmail(IdentifiedUser user, String operation) {
    return httpPasswordUpdateEmailFactory.create(user, operation);
  }

  @Override
  public EmailDecorator createInboundEmailRejectionEmail(
      Address to, String threadId, InboundEmailError reason) {
    return new InboundEmailRejectionEmailDecorator(to, threadId, reason);
  }

  @Override
  public RegisterNewEmailDecorator createRegisterNewEmail(String address) {
    return registerNewEmailFactory.create(address);
  }

  @Override
  public OutgoingEmail createOutgoingEmail(String messageClass, EmailDecorator emailDecorator) {
    return outgoingEmailFactory.create(messageClass, emailDecorator);
  }
}
