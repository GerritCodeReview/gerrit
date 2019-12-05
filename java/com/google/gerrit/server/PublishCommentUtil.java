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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.CommentsUtil.setCommentCommitId;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Comment.Status;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class PublishCommentUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final CommentsUtil commentsUtil;
  private PluginSetContext<CommentValidator> commentValidators;

  @Inject
  PublishCommentUtil(
      CommentsUtil commentsUtil,
      PatchListCache patchListCache,
      PatchSetUtil psUtil,
      PluginSetContext<CommentValidator> commentValidators) {
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.commentValidators = commentValidators;
  }

  public void publish(
      ChangeContext ctx,
      PatchSet.Id psId,
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
      draftComment.writtenOn = ctx.getWhen();
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
    commentsUtil.putComments(ctx.getUpdate(psId), Status.PUBLISHED, commentsToPublish);
  }

  public boolean insertComments(
      ChangeContext ctx, ReviewInput in, IdentifiedUser user, PatchSet ps, List<Comment> comments)
      throws UnprocessableEntityException, PatchListNotAvailableException,
          CommentsRejectedException {
    PatchSet.Id psId = ps.id();
    Map<String, List<CommentInput>> inputComments = in.comments;
    if (inputComments == null) {
      inputComments = Collections.emptyMap();
    }

    // HashMap instead of Collections.emptyMap() avoids warning about remove() on immutable
    // object.
    Map<String, Comment> drafts = new HashMap<>();
    // If there are inputComments we need the deduplication loop below, so we have to read (and
    // publish) drafts here.
    if (!inputComments.isEmpty() || in.drafts != DraftHandling.KEEP) {
      if (in.drafts == DraftHandling.PUBLISH_ALL_REVISIONS) {
        drafts = changeDrafts(ctx, in, user);
      } else {
        drafts = patchSetDrafts(ctx, user, psId);
      }
    }

    // This will be populated with Comment-s created from inputComments.
    List<Comment> toPublish = new ArrayList<>();

    Set<CommentSetEntry> existingComments =
        in.omitDuplicateComments ? readExistingComments(ctx) : Collections.emptySet();

    // Deduplication:
    // - Ignore drafts with the same ID as an inputComment here. These are deleted later.
    // - Swallow comments that already exist.
    for (Map.Entry<String, List<CommentInput>> entry : inputComments.entrySet()) {
      String path = entry.getKey();
      for (CommentInput inputComment : entry.getValue()) {
        Comment comment = drafts.remove(Url.decode(inputComment.id));
        if (comment == null) {
          String parent = Url.decode(inputComment.inReplyTo);
          comment =
              commentsUtil.newComment(
                  ctx,
                  path,
                  psId,
                  inputComment.side(),
                  inputComment.message,
                  inputComment.unresolved,
                  parent);
        } else {
          // In ChangeUpdate#putComment() the draft with the same ID will be deleted.
          comment.writtenOn = ctx.getWhen();
          comment.side = inputComment.side();
          comment.message = inputComment.message;
        }

        setCommentCommitId(comment, patchListCache, ctx.getChange(), ps);
        comment.setLineNbrAndRange(inputComment.line, inputComment.range);
        comment.tag = in.tag;
        if (existingComments.contains(CommentSetEntry.create(comment))) {
          continue;
        }
        toPublish.add(comment);
      }
    }

    switch (in.drafts) {
      case PUBLISH:
      case PUBLISH_ALL_REVISIONS:
        validateComments(Streams.concat(drafts.values().stream(), toPublish.stream()));
        publish(ctx, psId, drafts.values(), in.tag);
        comments.addAll(drafts.values());
        break;
      case KEEP:
      default:
        validateComments(toPublish.stream());
        break;
    }
    ChangeUpdate changeUpdate = ctx.getUpdate(psId);
    commentsUtil.putComments(changeUpdate, Comment.Status.PUBLISHED, toPublish);
    comments.addAll(toPublish);
    return !toPublish.isEmpty();
  }

  private Map<String, Comment> changeDrafts(
      ChangeContext ctx, ReviewInput in, IdentifiedUser user) {
    return commentsUtil.draftByChangeAuthor(ctx.getNotes(), user.getAccountId()).stream()
        .collect(
            Collectors.toMap(
                c -> c.key.uuid,
                c -> {
                  c.tag = in.tag;
                  return c;
                }));
  }

  private Map<String, Comment> patchSetDrafts(
      ChangeContext ctx, IdentifiedUser user, PatchSet.Id psId) {
    return commentsUtil.draftByPatchSetAuthor(psId, user.getAccountId(), ctx.getNotes()).stream()
        .collect(Collectors.toMap(c -> c.key.uuid, c -> c));
  }

  private Set<CommentSetEntry> readExistingComments(ChangeContext ctx) {
    return commentsUtil.publishedByChange(ctx.getNotes()).stream()
        .map(CommentSetEntry::create)
        .collect(toSet());
  }

  private void validateComments(Stream<Comment> comments) throws CommentsRejectedException {
    ImmutableList<CommentForValidation> draftsForValidation =
        comments
            .map(
                comment ->
                    CommentForValidation.create(
                        comment.lineNbr > 0
                            ? CommentForValidation.CommentType.INLINE_COMMENT
                            : CommentForValidation.CommentType.FILE_COMMENT,
                        comment.message))
            .collect(toImmutableList());
    ImmutableList<CommentValidationFailure> draftValidationFailures =
        PublishCommentUtil.findInvalidComments(commentValidators, draftsForValidation);
    if (!draftValidationFailures.isEmpty()) {
      throw new CommentsRejectedException(draftValidationFailures);
    }
  }

  private static PatchSet.Id psId(ChangeNotes notes, Comment c) {
    return PatchSet.id(notes.getChangeId(), c.key.patchSetId);
  }

  /**
   * Helper to run the specified set of {@link CommentValidator}-s on the specified comments.
   *
   * @return See {@link CommentValidator#validateComments(ImmutableList)}.
   */
  public static ImmutableList<CommentValidationFailure> findInvalidComments(
      PluginSetContext<CommentValidator> commentValidators,
      ImmutableList<CommentForValidation> commentsForValidation) {
    ImmutableList.Builder<CommentValidationFailure> commentValidationFailures =
        new ImmutableList.Builder<>();
    commentValidators.runEach(
        listener ->
            commentValidationFailures.addAll(listener.validateComments(commentsForValidation)));
    return commentValidationFailures.build();
  }

  @AutoValue
  abstract static class CommentSetEntry {
    private static CommentSetEntry create(
        String filename,
        int patchSetId,
        Integer line,
        Side side,
        HashCode message,
        Comment.Range range) {
      return new AutoValue_PublishCommentUtil_CommentSetEntry(
          filename, patchSetId, line, side, message, range);
    }

    public static CommentSetEntry create(Comment comment) {
      return create(
          comment.key.filename,
          comment.key.patchSetId,
          comment.lineNbr,
          Side.fromShort(comment.side),
          Hashing.murmur3_128().hashString(comment.message, UTF_8),
          comment.range);
    }

    abstract String filename();

    abstract int patchSetId();

    @Nullable
    abstract Integer line();

    abstract Side side();

    abstract HashCode message();

    @Nullable
    abstract Comment.Range range();
  }
}
