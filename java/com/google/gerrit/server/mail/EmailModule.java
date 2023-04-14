// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.mail.send.AbandonedChangeEmailDecorator;
import com.google.gerrit.server.mail.send.AddKeySender;
import com.google.gerrit.server.mail.send.AttentionSetChangeEmailDecorator;
import com.google.gerrit.server.mail.send.AttentionSetChangeEmailDecorator.AttentionSetChange;
import com.google.gerrit.server.mail.send.ChangeEmailNew;
import com.google.gerrit.server.mail.send.ChangeEmailNewFactory;
import com.google.gerrit.server.mail.send.CommentChangeEmail;
import com.google.gerrit.server.mail.send.CommentSender;
import com.google.gerrit.server.mail.send.CreateChangeSender;
import com.google.gerrit.server.mail.send.DeleteKeySender;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.mail.send.DeleteVoteSender;
import com.google.gerrit.server.mail.send.EmailArguments;
import com.google.gerrit.server.mail.send.HttpPasswordUpdateSender;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.mail.send.ModifyReviewerSender;
import com.google.gerrit.server.mail.send.OutgoingEmailNew;
import com.google.gerrit.server.mail.send.OutgoingEmailNewFactory;
import com.google.gerrit.server.mail.send.RegisterNewEmailSender;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.mail.send.RestoredSender;
import com.google.gerrit.server.mail.send.RevertedSender;
import com.google.inject.Inject;

public class EmailModule extends FactoryModule {
  @Override
  protected void configure() {
    factory(AddKeySender.Factory.class);
    factory(ModifyReviewerSender.Factory.class);
    factory(CommentSender.Factory.class);
    factory(CreateChangeSender.Factory.class);
    factory(DeleteKeySender.Factory.class);
    factory(DeleteReviewerSender.Factory.class);
    factory(DeleteVoteSender.Factory.class);
    factory(HttpPasswordUpdateSender.Factory.class);
    factory(MergedSender.Factory.class);
    factory(RegisterNewEmailSender.Factory.class);
    factory(ReplacePatchSetSender.Factory.class);
    factory(RestoredSender.Factory.class);
    factory(RevertedSender.Factory.class);
  }

  public static class AbandonedChangeEmailFactories {
    private final EmailArguments args;
    private final ChangeEmailNewFactory changeEmailFactory;
    private final OutgoingEmailNewFactory outgoingEmailFactory;
    private final AbandonedChangeEmailDecorator abandonedChangeEmailDecorator;

    @Inject
    public AbandonedChangeEmailFactories(
        EmailArguments args,
        ChangeEmailNewFactory changeEmailFactory,
        OutgoingEmailNewFactory outgoingEmailFactory,
        AbandonedChangeEmailDecorator abandonedChangeEmailDecorator) {
      this.args = args;
      this.changeEmailFactory = changeEmailFactory;
      this.outgoingEmailFactory = outgoingEmailFactory;
      this.abandonedChangeEmailDecorator = abandonedChangeEmailDecorator;
    }

    public ChangeEmailNew createChangeEmail(Project.NameKey project, Change.Id changeId) {
      return changeEmailFactory.create(
          args.newChangeData(project, changeId), abandonedChangeEmailDecorator);
    }

    public OutgoingEmailNew createEmail(ChangeEmailNew changeEmail) {
      return outgoingEmailFactory.create("abandon", changeEmail);
    }
  }

  public static class AttentionSetChangeEmailFactories {
    private final EmailArguments args;
    private final ChangeEmailNewFactory changeEmailFactory;
    private final OutgoingEmailNewFactory outgoingEmailFactory;

    @Inject
    public AttentionSetChangeEmailFactories(
        EmailArguments args,
        ChangeEmailNewFactory changeEmailFactory,
        OutgoingEmailNewFactory outgoingEmailFactory) {
      this.args = args;
      this.changeEmailFactory = changeEmailFactory;
      this.outgoingEmailFactory = outgoingEmailFactory;
    }

    public AttentionSetChangeEmailDecorator createAttentionSetChangeEmail() {
      return new AttentionSetChangeEmailDecorator();
    }

    public ChangeEmailNew createChangeEmail(
        Project.NameKey project,
        Change.Id changeId,
        AttentionSetChangeEmailDecorator attentionSetChangeEmailDecorator) {
      return changeEmailFactory.create(
          args.newChangeData(project, changeId), attentionSetChangeEmailDecorator);
    }

    public OutgoingEmailNew createEmail(
        AttentionSetChange attentionSetChange, ChangeEmailNew changeEmail) {
      if (AttentionSetChange.USER_ADDED.equals(attentionSetChange)) {
        return outgoingEmailFactory.create("addToAttentionSet", changeEmail);
      } else {
        return outgoingEmailFactory.create("removeFromAttentionSet", changeEmail);
      }
    }
  }

  public static class CommentChangeEmailFactories {
    private final EmailArguments args;
    private final CommentChangeEmailFactory commentChangeEmailFactory;
    private final ChangeEmailNewFactory changeEmailFactory;
    private final OutgoingEmailNewFactory outgoingEmailFactory;

    @Inject
    public CommentChangeEmailFactories(
        EmailArguments args,
        CommentChangeEmailFactory commentChangeEmailFactory,
        ChangeEmailNewFactory changeEmailFactory,
        OutgoingEmailNewFactory outgoingEmailFactory) {
      this.args = args;
      this.commentChangeEmailFactory = commentChangeEmailFactory;
      this.changeEmailFactory = changeEmailFactory;
      this.outgoingEmailFactory = outgoingEmailFactory;
    }

    public CommentChangeEmail createCommentChangeEmail() {
      return commentChangeEmailFactory.create();
    }

    public ChangeEmailNew createChangeEmail(
        Project.NameKey project,
        Change.Id changeId,
        CommentChangeEmail commentChangeEmail) {
      return changeEmailFactory.create(
          args.newChangeData(project, changeId), commentChangeEmail);
    }

    public OutgoingEmailNew createEmail(ChangeEmailNew changeEmail) {
      return outgoingEmailFactory.create("comment", changeEmail);
    }
  }
}
