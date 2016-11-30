// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.CommentsUtil.COMMENT_ORDER;
import static com.google.gerrit.server.notedb.ChangeNotes.parseException;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChangeNoteUtil {
  public static final FooterKey FOOTER_ASSIGNEE = new FooterKey("Assignee");
  public static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  public static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  public static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  public static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  public static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  public static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  public static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  public static final FooterKey FOOTER_PATCH_SET_DESCRIPTION =
      new FooterKey("Patch-set-description");
  public static final FooterKey FOOTER_REAL_USER = new FooterKey("Real-user");
  public static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  public static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  public static final FooterKey FOOTER_SUBMISSION_ID =
      new FooterKey("Submission-id");
  public static final FooterKey FOOTER_SUBMITTED_WITH =
      new FooterKey("Submitted-with");
  public static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");
  public static final FooterKey FOOTER_TAG = new FooterKey("Tag");

  private static final String AUTHOR = "Author";
  private static final String BASE_PATCH_SET = "Base-for-patch-set";
  private static final String LENGTH = "Bytes";
  private static final String PARENT = "Parent";
  private static final String PARENT_NUMBER = "Parent-number";
  private static final String PATCH_SET = "Patch-set";
  private static final String REAL_AUTHOR = "Real-author";
  private static final String REVISION = "Revision";
  private static final String UUID = "UUID";
  private static final String TAG = FOOTER_TAG.getName();

  public static String formatTime(PersonIdent ident, Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    // TODO(dborowitz): Use a ThreadLocal or use Joda.
    PersonIdent newIdent = new PersonIdent(ident, t);
    return dateFormatter.formatDate(newIdent);
  }

  private final AccountCache accountCache;
  private final PersonIdent serverIdent;
  private final String anonymousCowardName;
  private final String serverId;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  @Inject
  public ChangeNoteUtil(AccountCache accountCache,
      @GerritPersonIdent PersonIdent serverIdent,
      @AnonymousCowardName String anonymousCowardName,
      @GerritServerId String serverId) {
    this.accountCache = accountCache;
    this.serverIdent = serverIdent;
    this.anonymousCowardName = anonymousCowardName;
    this.serverId = serverId;
  }

  @VisibleForTesting
  public PersonIdent newIdent(Account author, Date when,
      PersonIdent serverIdent, String anonymousCowardName) {
    return new PersonIdent(
        author.getName(anonymousCowardName),
        author.getId().get() + "@" + serverId,
        when, serverIdent.getTimeZone());
  }

  public Gson getGson() {
    return gson;
  }

  public Account.Id parseIdent(PersonIdent ident, Change.Id changeId)
      throws ConfigInvalidException {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      if (host.equals(serverId)) {
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null) {
          return new Account.Id(id);
        }
      }
    }
    throw parseException(changeId, "invalid identity, expected <id>@%s: %s",
        serverId, email);
  }

  private void appendHeaderField(PrintWriter writer,
      String field, String value) {
    writer.print(field);
    writer.print(": ");
    writer.print(value);
    writer.print('\n');
  }

  /**
   * Build a note that contains the metadata for and the contents of all of the
   * comments in the given comments.
   *
   * @param comments Comments to be written to the output stream, keyed by patch
   *     set ID; multiple patch sets are allowed since base revisions may be
   *     shared across patch sets. All of the comments must share the same
   *     RevId, and all the comments for a given patch set must have the same
   *     side.
   * @param out output stream to write to.
   */
  void buildNote(Multimap<Integer, Comment> comments,
      OutputStream out) {
    if (comments.isEmpty()) {
      return;
    }

    List<Integer> psIds = new ArrayList<>(comments.keySet());
    Collections.sort(psIds);

    OutputStreamWriter streamWriter = new OutputStreamWriter(out, UTF_8);
    try (PrintWriter writer = new PrintWriter(streamWriter)) {
      String revId = comments.values().iterator().next().revId;
      appendHeaderField(writer, REVISION, revId);

      for (int psId : psIds) {
        List<Comment> psComments = COMMENT_ORDER.sortedCopy(comments.get(psId));
        Comment first = psComments.get(0);

        short side = first.side;
        appendHeaderField(writer, side <= 0
            ? BASE_PATCH_SET
            : PATCH_SET,
            Integer.toString(psId));
        if (side < 0) {
          appendHeaderField(writer, PARENT_NUMBER, Integer.toString(-side));
        }

        String currentFilename = null;

        for (Comment c : psComments) {
          checkArgument(revId.equals(c.revId),
              "All comments being added must have all the same RevId. The "
              + "comment below does not have the same RevId as the others "
              + "(%s).\n%s", revId, c);
          checkArgument(side == c.side,
              "All comments being added must all have the same side. The "
              + "comment below does not have the same side as the others "
              + "(%s).\n%s", side, c);
          String commentFilename = QuotedString.GIT_PATH.quote(c.key.filename);

          if (!commentFilename.equals(currentFilename)) {
            currentFilename = commentFilename;
            writer.print("File: ");
            writer.print(commentFilename);
            writer.print("\n\n");
          }

          appendOneComment(writer, c);
        }
      }
    }
  }

  private void appendOneComment(PrintWriter writer, Comment c) {
    // The CommentRange field for a comment is allowed to be null. If it is
    // null, then in the first line, we simply use the line number field for a
    // comment instead. If it isn't null, we write the comment range itself.
    Comment.Range range = c.range;
    if (range != null) {
      writer.print(range.startLine);
      writer.print(':');
      writer.print(range.startChar);
      writer.print('-');
      writer.print(range.endLine);
      writer.print(':');
      writer.print(range.endChar);
    } else {
      writer.print(c.lineNbr);
    }
    writer.print("\n");

    writer.print(formatTime(serverIdent, c.writtenOn));
    writer.print("\n");

    appendIdent(writer, AUTHOR, c.author.getId(), c.writtenOn);
    if (!c.getRealAuthor().equals(c.author)) {
      appendIdent(writer, REAL_AUTHOR, c.getRealAuthor().getId(), c.writtenOn);
    }

    String parent = c.parentUuid;
    if (parent != null) {
      appendHeaderField(writer, PARENT, parent);
    }

    appendHeaderField(writer, UUID, c.key.uuid);

    if (c.tag != null) {
      appendHeaderField(writer, TAG, c.tag);
    }

    byte[] messageBytes = c.message.getBytes(UTF_8);
    appendHeaderField(writer, LENGTH,
        Integer.toString(messageBytes.length));

    writer.print(c.message);
    writer.print("\n\n");
  }

  private void appendIdent(PrintWriter writer, String header, Account.Id id,
      Timestamp ts) {
    PersonIdent ident = newIdent(
        accountCache.get(id).getAccount(),
        ts, serverIdent, anonymousCowardName);
    StringBuilder name = new StringBuilder();
    PersonIdent.appendSanitized(name, ident.getName());
    name.append(" <");
    PersonIdent.appendSanitized(name, ident.getEmailAddress());
    name.append('>');
    appendHeaderField(writer, header, name.toString());
  }
}
