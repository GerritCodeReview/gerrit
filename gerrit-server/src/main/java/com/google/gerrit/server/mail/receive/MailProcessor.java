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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.change.EmailReviewComments;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.mail.MailFilter;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
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
import com.google.gwtorm.server.OrmException;
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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MailProcessor {
  private static final Logger log = LoggerFactory.getLogger(MailProcessor.class);

  private final Accounts accounts;
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
  private final Provider<String> canonicalUrl;

  @Inject
  public MailProcessor(
      Accounts accounts,
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
      @CanonicalWebUrl Provider<String> canonicalUrl) {
    this.accounts = accounts;
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
    this.canonicalUrl = canonicalUrl;
  }

  /**
   * Parse comments from MailMessage and persist them on the change.
   *
   * @param message MailMessage to process.
   */
  public void process(MailMessage message) throws RestApiException, UpdateException {
    retryHelper.execute(
        buf -> {
          processImpl(buf, message);
          return null;
        });
  }

  private void processImpl(BatchUpdate.Factory buf, MailMessage message)
      throws OrmException, UpdateException, RestApiException, IOException {
    for (DynamicMap.Entry<MailFilter> filter : mailFilters) {
      if (!filter.getProvider().get().shouldProcessMessage(message)) {
        log.warn(
            String.format(
                "Message %s filtered by plugin %s %s. Will delete message.",
                message.id(), filter.getPluginName(), filter.getExportName()));
        return;
      }
    }

    MailMetadata metadata = MetadataParser.parse(message);
    if (!metadata.hasRequiredFields()) {
      log.error(
          String.format(
              "Message %s is missing required metadata, have %s. Will delete message.",
              message.id(), metadata));
      return;
    }

    Set<Account.Id> accountIds = accounts.byEmail(metadata.author);
    if (accountIds.size() != 1) {
      log.error(
          String.format(
              "Address %s could not be matched to a unique account. It was matched to %s. Will delete message.",
              metadata.author, accountIds));
      return;
    }
    Account.Id account = accountIds.iterator().next();
    if (!accountCache.get(account).getAccount().isActive()) {
      log.warn(String.format("Mail: Account %s is inactive. Will delete message.", account));
      return;
    }

    try (ManualRequestContext ctx = oneOffRequestContext.openAs(account)) {
      List<ChangeData> changeDataList =
          queryProvider.get().byLegacyChangeId(new Change.Id(metadata.changeNumber));
      if (changeDataList.size() != 1) {
        log.error(
            String.format(
                "Message %s references unique change %s, but there are %d matching changes in the index. Will delete message.",
                message.id(), metadata.changeNumber, changeDataList.size()));
        return;
      }
      ChangeData cd = changeDataList.get(0);
      if (existingMessageIds(cd).contains(message.id())) {
        log.info("Message " + message.id() + " was already processed. Will delete message.");
        return;
      }
      // Get all comments; filter and sort them to get the original list of
      // comments from the outbound email.
      // TODO(hiesel) Also filter by original comment author.
      Collection<Comment> comments =
          cd.publishedComments()
              .stream()
              .filter(c -> (c.writtenOn.getTime() / 1000) == (metadata.timestamp.getTime() / 1000))
              .sorted(CommentsUtil.COMMENT_ORDER)
              .collect(toList());
      Project.NameKey project = cd.project();
      String changeUrl = canonicalUrl.get() + "#/c/" + cd.getId().get();

      List<MailComment> parsedComments;
      if (useHtmlParser(message)) {
        parsedComments = HtmlParser.parse(message, comments, changeUrl);
      } else {
        parsedComments = TextParser.parse(message, comments, changeUrl);
      }

      if (parsedComments.isEmpty()) {
        log.warn("Could not parse any comments from " + message.id() + ". Will delete message.");
        return;
      }

      Op o = new Op(new PatchSet.Id(cd.getId(), metadata.patchSet), parsedComments, message.id());
      BatchUpdate batchUpdate = buf.create(cd.db(), project, ctx.getUser(), TimeUtil.nowTs());
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
    private ChangeControl changeControl;

    private Op(PatchSet.Id psId, List<MailComment> parsedComments, String messageId) {
      this.psId = psId;
      this.parsedComments = parsedComments;
      this.tag = "mailMessageId=" + messageId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, UnprocessableEntityException {
      changeControl = ctx.getControl();
      patchSet = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      if (patchSet == null) {
        throw new OrmException("patch set not found: " + psId);
      }

      String changeMsg = "Patch Set " + psId.get() + ":";
      if (parsedComments.get(0).type == MailComment.CommentType.CHANGE_MESSAGE) {
        // Add a blank line after Patch Set to follow the default format
        if (parsedComments.size() > 1) {
          changeMsg += "\n\n" + numComments(parsedComments.size() - 1);
        }
        changeMsg += "\n\n" + parsedComments.get(0).message;
      } else {
        changeMsg += "\n\n" + numComments(parsedComments.size());
      }

      changeMessage = ChangeMessagesUtil.newMessage(ctx, changeMsg, tag);
      changeMessagesUtil.addChangeMessage(ctx.getDb(), ctx.getUpdate(psId), changeMessage);
      comments = new ArrayList<>();
      for (MailComment c : parsedComments) {
        if (c.type == MailComment.CommentType.CHANGE_MESSAGE) {
          continue;
        }

        String fileName;
        // The patch set that this comment is based on is different if this
        // comment was sent in reply to a comment on a previous patch set.
        PatchSet psForComment;
        Side side;
        if (c.inReplyTo != null) {
          fileName = c.inReplyTo.key.filename;
          psForComment =
              psUtil.get(
                  ctx.getDb(),
                  ctx.getNotes(),
                  new PatchSet.Id(ctx.getChange().getId(), c.inReplyTo.key.patchSetId));
          side = Side.fromShort(c.inReplyTo.side);
        } else {
          fileName = c.fileName;
          psForComment = patchSet;
          side = Side.REVISION;
        }

        Comment comment =
            commentsUtil.newComment(
                ctx,
                fileName,
                psForComment.getId(),
                (short) side.ordinal(),
                c.message,
                false,
                null);
        comment.tag = tag;
        if (c.inReplyTo != null) {
          comment.parentUuid = c.inReplyTo.key.uuid;
          comment.lineNbr = c.inReplyTo.lineNbr;
          comment.range = c.inReplyTo.range;
          comment.unresolved = c.inReplyTo.unresolved;
        }
        CommentsUtil.setCommentRevId(comment, patchListCache, ctx.getChange(), psForComment);
        comments.add(comment);
      }
      commentsUtil.putComments(
          ctx.getDb(),
          ctx.getUpdate(ctx.getChange().currentPatchSetId()),
          Status.PUBLISHED,
          comments);

      return true;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      String patchSetComment = null;
      if (parsedComments.get(0).type == MailComment.CommentType.CHANGE_MESSAGE) {
        patchSetComment = parsedComments.get(0).message;
      }
      // Send email notifications
      outgoingMailFactory
          .create(
              NotifyHandling.ALL,
              ArrayListMultimap.create(),
              changeControl.getNotes(),
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
          .byPatchSetUser(ctx.getDb(), changeControl, psId, ctx.getAccountId())
          .forEach(a -> approvals.put(a.getLabel(), a.getValue()));
      // Fire Gerrit event. Note that approvals can't be granted via email, so old and new approvals
      // are always the same here.
      commentAdded.fire(
          changeControl.getChange(),
          patchSet,
          ctx.getAccount(),
          changeMessage.getMessage(),
          approvals,
          approvals,
          ctx.getWhen());
    }
  }

  private static boolean useHtmlParser(MailMessage m) {
    return !Strings.isNullOrEmpty(m.htmlContent());
  }

  private static String numComments(int numComments) {
    return "(" + numComments + (numComments > 1 ? " comments)" : " comment)");
  }

  private Set<String> existingMessageIds(ChangeData cd) throws OrmException {
    Set<String> existingMessageIds = new HashSet<>();
    cd.messages()
        .stream()
        .forEach(
            m -> {
              String messageId = CommentsUtil.extractMessageId(m.getTag());
              if (messageId != null) {
                existingMessageIds.add(messageId);
              }
            });
    cd.publishedComments()
        .stream()
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
