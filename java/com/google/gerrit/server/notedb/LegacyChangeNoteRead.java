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

import static com.google.gerrit.server.notedb.ChangeNotes.parseException;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.config.GerritServerId;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;

public class LegacyChangeNoteRead {
  private final String serverId;

  @Inject
  public LegacyChangeNoteRead(@GerritServerId String serverId) {
    this.serverId = serverId;
  }

  public Account.Id parseIdent(PersonIdent ident, Change.Id changeId)
      throws ConfigInvalidException {
    return NoteDbUtil.parseIdent(ident, serverId)
        .orElseThrow(
            () ->
                parseException(
                    changeId,
                    "invalid identity, expected <id>@%s: %s",
                    serverId,
                    ident.getEmailAddress()));
  }

  private static boolean match(byte[] note, MutableInteger p, byte[] expected) {
    int m = RawParseUtils.match(note, p.value, expected);
    return m == p.value + expected.length;
  }

  public List<Comment> parseNote(byte[] note, MutableInteger p, Change.Id changeId)
      throws ConfigInvalidException {
    if (p.value >= note.length) {
      return ImmutableList.of();
    }
    Set<Comment.Key> seen = new HashSet<>();
    List<Comment> result = new ArrayList<>();
    int sizeOfNote = note.length;
    byte[] psb = ChangeNoteUtil.PATCH_SET.getBytes(UTF_8);
    byte[] bpsb = ChangeNoteUtil.BASE_PATCH_SET.getBytes(UTF_8);
    byte[] bpn = ChangeNoteUtil.PARENT_NUMBER.getBytes(UTF_8);

    RevId revId = new RevId(parseStringField(note, p, changeId, ChangeNoteUtil.REVISION));
    String fileName = null;
    PatchSet.Id psId = null;
    boolean isForBase = false;
    Integer parentNumber = null;

    while (p.value < sizeOfNote) {
      boolean matchPs = match(note, p, psb);
      boolean matchBase = match(note, p, bpsb);
      if (matchPs) {
        fileName = null;
        psId = parsePsId(note, p, changeId, ChangeNoteUtil.PATCH_SET);
        isForBase = false;
      } else if (matchBase) {
        fileName = null;
        psId = parsePsId(note, p, changeId, ChangeNoteUtil.BASE_PATCH_SET);
        isForBase = true;
        if (match(note, p, bpn)) {
          parentNumber = parseParentNumber(note, p, changeId);
        }
      } else if (psId == null) {
        throw parseException(
            changeId,
            "missing %s or %s header",
            ChangeNoteUtil.PATCH_SET,
            ChangeNoteUtil.BASE_PATCH_SET);
      }

      Comment c = parseComment(note, p, fileName, psId, revId, isForBase, parentNumber);
      fileName = c.key.filename;
      if (!seen.add(c.key)) {
        throw parseException(changeId, "multiple comments for %s in note", c.key);
      }
      result.add(c);
    }
    return result;
  }

  private Comment parseComment(
      byte[] note,
      MutableInteger curr,
      String currentFileName,
      PatchSet.Id psId,
      RevId revId,
      boolean isForBase,
      Integer parentNumber)
      throws ConfigInvalidException {
    Change.Id changeId = psId.changeId();

    // Check if there is a new file.
    boolean newFile =
        (RawParseUtils.match(note, curr.value, ChangeNoteUtil.FILE.getBytes(UTF_8))) != -1;
    if (newFile) {
      // If so, parse the new file name.
      currentFileName = parseFilename(note, curr, changeId);
    } else if (currentFileName == null) {
      throw parseException(changeId, "could not parse %s", ChangeNoteUtil.FILE);
    }

    CommentRange range = parseCommentRange(note, curr);
    if (range == null) {
      throw parseException(changeId, "could not parse %s", ChangeNoteUtil.COMMENT_RANGE);
    }

    Timestamp commentTime = parseTimestamp(note, curr, changeId);
    Account.Id aId = parseAuthor(note, curr, changeId, ChangeNoteUtil.AUTHOR);
    boolean hasRealAuthor =
        (RawParseUtils.match(note, curr.value, ChangeNoteUtil.REAL_AUTHOR.getBytes(UTF_8))) != -1;
    Account.Id raId = null;
    if (hasRealAuthor) {
      raId = parseAuthor(note, curr, changeId, ChangeNoteUtil.REAL_AUTHOR);
    }

    boolean hasParent =
        (RawParseUtils.match(note, curr.value, ChangeNoteUtil.PARENT.getBytes(UTF_8))) != -1;
    String parentUUID = null;
    boolean unresolved = false;
    if (hasParent) {
      parentUUID = parseStringField(note, curr, changeId, ChangeNoteUtil.PARENT);
    }
    boolean hasUnresolved =
        (RawParseUtils.match(note, curr.value, ChangeNoteUtil.UNRESOLVED.getBytes(UTF_8))) != -1;
    if (hasUnresolved) {
      unresolved = parseBooleanField(note, curr, changeId, ChangeNoteUtil.UNRESOLVED);
    }

    String uuid = parseStringField(note, curr, changeId, ChangeNoteUtil.UUID);

    boolean hasTag =
        (RawParseUtils.match(note, curr.value, ChangeNoteUtil.TAG.getBytes(UTF_8))) != -1;
    String tag = null;
    if (hasTag) {
      tag = parseStringField(note, curr, changeId, ChangeNoteUtil.TAG);
    }

    int commentLength = parseCommentLength(note, curr, changeId);

    String message = RawParseUtils.decode(UTF_8, note, curr.value, curr.value + commentLength);
    checkResult(message, "message contents", changeId);

    Comment c =
        new Comment(
            new Comment.Key(uuid, currentFileName, psId.get()),
            aId,
            commentTime,
            isForBase ? (short) (parentNumber == null ? 0 : -parentNumber) : (short) 1,
            message,
            serverId,
            unresolved);
    c.lineNbr = range.getEndLine();
    c.parentUuid = parentUUID;
    c.tag = tag;
    c.setRevId(revId);
    if (raId != null) {
      c.setRealAuthor(raId);
    }

    if (range.getStartCharacter() != -1) {
      c.setRange(range);
    }

    curr.value = RawParseUtils.nextLF(note, curr.value + commentLength);
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return c;
  }

  private static String parseStringField(
      byte[] note, MutableInteger curr, Change.Id changeId, String fieldName)
      throws ConfigInvalidException {
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    checkHeaderLineFormat(note, curr, fieldName, changeId);
    int startOfField = RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    curr.value = endOfLine;
    return RawParseUtils.decode(UTF_8, note, startOfField, endOfLine - 1);
  }

  /**
   * @return a comment range. If the comment range line in the note only has one number, we return a
   *     CommentRange with that one number as the end line and the other fields as -1. If the
   *     comment range line in the note contains a whole comment range, then we return a
   *     CommentRange with all fields set. If the line is not correctly formatted, return null.
   */
  private static CommentRange parseCommentRange(byte[] note, MutableInteger ptr) {
    CommentRange range = new CommentRange(-1, -1, -1, -1);

    int last = ptr.value;
    int startLine = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (ptr.value == last) {
      return null;
    } else if (note[ptr.value] == '\n') {
      range.setEndLine(startLine);
      ptr.value += 1;
      return range;
    } else if (note[ptr.value] == ':') {
      range.setStartLine(startLine);
      ptr.value += 1;
    } else {
      return null;
    }

    last = ptr.value;
    int startChar = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (ptr.value == last) {
      return null;
    } else if (note[ptr.value] == '-') {
      range.setStartCharacter(startChar);
      ptr.value += 1;
    } else {
      return null;
    }

    last = ptr.value;
    int endLine = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (ptr.value == last) {
      return null;
    } else if (note[ptr.value] == ':') {
      range.setEndLine(endLine);
      ptr.value += 1;
    } else {
      return null;
    }

    last = ptr.value;
    int endChar = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (ptr.value == last) {
      return null;
    } else if (note[ptr.value] == '\n') {
      range.setEndCharacter(endChar);
      ptr.value += 1;
    } else {
      return null;
    }
    return range;
  }

  private static PatchSet.Id parsePsId(
      byte[] note, MutableInteger curr, Change.Id changeId, String fieldName)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, fieldName, changeId);
    int startOfPsId = RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    int patchSetId = RawParseUtils.parseBase10(note, startOfPsId, i);
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine - 1) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
    checkResult(patchSetId, "patchset id", changeId);
    curr.value = endOfLine;
    return PatchSet.id(changeId, patchSetId);
  }

  private static Integer parseParentNumber(byte[] note, MutableInteger curr, Change.Id changeId)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, ChangeNoteUtil.PARENT_NUMBER, changeId);

    int start = RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    int parentNumber = RawParseUtils.parseBase10(note, start, i);
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine - 1) {
      throw parseException(changeId, "could not parse %s", ChangeNoteUtil.PARENT_NUMBER);
    }
    checkResult(parentNumber, "parent number", changeId);
    curr.value = endOfLine;
    return Integer.valueOf(parentNumber);
  }

  private static String parseFilename(byte[] note, MutableInteger curr, Change.Id changeId)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, ChangeNoteUtil.FILE, changeId);
    int startOfFileName = RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    curr.value = endOfLine;
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return QuotedString.GIT_PATH.dequote(
        RawParseUtils.decode(UTF_8, note, startOfFileName, endOfLine - 1));
  }

  private static Timestamp parseTimestamp(byte[] note, MutableInteger curr, Change.Id changeId)
      throws ConfigInvalidException {
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    Timestamp commentTime;
    String dateString = RawParseUtils.decode(UTF_8, note, curr.value, endOfLine - 1);
    try {
      commentTime = new Timestamp(GitDateParser.parse(dateString, null, Locale.US).getTime());
    } catch (ParseException e) {
      throw new ConfigInvalidException("could not parse comment timestamp", e);
    }
    curr.value = endOfLine;
    return checkResult(commentTime, "comment timestamp", changeId);
  }

  private Account.Id parseAuthor(
      byte[] note, MutableInteger curr, Change.Id changeId, String fieldName)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, fieldName, changeId);
    int startOfAccountId = RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    PersonIdent ident = RawParseUtils.parsePersonIdent(note, startOfAccountId);
    Account.Id aId = parseIdent(ident, changeId);
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return checkResult(aId, fieldName, changeId);
  }

  private static int parseCommentLength(byte[] note, MutableInteger curr, Change.Id changeId)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, ChangeNoteUtil.LENGTH, changeId);
    int startOfLength = RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    i.value = startOfLength;
    int commentLength = RawParseUtils.parseBase10(note, startOfLength, i);
    if (i.value == startOfLength) {
      throw parseException(changeId, "could not parse %s", ChangeNoteUtil.LENGTH);
    }
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine - 1) {
      throw parseException(changeId, "could not parse %s", ChangeNoteUtil.LENGTH);
    }
    curr.value = endOfLine;
    return checkResult(commentLength, "comment length", changeId);
  }

  private boolean parseBooleanField(
      byte[] note, MutableInteger curr, Change.Id changeId, String fieldName)
      throws ConfigInvalidException {
    String str = parseStringField(note, curr, changeId, fieldName);
    if ("true".equalsIgnoreCase(str)) {
      return true;
    } else if ("false".equalsIgnoreCase(str)) {
      return false;
    }
    throw parseException(changeId, "invalid boolean for %s: %s", fieldName, str);
  }

  private static <T> T checkResult(T o, String fieldName, Change.Id changeId)
      throws ConfigInvalidException {
    if (o == null) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
    return o;
  }

  private static int checkResult(int i, String fieldName, Change.Id changeId)
      throws ConfigInvalidException {
    if (i <= 0) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
    return i;
  }

  private static void checkHeaderLineFormat(
      byte[] note, MutableInteger curr, String fieldName, Change.Id changeId)
      throws ConfigInvalidException {
    boolean correct = RawParseUtils.match(note, curr.value, fieldName.getBytes(UTF_8)) != -1;
    int p = curr.value + fieldName.length();
    correct &= (p < note.length && note[p] == ':');
    p++;
    correct &= (p < note.length && note[p] == ' ');
    if (!correct) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
  }
}
