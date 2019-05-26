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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.CommentsUtil.COMMENT_ORDER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerId;
import com.google.inject.Inject;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.QuotedString;

public class LegacyChangeNoteWrite {

  private final PersonIdent serverIdent;
  private final String serverId;

  @Inject
  public LegacyChangeNoteWrite(
      @GerritPersonIdent PersonIdent serverIdent, @GerritServerId String serverId) {
    this.serverIdent = serverIdent;
    this.serverId = serverId;
  }

  public PersonIdent newIdent(Account.Id authorId, Date when, PersonIdent serverIdent) {
    return new PersonIdent(
        authorId.toString(), authorId.get() + "@" + serverId, when, serverIdent.getTimeZone());
  }

  @VisibleForTesting
  public PersonIdent newIdent(Account author, Date when, PersonIdent serverIdent) {
    return new PersonIdent(
        author.toString(), author.id().get() + "@" + serverId, when, serverIdent.getTimeZone());
  }

  public String getServerId() {
    return serverId;
  }

  private void appendHeaderField(PrintWriter writer, String field, String value) {
    writer.print(field);
    writer.print(": ");
    writer.print(value);
    writer.print('\n');
  }

  /**
   * Build a note that contains the metadata for and the contents of all of the comments in the
   * given comments.
   *
   * @param comments Comments to be written to the output stream, keyed by patch set ID; multiple
   *     patch sets are allowed since base revisions may be shared across patch sets. All of the
   *     comments must share the same commitId, and all the comments for a given patch set must have
   *     the same side.
   * @param out output stream to write to.
   */
  @UsedAt(UsedAt.Project.GOOGLE)
  public void buildNote(ListMultimap<Integer, Comment> comments, OutputStream out) {
    if (comments.isEmpty()) {
      return;
    }

    ImmutableList<Integer> psIds = comments.keySet().stream().sorted().collect(toImmutableList());

    OutputStreamWriter streamWriter = new OutputStreamWriter(out, UTF_8);
    try (PrintWriter writer = new PrintWriter(streamWriter)) {
      ObjectId commitId = comments.values().iterator().next().getCommitId();
      String commitName = commitId.name();
      appendHeaderField(writer, ChangeNoteUtil.REVISION, commitName);

      for (int psId : psIds) {
        List<Comment> psComments = COMMENT_ORDER.sortedCopy(comments.get(psId));
        Comment first = psComments.get(0);

        short side = first.side;
        appendHeaderField(
            writer,
            side <= 0 ? ChangeNoteUtil.BASE_PATCH_SET : ChangeNoteUtil.PATCH_SET,
            Integer.toString(psId));
        if (side < 0) {
          appendHeaderField(writer, ChangeNoteUtil.PARENT_NUMBER, Integer.toString(-side));
        }

        String currentFilename = null;

        for (Comment c : psComments) {
          checkArgument(
              commitId.equals(c.getCommitId()),
              "All comments being added must have all the same commitId. The "
                  + "comment below does not have the same commitId as the others "
                  + "(%s).\n%s",
              commitId,
              c);
          checkArgument(
              side == c.side,
              "All comments being added must all have the same side. The "
                  + "comment below does not have the same side as the others "
                  + "(%s).\n%s",
              side,
              c);
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

    writer.print(NoteDbUtil.formatTime(serverIdent, c.writtenOn));
    writer.print("\n");

    appendIdent(writer, ChangeNoteUtil.AUTHOR, c.author.getId(), c.writtenOn);
    if (!c.getRealAuthor().equals(c.author)) {
      appendIdent(writer, ChangeNoteUtil.REAL_AUTHOR, c.getRealAuthor().getId(), c.writtenOn);
    }

    String parent = c.parentUuid;
    if (parent != null) {
      appendHeaderField(writer, ChangeNoteUtil.PARENT, parent);
    }

    appendHeaderField(writer, ChangeNoteUtil.UNRESOLVED, Boolean.toString(c.unresolved));
    appendHeaderField(writer, ChangeNoteUtil.UUID, c.key.uuid);

    if (c.tag != null) {
      appendHeaderField(writer, ChangeNoteUtil.TAG, c.tag);
    }

    byte[] messageBytes = c.message.getBytes(UTF_8);
    appendHeaderField(writer, ChangeNoteUtil.LENGTH, Integer.toString(messageBytes.length));

    writer.print(c.message);
    writer.print("\n\n");
  }

  private void appendIdent(PrintWriter writer, String header, Account.Id id, Timestamp ts) {
    PersonIdent ident = newIdent(id, ts, serverIdent);
    StringBuilder name = new StringBuilder();
    PersonIdent.appendSanitized(name, ident.getName());
    name.append(" <");
    PersonIdent.appendSanitized(name, ident.getEmailAddress());
    name.append('>');
    appendHeaderField(writer, header, name.toString());
  }
}
