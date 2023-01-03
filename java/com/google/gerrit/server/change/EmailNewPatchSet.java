// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ObjectIdProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.entities.converter.SubmitRequirementResultProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.proto.Entities.Change_Kind;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyEntry;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyHandling;
import com.google.gerrit.proto.Entities.EmailTask.Payload;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.mail.send.MessageIdGenerator.MessageId;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;

public class EmailNewPatchSet {
  public interface Factory {
    EmailNewPatchSet create(
        PostUpdateContext postUpdateContext,
        PatchSet patchSet,
        @Nullable String message,
        ImmutableSet<PatchSetApproval> outdatedApprovals,
        @Assisted("reviewers") ImmutableSet<Account.Id> reviewers,
        @Assisted("extraCcs") ImmutableSet<Account.Id> extraCcs,
        ChangeKind changeKind,
        ObjectId preUpdateMetaId);
  }

  private final EmailTaskDispatcher emailTaskDispatcher;

  private final Project.NameKey projectName;
  private final Change.Id changeId;
  private final PatchSet.Id patchSetId;
  private final NotifyResolver.Result notify;
  private final ChangeKind changeKind;
  private final CurrentUser user;
  private final String message;
  private final Instant timestamp;
  private final String messageId;
  private final ImmutableSet<Account.Id> extraReviewers;
  private final ImmutableSet<Account.Id> extraCC;
  private final ImmutableSet<PatchSetApproval> outdatedApprovals;
  private final Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults;
  private final ObjectId preUpdateMetaId;

  @Inject
  EmailNewPatchSet(
      EmailTaskDispatcher emailTaskDispatcher,
      MessageIdGenerator messageIdGenerator,
      @Assisted PostUpdateContext postUpdateContext,
      @Assisted PatchSet patchSet,
      @Nullable @Assisted String message,
      @Assisted ImmutableSet<PatchSetApproval> outdatedApprovals,
      @Assisted("reviewers") ImmutableSet<Account.Id> reviewers,
      @Assisted("extraCcs") ImmutableSet<Account.Id> extraCcs,
      @Assisted ChangeKind changeKind,
      @Assisted ObjectId preUpdateMetaId) {
    this.emailTaskDispatcher = emailTaskDispatcher;

    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdateAndReason(
              postUpdateContext.getRepoView(), patchSet.id(), "EmailReplacePatchSet");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    Change.Id changeId = patchSet.id().changeId();

    // Getting the change data from PostUpdateContext retrieves a cached ChangeData
    // instance. This ChangeData instance has been created when the change was (re)indexed
    // due to the update, and hence has submit requirement results already cached (since
    // (re)indexing triggers the evaluation of the submit requirements).
    Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults =
        postUpdateContext
            .getChangeData(postUpdateContext.getProject(), changeId)
            .submitRequirementsIncludingLegacy();

    this.projectName = postUpdateContext.getProject();
    this.changeId = changeId;
    this.patchSetId = patchSet.id();
    this.notify = postUpdateContext.getNotify(changeId);
    this.user = postUpdateContext.getIdentifiedUser();
    this.message = message;
    this.timestamp = postUpdateContext.getWhen();
    this.changeKind = changeKind;
    this.messageId = messageId.id();
    this.extraReviewers = reviewers;
    this.extraCC = extraCcs;
    this.outdatedApprovals = outdatedApprovals;
    this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
    this.preUpdateMetaId = preUpdateMetaId;
  }

  public void dispatch() {
    EmailTask.Builder emailTaskBuilder =
        EmailTask.newBuilder()
            .setEventType(EmailTask.Type.NEW_PATCHSET)
            .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(projectName))
            .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(changeId))
            .setPatchsetId(PatchSetIdProtoConverter.INSTANCE.toProto(patchSetId))
            .setNotifyInput(getNotify(notify))
            .setPreUpdateMetaId(ObjectIdProtoConverter.INSTANCE.toProto(preUpdateMetaId))
            .addHeader(header(HeaderName.FROM_ID, user.getAccountId().toString()))
            .addHeader(header(HeaderName.TIMESTAMP, String.valueOf(timestamp.toEpochMilli())))
            .addHeader(header(HeaderName.MESSAGE_ID, messageId))
            .addAllExtraReviewers(
                extraReviewers.stream()
                    .map(AccountIdProtoConverter.INSTANCE::toProto)
                    .collect(Collectors.toList()))
            .addAllExtraCc(
                extraCC.stream()
                    .map(AccountIdProtoConverter.INSTANCE::toProto)
                    .collect(Collectors.toList()))
            .setPayload(
                Payload.newBuilder()
                    .setChangeKind(Change_Kind.valueOf(changeKind.name()))
                    .addAllOutdatedApprovals(
                        outdatedApprovals.stream()
                            .map(PatchSetApprovalProtoConverter.INSTANCE::toProto)
                            .collect(Collectors.toList()))
                    .addAllPostUpdateSubmitRequirementResults(
                        postUpdateSubmitRequirementResults.values().stream()
                            .map(SubmitRequirementResultProtoConverter.INSTANCE::toProto)
                            .collect(Collectors.toList()))
                    .build());
    if (message != null) {
      emailTaskBuilder.setMessage(message);
    }
    emailTaskDispatcher.dispatch(emailTaskBuilder.build());
  }

  private EmailTask.Header header(EmailTask.Header.HeaderName headerName, String value) {
    return EmailTask.Header.newBuilder().setName(headerName).setValue(value).build();
  }

  private NotifyInput getNotify(NotifyResolver.Result notify) {
    NotifyInput.Builder builder =
        NotifyInput.newBuilder()
            .setNotifyHandling(NotifyHandling.valueOf(notify.handling().name()));
    for (RecipientType recipientType : notify.accounts().keySet()) {
      notify.accounts().get(recipientType).stream()
          .forEach(
              a ->
                  builder.addNotifyEntry(
                      NotifyEntry.newBuilder()
                          .setAccount(AccountIdProtoConverter.INSTANCE.toProto(a))
                          .setRecipientType(EmailTask.RecipientType.valueOf(recipientType.name()))
                          .build()));
    }
    return builder.build();
  }
}
