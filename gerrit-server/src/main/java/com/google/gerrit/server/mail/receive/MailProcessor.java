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

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A service that can attach the comments from a {@link MailMessage} to a change. */
@Singleton
public class MailProcessor {
  private static final Logger log = LoggerFactory.getLogger(MailProcessor.class.getName());

  private final AccountByEmailCache accountByEmailCache;
  private final BatchUpdate.Factory buf;
  private final ChangeMessagesUtil changeMessagesUtil;
  private final CommentsUtil commentsUtil;
  private final OneOffRequestContext oneOffRequestContext;
  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<ReviewDb> reviewDb;
  private final Provider<String> canonicalUrl;

  @Inject
  public MailProcessor(
      AccountByEmailCache accountByEmailCache,
      BatchUpdate.Factory buf,
      ChangeMessagesUtil changeMessagesUtil,
      CommentsUtil commentsUtil,
      OneOffRequestContext oneOffRequestContext,
      PatchListCache patchListCache,
      PatchSetUtil psUtil,
      Provider<InternalChangeQuery> queryProvider,
      Provider<ReviewDb> reviewDb,
      @CanonicalWebUrl Provider<String> canonicalUrl) {
    this.accountByEmailCache = accountByEmailCache;
    this.buf = buf;
    this.changeMessagesUtil = changeMessagesUtil;
    this.commentsUtil = commentsUtil;
    this.oneOffRequestContext = oneOffRequestContext;
    this.patchListCache = patchListCache;
    this.psUtil = psUtil;
    this.queryProvider = queryProvider;
    this.reviewDb = reviewDb;
    this.canonicalUrl = canonicalUrl;
  }

  /**
   * Parses comments from a {@link MailMessage} and persists them on the change.
   *
   * @param message {@link MailMessage} to process
   * @throws OrmException indicating a failure on the persistence layer
   */
  public void process(MailMessage message) throws OrmException {
    MailMetadata metadata = MetadataParser.parse(message);
    if (!metadata.hasRequiredFields()) {
      log.error(
          String.format(
              "Mail: Message %s is missing required metadata, have %s. Will delete message.",
              message.id(), metadata));
      return;
    }

    Set<Account.Id> accounts = accountByEmailCache.get(metadata.author);
    if (accounts.size() != 1) {
      log.error(
          String.format(
              "Mail: Address %s could not be matched to a unique account. It was matched to %s. Will delete message.",
              metadata.author, accounts));
      return;
    }
    Account.Id account = accounts.iterator().next();
    if (!reviewDb.get().accounts().get(account).isActive()) {
      log.warn(String.format("Mail: Account %s is inactive. Will delete message.", account));
      return;
    }

    process(message, metadata, sender);
  }

  private void process(MailMessage message, MailMetadata metadata, Account.Id sender)
      throws OrmException {
    try (ManualRequestContext ctx = oneOffRequestContext.openAs(account)) {
      ChangeData cd =
          queryProvider.get().setLimit(1).byKey(Change.Key.parse(metadata.changeId)).get(0);
      if (existingMessageIds(cd).contains(message.id())) {
        log.info("Mail: Message " + message.id() + " was already processed. Will delete message.");
        return;
      }
      // Get all comments; filter and sort them to get the original list of
      // comments from the outbound email.
      Collection<Comment> comments =
          cd.publishedComments()
              .stream()
              .filter(c -> (c.writtenOn.getTime() / 1000) == (metadata.timestamp.getTime() / 1000))
              .sorted(CommentsUtil.COMMENT_ORDER)
              .collect(Collectors.toList());
      Project.NameKey project = cd.project();
      String changeUrl = canonicalUrl.get() + "#/c/" + cd.getId().get();

      List<MailComment> parsedComments;
      if (useHtmlParser(message)) {
        parsedComments = HtmlParser.parse(message, comments, changeUrl);
      } else {
        parsedComments = TextParser.parse(message, comments, changeUrl);
      }

      if (parsedComments.isEmpty()) {
        log.warn(
            String.format(
                "Mail: Could not parse any comments from %s. Will delete message.", message.id()));
        return;
      }

      Op o = new Op(new PatchSet.Id(cd.getId(), metadata.patchSet), parsedComments, message.id());
      BatchUpdate batchUpdate = buf.create(cd.db(), project, ctx.getUser(), TimeUtil.nowTs());
      batchUpdate.addOp(cd.getId(), o);
      try {
        batchUpdate.execute();
      } catch (UpdateException | RestApiException e) {
        throw new OrmException(e);
      }
    }
  }

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;
    private final List<MailComment> parsedComments;
    private final String tag;

    private Op(PatchSet.Id psId, List<MailComment> parsedComments, String messageId) {
      this.psId = psId;
      this.parsedComments = parsedComments;
      this.tag = "mailMessageId=" + messageId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException {
      PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      if (ps == null) {
        throw new OrmException("patch set not found: " + psId);
      }

      changeMessagesUtil.addChangeMessage(
          ctx.getDb(), ctx.getUpdate(psId), generateChangeMessage(ctx));

      List<Comment> comments = new ArrayList<>();
      for (MailComment mailComment : parsedComments) {
        if (mailComment.type == MailComment.CommentType.CHANGE_MESSAGE) {
          continue;
        }

        PatchSet patchSetForComment = targetPatchSetForComment(ctx, mailComment, ps);
        Comment comment = persistentCommentFromMailComment(ctx, mailComment, patchSetForComment);
        comments.add(comment);
      }
      commentsUtil.putComments(
          ctx.getDb(),
          ctx.getUpdate(ctx.getChange().currentPatchSetId()),
          Status.PUBLISHED,
          comments);

      return true;
    }

    private ChangeMessage generateChangeMessage(ChangeContext ctx) throws OrmException {
      String changeMsg = "Patch Set " + psId.get() + ":";
      if (parsedComments.get(0).type == MailComment.CommentType.CHANGE_MESSAGE) {
        if (parsedComments.size() > 1) {
          changeMsg += "\n" + numComments(parsedComments.size() - 1);
        }
        changeMsg += "\n" + parsedComments.get(0).message;
      } else {
        changeMsg += "\n" + numComments(parsedComments.size());
      }

      return ChangeMessagesUtil.newMessage(ctx, changeMsg, tag);
    }

    private PatchSet targetPatchSetForComment(
        ChangeContext ctx, MailComment mailComment, PatchSet current) throws OrmException {
      if (mailComment.inReplyTo != null) {
        return psUtil.get(
            ctx.getDb(),
            ctx.getNotes(),
            new PatchSet.Id(ctx.getChange().getId(), mailComment.inReplyTo.key.patchSetId));
      }
      return current;
    }

    private Comment persistentCommentFromMailComment(
        ChangeContext ctx, MailComment mailComment, PatchSet patchSetForComment)
        throws OrmException {
      String fileName;
      // The patch set that this comment is based on is different if this
      // comment was sent in reply to a comment on a previous patch set.
      Side side;
      if (mailComment.inReplyTo != null) {
        fileName = mailComment.inReplyTo.key.filename;
        side = Side.fromShort(mailComment.inReplyTo.side);
      } else {
        fileName = mailComment.fileName;
        side = Side.REVISION;
      }

      Comment comment =
          commentsUtil.newComment(
              ctx,
              fileName,
              patchSetForComment.getId(),
              (short) side.ordinal(),
              mailComment.message);
      comment.tag = tag;
      if (mailComment.inReplyTo != null) {
        comment.parentUuid = mailComment.inReplyTo.key.uuid;
        comment.lineNbr = mailComment.inReplyTo.lineNbr;
        comment.range = mailComment.inReplyTo.range;
      }
      CommentsUtil.setCommentRevId(comment, patchListCache, ctx.getChange(), patchSetForComment);
      return comment;
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
