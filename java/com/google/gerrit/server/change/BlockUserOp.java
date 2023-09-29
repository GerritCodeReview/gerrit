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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.mail.EmailFactories.USER_BLOCKED;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.BlockUserChangeEmailDecorator;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoView;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class BlockUserOp extends ReviewerOp {
  public interface Factory {
    BlockUserOp create(Account reviewer, DeleteReviewerInput input);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String blockedUsersGroupName;
  private final GroupCache groupCache;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final EmailFactories emailFactories;
  private final MessageIdGenerator messageIdGenerator;
  private final Account reviewer;
  private final DeleteReviewerInput input;

  private Change change;
  private Set<Account.Id> reviewers;
  private Supplier<Set<Address>> reviewersByEmail;
  private Supplier<String> message;

  @AssistedInject
  BlockUserOp(
      @GerritServerConfig Config config,
      GroupCache groupCache,
      @ServerInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      EmailFactories emailFactories,
      MessageIdGenerator messageIdGenerator,
      @Assisted Account reviewer,
      @Assisted DeleteReviewerInput input) {
    this.blockedUsersGroupName = config.getString("groups", null, "blockedUsersGroup");
    this.groupCache = groupCache;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.emailFactories = emailFactories;
    this.messageIdGenerator = messageIdGenerator;
    this.reviewer = reviewer;
    this.input = input;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, IOException, PermissionBackendException {
    Optional<InternalGroup> group = groupCache.get(AccountGroup.nameKey(blockedUsersGroupName));
    if (group.isEmpty()) {
      logger.atInfo().log(
          "Blocked users group (%s) cannot be find. Blocking user is not possible.");
      return false;
    }

    try {
      GroupDelta groupDelta =
          GroupDelta.builder()
              .setMemberModification(memberIds -> Sets.union(memberIds, Set.of(reviewer.id())))
              .build();
      groupsUpdateProvider.get().updateGroup(group.get().getGroupUUID(), groupDelta);
    } catch (NoSuchGroupException | ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("Blocked users group %s was not updated", blockedUsersGroupName), e);
    }

    // capture context for postUpdate operation
    reviewers = approvalsUtil.getReviewers(ctx.getNotes()).all();
    reviewersByEmail = () -> ctx.getNotes().getReviewersByEmail().all();
    change = ctx.getChange();
    String messageTemplate =
        String.format(
            "User %s was removed from change and moved to blocked users group.",
            AccountTemplateUtil.getAccountTemplate(reviewer.id()));
    message =
        () -> cmUtil.setChangeMessage(ctx, messageTemplate, ChangeMessagesUtil.TAG_BLOCK_USER);

    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    opResult = Result.builder().setBlockedReviewer(reviewer.id()).build();

    NotifyResolver.Result notify = ctx.getNotify(change.getId());
    if (sendEmail) {
      if (input.notify == null
          && change.isWorkInProgress()
          && !reviewers.isEmpty()
          && notify.handling().equals(NotifyHandling.NONE)) {
        // Override NotifyHandling from the context to notify owner if user was blocked form a WIP
        // change.
        notify = notify.withHandling(NotifyHandling.OWNER);
      }
      try {
        emailReviewers(
            ctx.getProject(),
            ctx.getAccountId(),
            Timestamp.from(ctx.getWhen()),
            notify,
            ctx.getRepoView());
      } catch (Exception err) {
        logger.atSevere().withCause(err).log("Cannot email update for change %s", change.getId());
      }
    }
  }

  private void emailReviewers(
      Project.NameKey projectName,
      Account.Id userId,
      Timestamp timestamp,
      NotifyResolver.Result notify,
      RepoView repoView)
      throws EmailException {
    Set<Account.Id> byAccountReviewers =
        reviewers.stream().filter(r -> r.get() != userId.get()).collect(Collectors.toSet());
    if (byAccountReviewers.isEmpty() && reviewersByEmail.get().isEmpty()) {
      // there is nobody left to receive the message so there is no point in creating it
      return;
    }

    BlockUserChangeEmailDecorator blockUserEmail = emailFactories.createBlockUserChangeEmail();
    blockUserEmail.addBlockedUser(reviewer.id());
    ChangeEmail changeEmail =
        emailFactories.createChangeEmail(projectName, change.getId(), blockUserEmail);
    changeEmail.setChangeMessage(message.get(), timestamp.toInstant());
    OutgoingEmail outgoingEmail = emailFactories.createOutgoingEmail(USER_BLOCKED, changeEmail);
    outgoingEmail.setFrom(userId);
    outgoingEmail.setNotify(notify);
    outgoingEmail.setMessageId(
        messageIdGenerator.fromChangeUpdate(repoView, change.currentPatchSetId()));
    outgoingEmail.send();
  }
}
