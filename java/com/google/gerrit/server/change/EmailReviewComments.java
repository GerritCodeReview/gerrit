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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.send.CommentSender;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.update.RepoView;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;

public class EmailReviewComments {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    // TODO(dborowitz/wyatta): Rationalize these arguments so HTML and text templates are operating
    // on the same set of inputs.
    /**
     * @param notify setting for handling notification.
     * @param notes change notes.
     * @param patchSet patch set corresponding to the top-level op
     * @param user user the email should come from.
     * @param message used by text template only: the full ChangeMessage that will go in the
     *     database. The contents of this message typically include the "Patch set N" header and "(M
     *     comments)".
     * @param comments inline comments.
     * @param comments inline comments.
     * @param patchSetComment used by HTML template only: some quasi-human-generated text. The
     *     contents should *not* include a "Patch set N" header or "(M comments)" footer, as these
     *     will be added automatically in soy in a structured way.
     * @param labels labels applied as part of this review operation.
     * @return handle for sending email.
     */
    EmailReviewComments create(
        NotifyResolver.Result notify,
        ChangeNotes notes,
        PatchSet patchSet,
        IdentifiedUser user,
        ChangeMessage message,
        List<? extends Comment> comments,
        String patchSetComment,
        List<LabelVote> labels,
        RepoView repoView);
  }

  private final PatchSetInfoFactory patchSetInfoFactory;
  private final CommentSender.Factory commentSenderFactory;
  private final MessageIdGenerator messageIdGenerator;

  private final NotifyResolver.Result notify;
  private final ChangeNotes notes;
  private final PatchSet patchSet;
  private final IdentifiedUser user;
  private final ChangeMessage message;
  private final List<? extends Comment> comments;
  private final String patchSetComment;
  private final List<LabelVote> labels;
  private final RepoView repoView;

  @Inject
  EmailReviewComments(
      PatchSetInfoFactory patchSetInfoFactory,
      CommentSender.Factory commentSenderFactory,
      MessageIdGenerator messageIdGenerator,
      @Assisted NotifyResolver.Result notify,
      @Assisted ChangeNotes notes,
      @Assisted PatchSet patchSet,
      @Assisted IdentifiedUser user,
      @Assisted ChangeMessage message,
      @Assisted List<? extends Comment> comments,
      @Nullable @Assisted String patchSetComment,
      @Assisted List<LabelVote> labels,
      @Assisted RepoView repoView) {
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.commentSenderFactory = commentSenderFactory;
    this.messageIdGenerator = messageIdGenerator;
    this.notify = notify;
    this.notes = notes;
    this.patchSet = patchSet;
    this.user = user;
    this.message = message;
    this.comments = COMMENT_ORDER.sortedCopy(comments);
    this.patchSetComment = patchSetComment;
    this.labels = labels;
    this.repoView = repoView;
  }

  public void send() {
    try {
      CommentSender emailSender =
          commentSenderFactory.create(notes.getProjectName(), notes.getChangeId());
      emailSender.setFrom(user.getAccountId());
      emailSender.setPatchSet(patchSet, patchSetInfoFactory.get(notes.getProjectName(), patchSet));
      emailSender.setChangeMessage(message.getMessage(), message.getWrittenOn());
      emailSender.setComments(comments);
      emailSender.setPatchSetComment(patchSetComment);
      emailSender.setLabels(labels);
      emailSender.setNotify(notify);
      emailSender.setMessageId(
          messageIdGenerator.fromChangeUpdateAndReason(
              repoView, patchSet.id(), "EmailReviewComments"));
      emailSender.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot email comments for %s", patchSet.id());
    }
  }
}
