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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentSource;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PublishCommentUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;

@Singleton
public class CreateDraftComment implements RestModifyView<RevisionResource, DraftInput> {
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PluginSetContext<CommentValidator> commentValidators;

  @Inject
  CreateDraftComment(
      BatchUpdate.Factory updateFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      ChangeNotes.Factory changeNotesFactory,
      PluginSetContext<CommentValidator> commentValidators) {
    this.updateFactory = updateFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.commentValidators = commentValidators;
  }

  @Override
  public Response<CommentInfo> apply(RevisionResource rsrc, DraftInput in)
      throws RestApiException, UpdateException, PermissionBackendException {
    if (Strings.isNullOrEmpty(in.path)) {
      throw new BadRequestException("path must be non-empty");
    } else if (in.message == null || in.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (in.path.equals(PATCHSET_LEVEL)
        && (in.side != null || in.range != null || in.line != null)) {
      throw new BadRequestException("patchset-level comments can't have side, range, or line");
    } else if (in.line != null && in.line < 0) {
      throw new BadRequestException("line must be >= 0");
    } else if (in.line != null && in.range != null && in.line != in.range.endLine) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    } else if (in.inReplyTo != null
        && !commentsUtil.getPublishedHumanComment(rsrc.getNotes(), in.inReplyTo).isPresent()
        && !commentsUtil.getRobotComment(rsrc.getNotes(), in.inReplyTo).isPresent()) {
      throw new BadRequestException(
          String.format("Invalid inReplyTo, comment %s not found", in.inReplyTo));
    }
    validateDraftComment(rsrc, in, changeNotesFactory, commentValidators, commentsUtil);
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.now())) {
        Op op = new Op(rsrc.getPatchSet().id(), in);
        bu.addOp(rsrc.getChange().getId(), op);
        bu.execute();
        return Response.created(
            commentJson.get().setFillAccounts(false).newHumanCommentFormatter().format(op.comment));
      }
    }
  }

  static void validateDraftComment(
      RevisionResource rsrc,
      DraftInput in,
      ChangeNotes.Factory changeNotesFactory,
      PluginSetContext<CommentValidator> commentValidators,
      CommentsUtil commentsUtil)
      throws BadRequestException {
    HumanComment comment =
        createDraftComment(
            changeNotesFactory.create(rsrc.getProject(), rsrc.getChange().getId()),
            rsrc.getUser(),
            TimeUtil.now(),
            in,
            rsrc.getChange(),
            rsrc.getPatchSet(),
            commentsUtil);

    CommentValidationContext ctx =
        CommentValidationContext.create(
            rsrc.getChange().getChangeId(),
            rsrc.getChange().getProject().get(),
            rsrc.getChange().getDest().branch());

    ImmutableList<CommentValidationFailure> validationFailures =
        PublishCommentUtil.findInvalidComments(
            ctx,
            commentValidators,
            ImmutableList.of(
                CommentForValidation.create(
                    CommentSource.HUMAN,
                    comment.lineNbr > 0 ? CommentType.INLINE_COMMENT : CommentType.FILE_COMMENT,
                    comment.message,
                    comment.message.length())));

    if (!validationFailures.isEmpty()) {
      throw new BadRequestException(
          String.format(
              "Found an invalid draft comment after validation: %s",
              validationFailures.stream()
                  .map(CommentValidationFailure::getMessage)
                  .collect(Collectors.toList())),
          new CommentsRejectedException(validationFailures));
    }
  }

  private static HumanComment createDraftComment(
      ChangeNotes notes,
      CurrentUser user,
      Instant when,
      DraftInput draftInput,
      Change change,
      PatchSet ps,
      CommentsUtil commentsUtil) {
    String parentUuid = Url.decode(draftInput.inReplyTo);

    HumanComment comment =
        commentsUtil.newHumanComment(
            notes,
            user,
            when,
            draftInput.path,
            ps.id(),
            draftInput.side(),
            draftInput.message.trim(),
            draftInput.unresolved,
            parentUuid);
    comment.setLineNbrAndRange(draftInput.line, draftInput.range);
    comment.tag = draftInput.tag;

    commentsUtil.setCommentCommitId(comment, change, ps);
    return comment;
  }

  private class Op implements BatchUpdateOp {
    private final PatchSet.Id psId;
    private final DraftInput in;

    private HumanComment comment;

    private Op(PatchSet.Id psId, DraftInput in) {
      this.psId = psId;
      this.in = in;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws ResourceNotFoundException, UnprocessableEntityException {
      PatchSet ps = psUtil.get(ctx.getNotes(), psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }

      comment =
          createDraftComment(
              ctx.getNotes(), ctx.getUser(), ctx.getWhen(), in, ctx.getChange(), ps, commentsUtil);

      commentsUtil.putHumanComments(
          ctx.getUpdate(psId), HumanComment.Status.DRAFT, Collections.singleton(comment));
      return true;
    }
  }
}
