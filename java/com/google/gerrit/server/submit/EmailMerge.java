// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.FROM_ID;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.MESSAGE_ID;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyEntry;
import com.google.gerrit.proto.Entities.EmailTask.NotifyInput.NotifyHandling;
import com.google.gerrit.proto.Entities.EmailTask.Payload;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class EmailMerge {
  interface Factory {
    EmailMerge create(
        Project.NameKey project,
        Change change,
        IdentifiedUser submitter,
        NotifyResolver.Result notify,
        RepoView repoView,
        String stickyApprovalDiff);
  }

  private final MessageIdGenerator messageIdGenerator;

  private final Project.NameKey project;
  private final Change change;
  private final IdentifiedUser submitter;
  private final NotifyResolver.Result notify;
  private final RepoView repoView;
  private final String stickyApprovalDiff;
  private final EmailTaskDispatcher emailTaskDispatcher;

  @Inject
  EmailMerge(
      MessageIdGenerator messageIdGenerator,
      EmailTaskDispatcher emailTaskDispatcher,
      @Assisted Project.NameKey project,
      @Assisted Change change,
      @Assisted @Nullable IdentifiedUser submitter,
      @Assisted NotifyResolver.Result notify,
      @Assisted RepoView repoView,
      @Assisted String stickyApprovalDiff) {
    this.messageIdGenerator = messageIdGenerator;
    this.project = project;
    this.change = change;
    this.submitter = submitter;
    this.notify = notify;
    this.repoView = repoView;
    this.stickyApprovalDiff = stickyApprovalDiff;
    this.emailTaskDispatcher = emailTaskDispatcher;
  }

  void sendAsync() {
    String messageId =
        messageIdGenerator.fromChangeUpdate(repoView, change.currentPatchSetId()).id();
    EmailTask.Builder emailTaskBuilder =
        EmailTask.newBuilder()
            .setEventType(EmailTask.Type.MERGED)
            .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(project))
            .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(change.getId()))
            .setNotifyInput(getNotify(notify))
            .addHeader(header(MESSAGE_ID, messageId));
    if (submitter != null) {
      emailTaskBuilder.addHeader(header(FROM_ID, submitter.getAccountId().toString()));
      emailTaskBuilder.setOnBehalfOf(
          AccountIdProtoConverter.INSTANCE.toProto(submitter.getRealUser().getAccountId()));
      emailTaskBuilder.setCaller(
          AccountIdProtoConverter.INSTANCE.toProto(submitter.getAccountId()));
    }
    if (!Strings.isNullOrEmpty(stickyApprovalDiff)) {
      emailTaskBuilder.setPayload(
          Payload.newBuilder().setStickyApprovalDiff(stickyApprovalDiff).build());
    }
    emailTaskDispatcher.dispatch(emailTaskBuilder.build());
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

  private EmailTask.Header header(EmailTask.Header.HeaderName headerName, String value) {
    return EmailTask.Header.newBuilder().setName(headerName).setValue(value).build();
  }

  @Override
  public String toString() {
    return "send-email merged";
  }
}
