// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.receive;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.mail.HtmlParser;
import com.google.gerrit.mail.MailComment;
import com.google.gerrit.mail.MailHeaderParser;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.mail.MailMetadata;
import com.google.gerrit.mail.TextParser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PublishCommentUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.mail.MailFilter;
import com.google.gerrit.server.mail.send.InboundEmailRejectionSender;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A service that can attach the comments from a {@link MailMessage} to a change. */
@Singleton
public class MailProcessor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableMap<MailComment.CommentType, CommentForValidation.CommentType>
      MAIL_COMMENT_TYPE_TO_VALIDATION_TYPE =
          ImmutableMap.of(
              MailComment.CommentType.CHANGE_MESSAGE,
                  CommentForValidation.CommentType.CHANGE_MESSAGE,
              MailComment.CommentType.FILE_COMMENT, CommentForValidation.CommentType.FILE_COMMENT,
              MailComment.CommentType.INLINE_COMMENT,
                  CommentForValidation.CommentType.INLINE_COMMENT);

  private final Emails emails;
  private final InboundEmailRejectionSender.Factory emailRejectionSender;
  private final RetryHelper retryHelper;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final CommentsUtil commentsUtil;
  private final OneOffRequestContext oneOffRequestContext;
  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final Provider<InternalChangeQuery> queryProvider;
  private final DynamicMap<MailFilter> mailFilters;
  private final EmailReviewComments.Factory outgoingMailFactory;
  private final CommentAdded commentAdded;
  private final ApprovalsUtil approvalsUtil;
  private final AccountCache accountCache;
  private final DynamicItem<UrlFormatter> urlFormatter;
  private final PluginSetContext<CommentValidator> commentValidators;

  @Inject
  public MailProcessor(
      Emails emails,
      InboundEmailRejectionSender.Factory emailRejectionSender,
      RetryHelper retryHelper,
      ChangeMessagesUtil changeMessagesUtil,
      CommentsUtil commentsUtil,
      OneOffRequestContext oneOffRequestContext,
      PatchListCache patchListCache,
      PatchSetUtil psUtil,
      Provider<InternalChangeQuery> queryProvider,
      DynamicMap<MailFilter> mailFilters,
      EmailReviewComments.Factory outgoingMailFactory,
      ApprovalsUtil approvalsUtil,
      CommentAdded commentAdded,
      AccountCache accountCache,
      DynamicItem<UrlFormatter> urlFormatter,
      PluginSetContext<CommentValidator> commentValidators) {
    this.emails = emails;
    this.emailRejectionSender = emailRejectionSender;
    this.retryHelper = retryHelper;
    this.changeMessagesUtil = changeMessagesUtil;
    this.commentsUtil = commentsUtil;
    this.oneOffRequestContext = oneOffRequestContext;
    this.patchListCache = patchListCache;
    this.psUtil = psUtil;
    this.queryProvider = queryProvider;
    this.mailFilters = mailFilters;
    this.outgoingMailFactory = outgoingMailFactory;
    this.commentAdded = commentAdded;
    this.approvalsUtil = approvalsUtil;
    this.accountCache = accountCache;
    this.urlFormatter = urlFormatter;
    this.commentValidators = commentValidators;
  }

  /**
   * Parses comments from a {@link MailMessage} and persists them on the change.
   *
   * @param message {@link MailMessage} to process
   */
  public void process(MailMessage message) throws RestApiException, UpdateException {
    retryHelper
        .changeUpdate(
            "processCommentsReceivedByEmail",
            buf -> {
              processImpl(buf, message);
              return null;
            })
        .call();
  }

  private void processImpl(BatchUpdate.Factory buf, MailMessage message)
      throws UpdateException, RestApiException, IOException {
    for (Extension<MailFilter> filter : mailFilters) {
      if (!filter.getProvider().get().shouldProcessMessage(message)) {
        logger.atWarning().log(
            "Message %s filtered by plugin %s %s. Will delete message.",
            message.id(), filter.getPluginName(), filter.getExportName());
        return;
      }
    }

    MailMetadata metadata = MailHeaderParser.parse(message);

    if (!metadata.hasRequiredFields()) {
      logger.atSevere().log(
          "Message %s is missing required metadata, have %s. Will delete message.",
          message.id(), metadata);
      sendRejectionEmail(message, InboundEmailRejectionSender.Error.PARSING_ERROR);
      return;
    }

    Set<Account.Id> accountIds = emails.getAccountFor(metadata.author);

    if (accountIds.size() != 1) {
      logger.atSevere().log(
          "Address %s could not be matched to a unique account. It was matched to %s."
              + " Will delete message.",
          metadata.author, accountIds);

      // We don't want to send an email if no accounts are linked to it.
      if (accountIds.size() > 1) {
        sendRejectionEmail(message, InboundEmailRejectionSender.Error.UNKNOWN_ACCOUNT);
      }
      return;
    }
    Account.Id accountId = accountIds.iterator().next();
    Optional<AccountState> accountState = accountCache.get(accountId);
    if (!accountState.isPresent()) {
      logger.atWarning().log("Mail: Account %s doesn't exist. Will delete message.", accountId);
      return;
    }
    if (!accountState.get().account().isActive()) {
      logger.atWarning().log("Mail: Account %s is inactive. Will delete message.", accountId);
      sendRejectionEmail(message, InboundEmailRejectionSender.Error.INACTIVE_ACCOUNT);
      return;
    }

    persistComments(buf, message, metadata, accountId);
  }

  private void sendRejectionEmail(MailMessage message, InboundEmailRejectionSender.Error reason) {
    try {
      InboundEmailRejectionSender em =
          emailRejectionSender.create(message.from(), message.id(), reason);
      em.send();
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot send email to warn for an error");
    }
  }

  private void persistComments(
      BatchUpdate.Factory buf, MailMessage message, MailMetadata metadata, Account.Id sender)
      throws UpdateException, RestApiException {
    try (ManualRequestContext ctx = oneOffRequestContext.openAs(sender)) {
      List<ChangeData> changeDataList =
          queryProvider.get().byLegacyChangeId(Change.id(metadata.changeNumber));
      if (changeDataList.size() != 1) {
        logger.atSevere().log(
            "Message %s references unique change %s,"
                + " but there are %d matching changes in the index."
                + " Will delete message.",
            message.id(), metadata.changeNumber, changeDataList.size());

        sendRejectionEmail(message, InboundEmailRejectionSender.Error.INTERNAL_EXCEPTION);
        return;
      }
      ChangeData cd = changeDataList.get(0);
      if (existingMessageIds(cd).contains(message.id())) {
        logger.atInfo().log("Message %s was already processed. Will delete message.", message.id());
        return;
      }
      // Get all comments; filter and sort them to get the original list of
      // comments from the outbound email.
      // TODO(hiesel) Also filter by original comment author.
      Collection<Comment> comments =
          cd.publishedComments().stream()
              .filter(c -> (c.writtenOn.getTime() / 1000) == (metadata.timestamp.getTime() / 1000))
              .sorted(CommentsUtil.COMMENT_ORDER)
              .collect(toList());
      Project.NameKey project = cd.project();

      // If URL is not defined, we won't be able to parse line comments. We still attempt to get the
      // other ones.
      String changeUrl =
          urlFormatter
              .get()
              .getChangeViewUrl(cd.project(), cd.getId())
              .orElse("http://gerrit.invalid/");

      List<MailComment> parsedComments;
      if (useHtmlParser(message)) {
        parsedComments = HtmlParser.parse(message, comments, changeUrl);
      } else {
        parsedComments = TextParser.parse(message, comments, changeUrl);
      }

      if (parsedComments.isEmpty()) {
        logger.atWarning().log(
            "Could not parse any comments from %s. Will delete message.", message.id());
        sendRejectionEmail(message, InboundEmailRejectionSender.Error.PARSING_ERROR);
        return;
      }

      ImmutableList<CommentForValidation> parsedCommentsForValidation =
          parsedComments.stream()
              .map(
                  comment ->
                      CommentForValidation.create(
                          MAIL_COMMENT_TYPE_TO_VALIDATION_TYPE.get(comment.getType()),
                          comment.getMessage()))
              .collect(ImmutableList.toImmutableList());
      CommentValidationContext commentValidationCtx =
          CommentValidationContext.builder()
              .changeId(cd.change().getChangeId())
              .project(cd.change().getProject().get())
              .build();
      ImmutableList<CommentValidationFailure> commentValidationFailures =
          PublishCommentUtil.findInvalidComments(
              commentValidationCtx, commentValidators, parsedCommentsForValidation);
      if (!commentValidationFailures.isEmpty()) {
        sendRejectionEmail(message, InboundEmailRejectionSender.Error.COMMENT_REJECTED);
        return;
      }

      Op o = new Op(PatchSet.id(cd.getId(), metadata.patchSet), parsedComments, message.id());
      BatchUpdate batchUpdate = buf.create(project, ctx.getUser(), TimeUtil.nowTs());
      batchUpdate.addOp(cd.getId(), o);
      batchUpdate.execute();
    }
  }

  private class Op implements BatchUpdateOp {
    private final PatchSet.Id psId;
    private final List<MailComment> parsedComments;
    private final String tag;
    private ChangeMessage changeMessage;
    private List<Comment> comments;
    private PatchSet patchSet;
    private ChangeNotes notes;

    private Op(PatchSet.Id psId, List<MailComment> parsedComments, String messageId) {
      this.psId = psId;
      this.parsedComments = parsedComments;
      this.tag = "mailMessageId=" + messageId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws UnprocessableEntityException, PatchListNotAvailableException {
      patchSet = psUtil.get(ctx.getNotes(), psId);
      notes = ctx.getNotes();
      if (patchSet == null) {
        throw new StorageException("patch set not found: " + psId);
      }

      changeMessage = generateChangeMessage(ctx);
      changeMessagesUtil.addChangeMessage(ctx.getUpdate(psId), changeMessage);

      comments = new ArrayList<>();
      for (MailComment c : parsedComments) {
        if (c.getType() == MailComment.CommentType.CHANGE_MESSAGE) {
          continue;
        }
        comments.add(
            persistentCommentFromMailComment(ctx, c, targetPatchSetForComment(ctx, c, patchSet)));
      }
      commentsUtil.putComments(
          ctx.getUpdate(ctx.getChange().currentPatchSetId()), Comment.Status.PUBLISHED, comments);

      return true;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      String patchSetComment = null;
      if (parsedComments.get(0).getType() == MailComment.CommentType.CHANGE_MESSAGE) {
        patchSetComment = parsedComments.get(0).getMessage();
      }
      // Send email notifications
      outgoingMailFactory
          .create(
              ctx.getNotify(notes.getChangeId()),
              notes,
              patchSet,
              ctx.getUser().asIdentifiedUser(),
              changeMessage,
              comments,
              patchSetComment,
              ImmutableList.of())
          .sendAsync();
      // Get previous approvals from this user
      Map<String, Short> approvals = new HashMap<>();
      approvalsUtil
          .byPatchSetUser(
              notes, psId, ctx.getAccountId(), ctx.getRevWalk(), ctx.getRepoView().getConfig())
          .forEach(a -> approvals.put(a.label(), a.value()));
      // Fire Gerrit event. Note that approvals can't be granted via email, so old and new approvals
      // are always the same here.
      commentAdded.fire(
          notes.getChange(),
          patchSet,
          ctx.getAccount(),
          changeMessage.getMessage(),
          approvals,
          approvals,
          ctx.getWhen());
    }

    private ChangeMessage generateChangeMessage(ChangeContext ctx) {
      String changeMsg = "Patch Set " + psId.get() + ":";
      if (parsedComments.get(0).getType() == MailComment.CommentType.CHANGE_MESSAGE) {
        // Add a blank line after Patch Set to follow the default format
        if (parsedComments.size() > 1) {
          changeMsg += "\n\n" + numComments(parsedComments.size() - 1);
        }
        changeMsg += "\n\n" + parsedComments.get(0).getMessage();
      } else {
        changeMsg += "\n\n" + numComments(parsedComments.size());
      }
      return ChangeMessagesUtil.newMessage(ctx, changeMsg, tag);
    }

    private PatchSet targetPatchSetForComment(
        ChangeContext ctx, MailComment mailComment, PatchSet current) {
      if (mailComment.getInReplyTo() != null) {
        return psUtil.get(
            ctx.getNotes(),
            PatchSet.id(ctx.getChange().getId(), mailComment.getInReplyTo().key.patchSetId));
      }
      return current;
    }

    private Comment persistentCommentFromMailComment(
        ChangeContext ctx, MailComment mailComment, PatchSet patchSetForComment)
        throws UnprocessableEntityException, PatchListNotAvailableException {
      String fileName;
      // The patch set that this comment is based on is different if this
      // comment was sent in reply to a comment on a previous patch set.
      Side side;
      if (mailComment.getInReplyTo() != null) {
        fileName = mailComment.getInReplyTo().key.filename;
        side = Side.fromShort(mailComment.getInReplyTo().side);
      } else {
        fileName = mailComment.getFileName();
        side = Side.REVISION;
      }

      Comment comment =
          commentsUtil.newComment(
              ctx,
              fileName,
              patchSetForComment.id(),
              (short) side.ordinal(),
              mailComment.getMessage(),
              false,
              null);

      comment.tag = tag;
      if (mailComment.getInReplyTo() != null) {
        comment.parentUuid = mailComment.getInReplyTo().key.uuid;
        comment.lineNbr = mailComment.getInReplyTo().lineNbr;
        comment.range = mailComment.getInReplyTo().range;
        comment.unresolved = mailComment.getInReplyTo().unresolved;
      }
      CommentsUtil.setCommentCommitId(comment, patchListCache, ctx.getChange(), patchSetForComment);
      return comment;
    }
  }

  private static boolean useHtmlParser(MailMessage m) {
    return !Strings.isNullOrEmpty(m.htmlContent());
  }

  private static String numComments(int numComments) {
    return "(" + numComments + (numComments > 1 ? " comments)" : " comment)");
  }

  private Set<String> existingMessageIds(ChangeData cd) {
    Set<String> existingMessageIds = new HashSet<>();
    cd.messages().stream()
        .forEach(
            m -> {
              String messageId = CommentsUtil.extractMessageId(m.getTag());
              if (messageId != null) {
                existingMessageIds.add(messageId);
              }
            });
    cd.publishedComments().stream()
        .forEach(
            c -> {
              String messageId = CommentsUtil.extractMessageId(c.tag);
              if (messageId != null) {
                existingMessageIds.add(messageId);
              }
            });
    return existingMessageIds;
  }
}
