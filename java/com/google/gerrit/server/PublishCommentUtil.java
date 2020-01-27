// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Comment.Status;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class PublishCommentUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final CommentsUtil commentsUtil;

  @Inject
  PublishCommentUtil(
      CommentsUtil commentsUtil, PatchListCache patchListCache, PatchSetUtil psUtil) {
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
  }

  public void publish(
      ChangeContext ctx,
      ChangeUpdate changeUpdate,
      Timestamp ts,
      Collection<Comment> draftComments,
      @Nullable String tag) {
    ChangeNotes notes = ctx.getNotes();
    checkArgument(notes != null);
    if (draftComments.isEmpty()) {
      return;
    }

    Map<PatchSet.Id, PatchSet> patchSets =
        psUtil.getAsMap(notes, draftComments.stream().map(d -> psId(notes, d)).collect(toSet()));
    Set<Comment> commentsToPublish = new HashSet<>();
    for (Comment draftComment : draftComments) {
      PatchSet.Id psIdOfDraftComment = psId(notes, draftComment);
      PatchSet ps = patchSets.get(psIdOfDraftComment);
      if (ps == null) {
        // This can happen if changes with the same numeric ID exist:
        // - change 12345 has 3 patch sets in repo X
        // - another change 12345 has 7 patch sets in repo Y
        // - the user saves a draft comment on patch set 6 of the change in repo Y
        // - this draft comment gets stored in:
        //   AllUsers -> refs/draft-comments/45/12345/<account-id>
        // - when posting a review with draft handling PUBLISH_ALL_REVISIONS on the change in
        //   repo X, the draft comments are loaded from
        //   AllUsers -> refs/draft-comments/45/12345/<account-id>, including the draft
        //   comment that was saved for patch set 6 of the change in repo Y
        // - patch set 6 does not exist for the change in repo x, hence we get null for the patch
        //   set here
        // Instead of failing hard (and returning an Internal Server Error) to the caller,
        // just ignore that comment.
        // Gerrit ensures that numeric change IDs are unique, but you can get duplicates if
        // change refs of one repo are copied/pushed to another repo on the same host (this
        // should never be done, but we know it happens).
        logger.atWarning().log(
            "Ignoring draft comment %s on non existing patch set %s (repo = %s)",
            draftComment, psIdOfDraftComment, notes.getProjectName());
        continue;
      }
      draftComment.writtenOn = ts;
      draftComment.tag = tag;
      // Draft may have been created by a different real user; copy the current real user. (Only
      // applies to X-Gerrit-RunAs, since modifying drafts via on_behalf_of is not allowed.)
      ctx.getUser().updateRealAccountId(draftComment::setRealAuthor);
      try {
        CommentsUtil.setCommentCommitId(draftComment, patchListCache, notes.getChange(), ps);
      } catch (PatchListNotAvailableException e) {
        throw new StorageException(e);
      }
      commentsToPublish.add(draftComment);
    }
    commentsUtil.putComments(changeUpdate, Status.PUBLISHED, commentsToPublish);
  }

  private static PatchSet.Id psId(ChangeNotes notes, Comment c) {
    return PatchSet.id(notes.getChangeId(), c.key.patchSetId);
  }

  /**
   * Helper to run the specified set of {@link CommentValidator}-s on the specified comments.
   *
   * @return See {@link CommentValidator#validateComments(CommentValidationContext,ImmutableList)}.
   */
  public static ImmutableList<CommentValidationFailure> findInvalidComments(
      CommentValidationContext ctx,
      PluginSetContext<CommentValidator> commentValidators,
      ImmutableList<CommentForValidation> commentsForValidation) {
    ImmutableList.Builder<CommentValidationFailure> commentValidationFailures =
        new ImmutableList.Builder<>();
    commentValidators.runEach(
        validator ->
            commentValidationFailures.addAll(
                validator.validateComments(ctx, commentsForValidation)));
    return commentValidationFailures.build();
  }
}
