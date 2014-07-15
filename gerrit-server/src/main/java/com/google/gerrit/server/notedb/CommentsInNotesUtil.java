// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;
import static com.google.gerrit.server.notedb.ChangeNotes.parseException;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.GitDateFormatter.Format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Utility functions to parse PatchLineComments out of a note byte array and
 * store a list of PatchLineComments in the form of a note (in a byte array).
 **/
public class CommentsInNotesUtil {
  private static final String AUTHOR = "Author";
  private static final String BASE_PATCH_SET = "Base-for-patch-set";
  private static final String COMMENT_RANGE = "Comment-range";
  private static final String FILE = "File";
  private static final String LENGTH = "Bytes";
  private static final String PARENT = "Parent";
  private static final String PATCH_SET = "Patch-set";
  private static final String REVISION = "Revision";
  private static final String UUID = "UUID";

  public static NoteMap parseCommentsFromNotes(Repository repo, String refName,
      RevWalk walk, Change.Id changeId,
      Multimap<PatchSet.Id, PatchLineComment> commentsForBase,
      Multimap<PatchSet.Id, PatchLineComment> commentsForPs)
      throws IOException, ConfigInvalidException {
    Ref ref = repo.getRef(refName);
    if (ref == null) {
      return null;
    }
    RevCommit commit = walk.parseCommit(ref.getObjectId());
    NoteMap noteMap = NoteMap.read(walk.getObjectReader(), commit);

    for (Note note: noteMap) {
      byte[] bytes = walk.getObjectReader().open(
          note.getData(), Constants.OBJ_BLOB).getBytes();
      List<PatchLineComment> result = parseNote(bytes, changeId);
      if ((result == null) || (result.isEmpty())) {
        continue;
      }
      PatchSet.Id psId = result.get(0).getKey().getParentKey().getParentKey();
      short side = result.get(0).getSide();
      if (side == 0) {
        commentsForBase.putAll(psId, result);
      } else {
        commentsForPs.putAll(psId, result);
      }
    }
    return noteMap;
  }

  public static List<PatchLineComment> parseNote(byte[] note,
      Change.Id changeId) throws ConfigInvalidException {
    List<PatchLineComment> result = Lists.newArrayList();
    int sizeOfNote = note.length;
    Charset enc = RawParseUtils.parseEncoding(note);
    MutableInteger curr = new MutableInteger();
    curr.value = 0;

    boolean isForBase =
        (RawParseUtils.match(note, curr.value, PATCH_SET.getBytes(UTF_8))) < 0;

    PatchSet.Id psId = parsePsId(note, curr, changeId, enc,
        isForBase ? BASE_PATCH_SET : PATCH_SET);

    RevId revId =
        new RevId(parseStringField(note, curr, changeId, enc, REVISION));

    PatchLineComment c = null;
    while (curr.value < sizeOfNote) {
      String previousFileName = c == null ?
          null : c.getKey().getParentKey().getFileName();
      c = parseComment(note, curr, previousFileName, psId, revId,
          isForBase, enc);
      result.add(c);
    }
    return result;
  }

  public static String formatTime(PersonIdent ident, Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    // TODO(dborowitz): Use a ThreadLocal or use Joda.
    PersonIdent newIdent = new PersonIdent(ident, t);
    return dateFormatter.formatDate(newIdent);
  }

  public static PatchSet.Id getCommentPsId(PatchLineComment plc) {
    return plc.getKey().getParentKey().getParentKey();
  }

  private static PatchLineComment parseComment(byte[] note, MutableInteger curr,
      String currentFileName, PatchSet.Id psId, RevId revId, boolean isForBase,
      Charset enc)
          throws ConfigInvalidException {
    Change.Id changeId = psId.getParentKey();

    // Check if there is a new file.
    boolean newFile =
        (RawParseUtils.match(note, curr.value, FILE.getBytes(UTF_8))) != -1;
    if (newFile) {
      // If so, parse the new file name.
      currentFileName = parseFilename(note, curr, changeId, enc);
    } else if (currentFileName == null) {
      throw parseException(changeId, "could not parse %s", FILE);
    }

    CommentRange range = parseCommentRange(note, curr, changeId);
    if (range == null) {
      throw parseException(changeId, "could not parse %s", COMMENT_RANGE);
    }

    Timestamp commentTime = parseTimestamp(note, curr, changeId, enc);
    Account.Id aId = parseAuthor(note, curr, changeId, enc);

    boolean hasParent =
        (RawParseUtils.match(note, curr.value, PARENT.getBytes(enc))) != -1;
    String parentUUID = null;
    if (hasParent) {
      parentUUID = parseStringField(note, curr, changeId, enc, PARENT);
    }

    String uuid = parseStringField(note, curr, changeId, enc, UUID);
    int commentLength = parseCommentLength(note, curr, changeId, enc);

    String message = RawParseUtils.decode(
        enc, note, curr.value, curr.value + commentLength);
    checkResult(message, "message contents", changeId);

    PatchLineComment plc = new PatchLineComment(
        new PatchLineComment.Key(new Patch.Key(psId, currentFileName), uuid),
        range.getEndLine(), aId, parentUUID, commentTime);
    plc.setMessage(message);
    plc.setSide((short) (isForBase ? 0 : 1));
    if (range.getStartCharacter() != -1) {
      plc.setRange(range);
    }
    plc.setRevId(revId);

    curr.value = RawParseUtils.nextLF(note, curr.value + commentLength);
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return plc;
  }

  private static String parseStringField(byte[] note, MutableInteger curr,
      Change.Id changeId, Charset enc, String fieldName)
      throws ConfigInvalidException {
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    checkHeaderLineFormat(note, curr, fieldName, enc, changeId);
    int startOfField = RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    curr.value = endOfLine;
    return RawParseUtils.decode(enc, note, startOfField, endOfLine - 1);
  }

  /**
   * @return a comment range. If the comment range line in the note only has
   *    one number, we return a CommentRange with that one number as the end
   *    line and the other fields as -1. If the comment range line in the note
   *    contains a whole comment range, then we return a CommentRange with all
   *    fields set. If the line is not correctly formatted, return null.
   */
  private static CommentRange parseCommentRange(byte[] note, MutableInteger ptr,
      Change.Id changeId) throws ConfigInvalidException {
    CommentRange range = new CommentRange(-1, -1, -1, -1);

    int startLine = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (startLine == 0) {
      return null;
    }

    if (note[ptr.value] == '\n') {
      range.setEndLine(startLine);
      return range;
    } else if (note[ptr.value] == ':') {
      range.setStartLine(startLine);
      ptr.value += 1;
    } else {
      return null;
    }

    int startChar = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (startChar == 0) {
      return null;
    }
    if (note[ptr.value] == '-') {
      range.setStartCharacter(startChar);
      ptr.value += 1;
    } else {
      return null;
    }

    int endLine = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (endLine == 0) {
      return null;
    }
    if (note[ptr.value] == ':') {
      range.setEndLine(endLine);
      ptr.value += 1;
    } else {
      return null;
    }

    int endChar = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (endChar == 0) {
      return null;
    }
    if (note[ptr.value] == '\n') {
      range.setEndCharacter(endChar);
      ptr.value += 1;
    } else {
      return null;
    }
    return range;
  }

  private static PatchSet.Id parsePsId(byte[] note, MutableInteger curr,
      Change.Id changeId, Charset enc, String fieldName)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, fieldName, enc, changeId);
    int startOfPsId =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    int patchSetId =
        RawParseUtils.parseBase10(note, startOfPsId, i);
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine - 1) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
    checkResult(patchSetId, "patchset id", changeId);
    curr.value = endOfLine;
    return new PatchSet.Id(changeId, patchSetId);
  }

  private static String parseFilename(byte[] note, MutableInteger curr,
      Change.Id changeId, Charset enc) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, FILE, enc, changeId);
    int startOfFileName =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    curr.value = endOfLine;
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return QuotedString.GIT_PATH.dequote(
        RawParseUtils.decode(enc, note, startOfFileName, endOfLine - 1));
  }

  private static Timestamp parseTimestamp(byte[] note, MutableInteger curr,
      Change.Id changeId, Charset enc)
      throws ConfigInvalidException {
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    Timestamp commentTime;
    String dateString =
        RawParseUtils.decode(enc, note, curr.value, endOfLine - 1);
    try {
      commentTime =
          new Timestamp(GitDateParser.parse(dateString, null).getTime());
    } catch (ParseException e) {
      throw new ConfigInvalidException("could not parse comment timestamp", e);
    }
    curr.value = endOfLine;
    return checkResult(commentTime, "comment timestamp", changeId);
  }

  private static Account.Id parseAuthor(byte[] note, MutableInteger curr,
      Change.Id changeId, Charset enc) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, AUTHOR, enc, changeId);
    int startOfAccountId =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    PersonIdent ident =
        RawParseUtils.parsePersonIdent(note, startOfAccountId);
    Account.Id aId = parseIdent(ident, changeId);
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return checkResult(aId, "comment author", changeId);
  }

  private static int parseCommentLength(byte[] note, MutableInteger curr,
      Change.Id changeId, Charset enc) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, LENGTH, enc, changeId);
    int startOfLength =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    int commentLength =
        RawParseUtils.parseBase10(note, startOfLength, i);
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine-1) {
      throw parseException(changeId, "could not parse %s", PATCH_SET);
    }
    curr.value = endOfLine;
    return checkResult(commentLength, "comment length", changeId);
  }

  private static <T> T checkResult(T o, String fieldName,
      Change.Id changeId) throws ConfigInvalidException {
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

  private PersonIdent newIdent(Account author, Date when) {
    return new PersonIdent(
        author.getFullName(),
        author.getId().get() + "@" + GERRIT_PLACEHOLDER_HOST,
        when, serverIdent.getTimeZone());
  }

  private static Account.Id parseIdent(PersonIdent ident, Change.Id changeId)
      throws ConfigInvalidException {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      Integer id = Ints.tryParse(email.substring(0, at));
      if (id != null && host.equals(GERRIT_PLACEHOLDER_HOST)) {
        return new Account.Id(id);
      }
    }
    throw parseException(changeId, "invalid identity, expected <id>@%s: %s",
      GERRIT_PLACEHOLDER_HOST, email);
  }

  private void appendHeaderField(PrintWriter writer,
      String field, String value) throws IOException {
    writer.print(field);
    writer.print(": ");
    writer.print(value);
    writer.print('\n');
  }

  private static void checkHeaderLineFormat(byte[] note, MutableInteger curr,
      String fieldName, Charset enc, Change.Id changeId)
      throws ConfigInvalidException {
    boolean correct =
        RawParseUtils.match(note, curr.value, fieldName.getBytes(enc)) != -1;
    correct &= (note[curr.value + fieldName.length()] == ':');
    correct &= (note[curr.value + fieldName.length() + 1] == ' ');
    if (!correct) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
  }

  private final AccountCache accountCache;
  private final PersonIdent serverIdent;

  @VisibleForTesting
  @Inject
  public CommentsInNotesUtil(AccountCache accountCache,
      @GerritPersonIdent PersonIdent serverIdent) {
    this.accountCache = accountCache;
    this.serverIdent = serverIdent;
  }

  /**
   * Build a note that contains the metadata for and the contents of all of the
   * comments in the given list of comments.
   *
   * @param comments
   *            A list of the comments to be written to the returned note
   *            byte array.
   *            All of the comments in this list must have the same side and
   *            must share the same PatchSet.Id.
   *            This list must not be empty because we cannot build a note
   *            for no comments.
   * @return the note. Null if there are no comments in the list.
   */
  public byte[] buildNote(List<PatchLineComment> comments)
      throws OrmException, IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    OutputStreamWriter streamWriter = new OutputStreamWriter(buf, UTF_8);
    PrintWriter writer = new PrintWriter(streamWriter);
    PatchLineComment first = comments.get(0);

    short side = first.getSide();
    PatchSet.Id psId = getCommentPsId(first);
    appendHeaderField(writer, side == 0
        ? BASE_PATCH_SET
        : PATCH_SET,
        Integer.toString(psId.get()));
    appendHeaderField(writer, REVISION, first.getRevId().get());

    String currentFilename = null;

    for (PatchLineComment c : comments) {
      PatchSet.Id currentPsId = getCommentPsId(c);
      checkArgument(psId.equals(currentPsId),
          "All comments being added must all have the same PatchSet.Id. The"
          + "comment below does not have the same PatchSet.Id as the others "
          + "(%d).\n%s", psId.toString(), c.toString());
      checkArgument(side == c.getSide(),
          "All comments being added must all have the same side. The"
          + "comment below does not have the same side as the others "
          + "(%d).\n%s", side, c.toString());
      String commentFilename =
          QuotedString.GIT_PATH.quote(c.getKey().getParentKey().getFileName());

      if (!commentFilename.equals(currentFilename)) {
        currentFilename = commentFilename;
        writer.print("File: ");
        writer.print(commentFilename);
        writer.print("\n\n");
      }

      // The CommentRange field for a comment is allowed to be null.
      // If it is indeed null, then in the first line, we simply use the line
      // number field for a comment instead. If it isn't null, we write the
      // comment range itself.
      CommentRange range = c.getRange();
      if (range != null) {
        writer.print(range.getStartLine());
        writer.print(':');
        writer.print(range.getStartCharacter());
        writer.print('-');
        writer.print(range.getEndLine());
        writer.print(':');
        writer.print(range.getEndCharacter());
      } else {
        writer.print(c.getLine());
      }
      writer.print("\n");

      writer.print(formatTime(serverIdent, c.getWrittenOn()));
      writer.print("\n");

      PersonIdent ident =
          newIdent(accountCache.get(c.getAuthor()).getAccount(),
              c.getWrittenOn());
      String nameString = ident.getName() + " <" + ident.getEmailAddress()
          + ">";
      appendHeaderField(writer, AUTHOR, nameString);

      String parent = c.getParentUuid();
      if (parent != null) {
        appendHeaderField(writer, PARENT, parent);
      }

      appendHeaderField(writer, UUID, c.getKey().get());

      byte[] messageBytes = c.getMessage().getBytes(UTF_8);
      appendHeaderField(writer, LENGTH,
          Integer.toString(messageBytes.length));

      writer.print(c.getMessage());
      writer.print("\n\n");
    }
    writer.close();
    return buf.toByteArray();
  }

  public void writeCommentsToNoteMap(NoteMap noteMap,
      List<PatchLineComment> allComments, ObjectInserter inserter)
        throws OrmException, IOException {
    checkArgument(!allComments.isEmpty(),
        "No comments to write; to delete, use removeNoteFromNoteMap().");
    ObjectId commitOID =
        ObjectId.fromString(allComments.get(0).getRevId().get());
    Collections.sort(allComments, ChangeNotes.PatchLineCommentComparator);
    byte[] note = buildNote(allComments);
    ObjectId noteId = inserter.insert(Constants.OBJ_BLOB, note, 0, note.length);
    noteMap.set(commitOID, noteId);
  }

  public void removeNote(NoteMap noteMap, RevId commitId)
      throws IOException {
    noteMap.remove(ObjectId.fromString(commitId.get()));
  }
}
