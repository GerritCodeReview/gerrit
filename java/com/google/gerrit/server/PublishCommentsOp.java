package com.google.gerrit.server;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.util.LabelVote;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PublishCommentsOp implements BatchUpdateOp {
  public static final String START_REVIEW_MESSAGE = "This change is ready for review.";

  protected ChangeNotes changeNotes;
  protected List<Comment> comments = new ArrayList<>();
  protected IdentifiedUser user;
  protected PatchSet ps;
  protected PatchSet.Id psId;
  protected ReviewInput in;
  protected List<LabelVote> labelDelta = new ArrayList<>();
  protected Map<String, Short> approvals = new HashMap<>();
  protected Map<String, Short> oldApprovals = new HashMap<>();

  private ChangeMessage message;
  private ChangeMessagesUtil cmUtil;
  private CommentAdded commentAdded;
  private EmailReviewComments.Factory email;
  private PluginSetContext<CommentValidator> commentValidators;

  public PublishCommentsOp(
      ChangeMessagesUtil cmUtil,
      CommentAdded commentAdded,
      EmailReviewComments.Factory email,
      PluginSetContext<CommentValidator> commentValidators,
      PatchSet.Id psId,
      ReviewInput in) {
    this.cmUtil = cmUtil;
    this.commentAdded = commentAdded;
    this.email = email;
    this.commentValidators = commentValidators;
    this.psId = psId;
    this.in = in;
  }

  @Override
  public void postUpdate(Context ctx) {
    if (message == null) {
      return;
    }
    NotifyResolver.Result notify = ctx.getNotify(changeNotes.getChangeId());
    if (notify.shouldNotify()) {
      email
          .create(notify, changeNotes, ps, user, message, comments, in.message, labelDelta)
          .sendAsync();
    }
    commentAdded.fire(
        changeNotes.getChange(),
        ps,
        user.state(),
        message.getMessage(),
        approvals,
        oldApprovals,
        ctx.getWhen());
  }

  protected boolean insertMessage(ChangeContext ctx) throws CommentsRejectedException {
    String msg = Strings.nullToEmpty(in.message).trim();

    StringBuilder buf = new StringBuilder();
    for (LabelVote d : labelDelta) {
      buf.append(" ").append(d.format());
    }
    if (comments.size() == 1) {
      buf.append("\n\n(1 comment)");
    } else if (comments.size() > 1) {
      buf.append(String.format("\n\n(%d comments)", comments.size()));
    }
    if (!msg.isEmpty()) {
      ImmutableList<CommentValidationFailure> messageValidationFailure =
          PublishCommentUtil.findInvalidComments(
              commentValidators,
              ImmutableList.of(
                  CommentForValidation.create(
                      CommentForValidation.CommentType.CHANGE_MESSAGE, msg)));
      if (!messageValidationFailure.isEmpty()) {
        throw new CommentsRejectedException(messageValidationFailure);
      }
      buf.append("\n\n").append(msg);
    } else if (in.ready) {
      buf.append("\n\n" + START_REVIEW_MESSAGE);
    }
    if (buf.length() == 0) {
      return false;
    }

    message =
        ChangeMessagesUtil.newMessage(
            psId, user, ctx.getWhen(), "Patch Set " + psId.get() + ":" + buf, in.tag);
    cmUtil.addChangeMessage(ctx.getUpdate(psId), message);
    return true;
  }
}
