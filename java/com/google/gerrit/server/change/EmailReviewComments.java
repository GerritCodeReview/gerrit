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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.CommentsUtil.COMMENT_ORDER;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.LabelVote;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.LabelVoteProtoConverter;
import com.google.gerrit.entities.converter.ObjectIdProtoConverter;
import com.google.gerrit.entities.converter.PatchSetIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.entities.converter.SubmitRequirementResultProtoConverter;
import com.google.gerrit.extensions.api.changes.RecipientType;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;

public class EmailReviewComments {
  public interface Factory {
    // TODO(dborowitz/wyatta): Rationalize these arguments so HTML and text templates are operating
    // on the same set of inputs.
    /**
     * Creates handle for sending email
     *
     * @param postUpdateContext the post update context from the calling BatchUpdateOp
     * @param patchSet patch set corresponding to the top-level op
     * @param preUpdateMetaId the SHA1 to which the notes branch pointed before the update
     * @param message used by text template only. The contents of this message typically include the
     *     "Patch set N" header and "(M comments)".
     * @param comments inline comments.
     * @param patchSetComment used by HTML template only: some quasi-human-generated text. The
     *     contents should *not* include a "Patch set N" header or "(M comments)" footer, as these
     *     will be added automatically in soy in a structured way.
     * @param labels labels applied as part of this review operation.
     */
    EmailReviewComments create(
        PostUpdateContext postUpdateContext,
        PatchSet patchSet,
        ObjectId preUpdateMetaId,
        @Assisted("message") String message,
        List<? extends Comment> comments,
        @Nullable @Assisted("patchSetComment") String patchSetComment,
        List<LabelVote> labels);
  }

  private final EmailTaskDispatcher emailTaskDispatcher;
  private final CurrentUser user;
  private final String messageId;
  private final NotifyResolver.Result notify;
  private final Project.NameKey project;
  private final Change.Id changeId;
  private final PatchSet patchSet;
  private final ObjectId preUpdateMetaId;
  private final String message;
  private final Instant when;
  private final List<? extends Comment> comments;
  private final String patchSetComment;
  private final List<LabelVote> labels;
  private final Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults;

  @Inject
  EmailReviewComments(
      MessageIdGenerator messageIdGenerator,
      EmailTaskDispatcher emailTaskDispatcher,
      @Assisted PostUpdateContext postUpdateContext,
      @Assisted PatchSet patchSet,
      @Assisted ObjectId preUpdateMetaId,
      @Assisted("message") String message,
      @Assisted List<? extends Comment> comments,
      @Nullable @Assisted("patchSetComment") String patchSetComment,
      @Assisted List<LabelVote> labels) {
    this.emailTaskDispatcher = emailTaskDispatcher;

    MessageId messageId;
    try {
      messageId =
          messageIdGenerator.fromChangeUpdateAndReason(
              postUpdateContext.getRepoView(), patchSet.id(), "EmailReviewComments");
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
    this.user = postUpdateContext.getUser().asIdentifiedUser();
    this.messageId = messageId.id();
    this.notify = postUpdateContext.getNotify(changeId);
    this.project = postUpdateContext.getProject();
    this.changeId = changeId;
    this.patchSet = patchSet;
    this.preUpdateMetaId = preUpdateMetaId;
    this.message = message;
    this.when = postUpdateContext.getWhen();
    this.comments = ImmutableList.copyOf(COMMENT_ORDER.sortedCopy(comments));
    this.patchSetComment = patchSetComment;
    this.labels = ImmutableList.copyOf(labels);
    this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
  }

  public void dispatch() {
    EmailTask.Builder emailTaskBuilder =
        EmailTask.newBuilder()
            .setEventType(EmailTask.Type.COMMENTS)
            .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(project))
            .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(changeId))
            .setPatchsetId(PatchSetIdProtoConverter.INSTANCE.toProto(patchSet.id()))
            .setNotifyInput(getNotify(notify))
            .setPreUpdateMetaId(ObjectIdProtoConverter.INSTANCE.toProto(preUpdateMetaId))
            .addHeader(header(HeaderName.FROM_ID, user.getAccountId().toString()))
            .addHeader(header(HeaderName.TIMESTAMP, String.valueOf(when.toEpochMilli())))
            .addHeader(header(HeaderName.MESSAGE_ID, messageId))
            .setPayload(
                Payload.newBuilder()
                    .addAllPostUpdateSubmitRequirementResults(
                        postUpdateSubmitRequirementResults.values().stream()
                            .map(SubmitRequirementResultProtoConverter.INSTANCE::toProto)
                            .collect(Collectors.toList()))
                    .addAllCommentUuids(
                        comments.stream().map(c -> c.key.uuid).collect(Collectors.toList()))
                    .setPatchsetComment(Strings.nullToEmpty(patchSetComment))
                    .addAllLabelVotes(
                        labels.stream()
                            .map(labelVote -> LabelVoteProtoConverter.INSTANCE.toProto(labelVote))
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
