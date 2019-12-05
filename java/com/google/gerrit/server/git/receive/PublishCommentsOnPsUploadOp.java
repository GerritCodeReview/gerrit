package com.google.gerrit.server.git.receive;

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PublishCommentUtil;
import com.google.gerrit.server.PublishCommentsOp;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;

/*
 This Op is used by the Git Receive commits workflow
 to publish any draft comments when uploading a new patch set
*/
public class PublishCommentsOnPsUploadOp extends PublishCommentsOp {
  private PatchSetUtil psUtil;
  private PublishCommentUtil publishCommentUtil;

  public interface Factory {
    PublishCommentsOnPsUploadOp create(PatchSet.Id psId, ReviewInput in);
  }

  @Inject
  PublishCommentsOnPsUploadOp(
      ChangeMessagesUtil cmUtil,
      CommentAdded commentAdded,
      EmailReviewComments.Factory email,
      PatchSetUtil psUtil,
      PluginSetContext<CommentValidator> commentValidators,
      PublishCommentUtil publishCommentUtil,
      @Assisted PatchSet.Id psId,
      @Assisted ReviewInput in) {
    super(cmUtil, commentAdded, email, commentValidators, psId, in);
    this.psUtil = psUtil;
    this.publishCommentUtil = publishCommentUtil;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws ResourceConflictException, UnprocessableEntityException, IOException,
          PatchListNotAvailableException, CommentsRejectedException {
    user = ctx.getIdentifiedUser();
    changeNotes = ctx.getNotes();
    ps = psUtil.get(ctx.getNotes(), psId);
    boolean dirty = publishCommentUtil.insertComments(ctx, in, user, ps, comments);
    dirty |= insertMessage(ctx);
    return dirty;
  }
}
