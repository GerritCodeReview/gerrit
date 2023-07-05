// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A {@link BatchUpdateOp} that can be used to publish draft comments
 *
 * <p>This class uses the {@link PublishCommentUtil} to publish draft comments and fires the
 * necessary event for this.
 */
public class PublishCommentsOp implements BatchUpdateOp {
  private final PatchSetUtil psUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final CommentAdded commentAdded;
  private final DraftCommentsReader draftCommentsReader;
  private final EmailReviewComments.Factory email;
  private final Project.NameKey projectNameKey;
  private final PatchSet.Id psId;
  private final PublishCommentUtil publishCommentUtil;
  private final ChangeMessagesUtil changeMessagesUtil;

  private ObjectId preUpdateMetaId;
  private List<HumanComment> comments = new ArrayList<>();
  private String mailMessage;

  public interface Factory {
    PublishCommentsOp create(PatchSet.Id psId, Project.NameKey projectNameKey);
  }

  @Inject
  public PublishCommentsOp(
      ChangeNotes.Factory changeNotesFactory,
      CommentAdded commentAdded,
      DraftCommentsReader draftCommentsReader,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      PublishCommentUtil publishCommentUtil,
      ChangeMessagesUtil changeMessagesUtil,
      @Assisted PatchSet.Id psId,
      @Assisted Project.NameKey projectNameKey) {
    this.changeNotesFactory = changeNotesFactory;
    this.commentAdded = commentAdded;
    this.draftCommentsReader = draftCommentsReader;
    this.email = email;
    this.psId = psId;
    this.publishCommentUtil = publishCommentUtil;
    this.psUtil = psUtil;
    this.projectNameKey = projectNameKey;
    this.changeMessagesUtil = changeMessagesUtil;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, UnprocessableEntityException, IOException,
          PatchListNotAvailableException, CommentsRejectedException {
    preUpdateMetaId = ctx.getNotes().getMetaId();
    comments =
        draftCommentsReader.getDraftsByChangeAndDraftAuthor(
            ctx.getNotes(), ctx.getUser().getAccountId());

    // PublishCommentsOp should update a separate ChangeUpdate Object than the one used by other ops
    // For example, with the "publish comments on PS upload" workflow,
    // There are 2 ops: ReplaceOp & PublishCommentsOp, where each updates its own ChangeUpdate
    // This is required since
    //   1. a ChangeUpdate has only 1 change message
    //   2. Each ChangeUpdate results in 1 commit in NoteDb
    // We do it this way so that the execution results in 2 different commits in NoteDb
    ChangeUpdate changeUpdate = ctx.getDistinctUpdate(psId);
    publishCommentUtil.publish(ctx, changeUpdate, comments, /* tag= */ null);
    return insertMessage(changeUpdate);
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (Strings.isNullOrEmpty(mailMessage) || comments.isEmpty()) {
      return;
    }
    ChangeNotes changeNotes = changeNotesFactory.createChecked(projectNameKey, psId.changeId());
    PatchSet ps = psUtil.get(changeNotes, psId);
    email
        .create(
            ctx,
            ps,
            preUpdateMetaId,
            mailMessage,
            comments,
            /* patchSetComment= */ null,
            /* labels= */ ImmutableList.of())
        .sendAsync();
    commentAdded.fire(
        ctx.getChangeData(changeNotes),
        ps,
        ctx.getAccount(),
        mailMessage,
        ImmutableMap.of(),
        ImmutableMap.of(),
        ctx.getWhen());
  }

  private boolean insertMessage(ChangeUpdate changeUpdate) {
    StringBuilder buf = new StringBuilder();
    if (comments.size() == 1) {
      buf.append("\n\n(1 comment)");
    } else if (comments.size() > 1) {
      buf.append(String.format("\n\n(%d comments)", comments.size()));
    }
    if (buf.length() == 0) {
      return false;
    }
    mailMessage =
        changeMessagesUtil.setChangeMessage(
            changeUpdate, "Patch Set " + psId.get() + ":" + buf, /* tag= */ null);
    return true;
  }
}
