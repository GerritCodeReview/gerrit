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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.GitDateFormatter.Format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.List;

/** Utility functions to parse PatchLineComments out of a note byte array. */
public class CommentsInNotesUtil {
  private final AccountCache accountCache;
  private final PersonIdent serverIdent;

  @VisibleForTesting
  @Inject
  public CommentsInNotesUtil(AccountCache accountCache,
      @GerritPersonIdent PersonIdent serverIdent) {
    this.accountCache = accountCache;
    this.serverIdent = serverIdent;
  }

  public static String formatTime(PersonIdent ident, Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    PersonIdent newIdent = new PersonIdent(ident, t);
    return dateFormatter.formatDate(newIdent);
  }

  private String formatTime(Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    PersonIdent newIdent = new PersonIdent(serverIdent, t);
    return dateFormatter.formatDate(newIdent);
  }

  private static void appendHeaderField(OutputStreamWriter writer,
      String field, String value) throws IOException {
    writer.write(field);
    writer.write(": ");
    writer.write(value);
    writer.write('\n');
  }

  /**
   * This function assumes that all comments in this list have the same side
   * because the format for a note is slightly different if it contains notes
   * with side == 0 versus side == 1. Also assumes that all comments will be on
   * the same patchSet.
   * */
  public byte[] buildNote(List<PatchLineComment> comments)
      throws OrmException, IOException {
    if (comments.isEmpty()) {
      return null;
    }

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(buf,
        StandardCharsets.UTF_8);

    short side = comments.get(0).getSide();
    PatchSet.Id psId = comments.get(0).getKey().getParentKey().getParentKey();
    if (side == 0) {
      appendHeaderField(writer, "Base-for-patch-set",
          Integer.toString(
              comments.get(0).getKey().getParentKey().getParentKey().get()));
    } else {
      appendHeaderField(writer, "Patch-set",
          Integer.toString(
              comments.get(0).getKey().getParentKey().getParentKey().get()));
    }
    appendHeaderField(writer, "Rev-Id", comments.get(0).getRevId().get());

    String currentFilename = null;

    for (PatchLineComment c : comments) {
      checkArgument(psId.equals(c.getKey().getParentKey().getParentKey()));
      checkArgument(side == c.getSide());
      String commentFilename =
          QuotedString.GIT_PATH.quote(c.getKey().getParentKey().getFileName());

      if (!commentFilename.equals(currentFilename)) {
        currentFilename = commentFilename;
        writer.write("File: ");
        writer.write(commentFilename);
        writer.write("\n\n");
      }
      CommentRange range = c.getRange();
      if (range != null) {
        writer.write(Integer.toString(range.getStartLine()));
        writer.write(':');
        writer.write(Integer.toString(range.getStartCharacter()));
        writer.write('-');
        writer.write(Integer.toString(range.getEndLine()));
        writer.write(":");
        writer.write(Integer.toString(range.getEndCharacter()));
      } else {
        writer.write(Integer.toString(c.getLine()));
      }
      writer.write("\n");

      writer.write(formatTime(c.getWrittenOn()));
      writer.write("\n");

      Account account = accountCache.get(c.getAuthor()).getAccount();
      String nameString = account.getId().toString() + " ("
          + account.getFullName() + " <" + account.getPreferredEmail() + ">)";
      appendHeaderField(writer, "Author", nameString);

      String parent = c.getParentUuid();
      if (parent != null) {
        appendHeaderField(writer, "Parent", parent);
      }

      appendHeaderField(writer, "UUID", c.getKey().get());

      byte[] messageBytes = c.getMessage().getBytes(StandardCharsets.UTF_8);
      appendHeaderField(writer, "Length",
          Integer.toString(messageBytes.length) + " bytes");

      writer.write(c.getMessage());
      writer.write("\n\n");
    }
    writer.close();
    byte[] result = buf.toByteArray();
    return result;
  }

  private static String parseParent(byte[] note , int curr, Charset enc) {
    int nextNewLine = RawParseUtils.nextLF(note, curr);
    String nextHeader = RawParseUtils.decode(enc, note, curr, curr + 6);
    boolean hasParent = nextHeader.startsWith("Parent");
    if (hasParent) {
      int startOfParent = RawParseUtils.endOfFooterLineKey(note, curr) + 1;
      return RawParseUtils.decode(enc, note, startOfParent, nextNewLine - 1);
    } else {
      return null;
    }
  }

  private static CommentRange parseCommentRange(byte[] note, int curr) {
    MutableInteger m = new MutableInteger();
    m.value = curr;

    int startLine = RawParseUtils.parseBase10(note, m.value, m);
    if (startLine == 0)
      return null;
    m.value += 1;

    int startChar = RawParseUtils.parseBase10(note, m.value, m);
    if (startChar == 0)
      return null;
    m.value += 1;

    int endLine = RawParseUtils.parseBase10(note, m.value, m);
    if (endLine == 0)
      return null;
    m.value += 1;

    int endChar = RawParseUtils.parseBase10(note, m.value, m);
    if (endChar == 0)
      return null;
    m.value += 1;

    CommentRange result =
        new CommentRange(startLine, startChar, endLine, endChar);
    return result;
  }

  public static List<PatchLineComment> parseNote(byte[] note, Id changeId)
      throws ConfigInvalidException {
    List<PatchLineComment> result = Lists.newArrayList();
    int sizeOfNote = note.length;
    Charset enc = RawParseUtils.parseEncoding(note);
    String currentFileName = null;
    int curr = 0;
    int endOfLine;

    // Here, we are parsing the PatchSet.Id.
    String patchSetHeader = RawParseUtils.decode(enc, note, curr, curr + 4);
    boolean isForBase = patchSetHeader.startsWith("Base");

    int startOfPSId =
        RawParseUtils.endOfFooterLineKey(note, curr) + 1;
    int patchSetId =
        RawParseUtils.parseBase10(note, startOfPSId, null);
    checkResult(patchSetId, "patchset id", changeId);
    curr = RawParseUtils.nextLF(note, curr);
    PatchSet.Id psId = new PatchSet.Id(changeId, patchSetId);

    // Here, we are parsing the Rev.Id.
    int startOfRevId =
        RawParseUtils.endOfFooterLineKey(note, curr) + 2;
    endOfLine = RawParseUtils.nextLF(note, curr);
    String revIdString =
        RawParseUtils.decode(enc, note, startOfRevId, endOfLine - 1);
    RevId revId = new RevId(revIdString);
    checkResult(revId, "revId", changeId);
    curr = endOfLine;

    while (curr < sizeOfNote) {
      // Check if there is a new file.
      String start = RawParseUtils.decode(enc, note, curr, curr + 5);
      if (start.startsWith("File:") || currentFileName == null) {
        // If so, parse the new file name.
        int startOfFileName =
            RawParseUtils.endOfFooterLineKey(note, curr) + 2;
        endOfLine = RawParseUtils.nextLF(note, curr);
        currentFileName =
            QuotedString.GIT_PATH.dequote(
                RawParseUtils.decode(
                    enc, note, startOfFileName, endOfLine - 1));
        curr = RawParseUtils.nextLF(note, endOfLine);
      }

      CommentRange range = parseCommentRange(note, curr);
      int commentLine;
      if (range == null) {
        commentLine = RawParseUtils.parseBase10(note, curr, null);
      } else {
        commentLine = range.getEndLine();
      }

      checkResult(commentLine, "comment range", changeId);
      curr = RawParseUtils.nextLF(note, curr);

      // Here, we are parsing the timestamp.
      endOfLine = RawParseUtils.nextLF(note, curr);
      String dateString =
          RawParseUtils.decode(enc, note, curr, endOfLine - 1);
      Timestamp commentTime;
      try {
        commentTime = new Timestamp(GitDateParser.parse(dateString, null).getTime());
      } catch (ParseException e) {
        return null;
      }
      checkResult(commentTime, "comment timestamp", changeId);
      curr = RawParseUtils.nextLF(note, curr);

      // Here, we are parsing the author.
      int startOfAccountId =
          RawParseUtils.endOfFooterLineKey(note, curr) + 2;
      int accountId = RawParseUtils.parseBase10(note, startOfAccountId, null);

      Account.Id aId = new Account.Id(accountId);
      checkResult(aId, "comment author", changeId);
      curr = RawParseUtils.nextLF(note, curr);

      // Here, we are parsing the parent UUID.
      String parentUUID = parseParent(note, curr, enc);
      if (parentUUID != null) {
        curr = RawParseUtils.nextLF(note, curr);
      }

      // Here, we are parsing the UUID.
      int startOfUUID = RawParseUtils.endOfFooterLineKey(note, curr) + 2;
      endOfLine = RawParseUtils.nextLF(note, curr);
      String UUID = RawParseUtils.decode(
          enc, note, startOfUUID, endOfLine - 1);
      checkResult(UUID, "UUID", changeId);
      curr = endOfLine;

      // Here, we are parsing the number of bytes in the comment text.
      int startOfLength =
          RawParseUtils.endOfFooterLineKey(note, curr) + 1;
      int commentLength =
          RawParseUtils.parseBase10(note, startOfLength, null);
      checkResult(commentLength, "comment length", changeId);
      curr = RawParseUtils.nextLF(note, curr);

      String message = RawParseUtils.decode(
          enc, note, curr, curr + commentLength);
      checkResult(message, "message contents", changeId);

      PatchLineComment plc = new PatchLineComment(
          new PatchLineComment.Key(new Patch.Key(psId, currentFileName), UUID),
          commentLine, aId, parentUUID, commentTime);
      plc.setMessage(message);
      plc.setSide((short) (isForBase ? 0 : 1));
      if (range != null) {
        plc.setRange(range);
      }
      plc.setRevId(revId);
      result.add(plc);

      curr = RawParseUtils.nextLF(note, curr + commentLength);
      curr = RawParseUtils.nextLF(note, curr);
    }
    return result;
  }

  private static void checkResult(Object o, String fieldName, Id changeId) throws ConfigInvalidException {
    if (o instanceof Integer) {
      if (((Integer) o).intValue() < 0) {
        throw parseException(changeId, "could not parse ", fieldName);
      }
    }
    if (o == null) {
      throw parseException(changeId, "could not parse ", fieldName);
    }
  }

  private static ConfigInvalidException parseException(Id changeId,
      String fmt, Object... args) {
    return new ConfigInvalidException("Change " + changeId + ": "
        + String.format(fmt, args));
  }
}
