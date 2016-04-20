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
import static com.google.gerrit.server.notedb.ChangeNotes.parseException;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.RawParseUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChangeNoteUtil {
  static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  static final FooterKey FOOTER_SUBMISSION_ID = new FooterKey("Submission-id");
  static final FooterKey FOOTER_SUBMITTED_WITH =
      new FooterKey("Submitted-with");
  static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");
  static final FooterKey FOOTER_TAG = new FooterKey("Tag");

  private static final String AUTHOR = "Author";
  private static final String BASE_PATCH_SET = "Base-for-patch-set";
  private static final String COMMENT_RANGE = "Comment-range";
  private static final String FILE = "File";
  private static final String LENGTH = "Bytes";
  private static final String PARENT = "Parent";
  private static final String PATCH_SET = "Patch-set";
  private static final String REVISION = "Revision";
  private static final String UUID = "UUID";
  private static final String TAG = FOOTER_TAG.getName();

  public static String changeRefName(Change.Id id) {
    StringBuilder r = new StringBuilder();
    r.append(RefNames.REFS_CHANGES);
    int n = id.get();
    int m = n % 100;
    if (m < 10) {
      r.append('0');
    }
    r.append(m);
    r.append('/');
    r.append(n);
    r.append(RefNames.META_SUFFIX);
    return r.toString();
  }

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

  public List<PatchLineComment> parseNote(byte[] note, MutableInteger p,
      Change.Id changeId, Status status) throws ConfigInvalidException {
    if (p.value >= note.length) {
      return ImmutableList.of();
    }
    Set<PatchLineComment.Key> seen = new HashSet<>();
    List<PatchLineComment> result = Lists.newArrayList();
    int sizeOfNote = note.length;

    boolean isForBase =
        (RawParseUtils.match(note, p.value, PATCH_SET.getBytes(UTF_8))) < 0;

    PatchSet.Id psId = parsePsId(note, p, changeId, isForBase ? BASE_PATCH_SET : PATCH_SET);

    RevId revId =
        new RevId(parseStringField(note, p, changeId, REVISION));

    PatchLineComment c = null;
    while (p.value < sizeOfNote) {
      String previousFileName = c == null ?
          null : c.getKey().getParentKey().getFileName();
      c = parseComment(note, p, previousFileName, psId, revId,
          isForBase, status);
      if (!seen.add(c.getKey())) {
        throw parseException(
            changeId, "multiple comments for %s in note", c.getKey());
      }
      result.add(c);
    }
    return result;
  }

  private PatchLineComment parseComment(byte[] note, MutableInteger curr,
      String currentFileName, PatchSet.Id psId, RevId revId, boolean isForBase,
      Status status) throws ConfigInvalidException {
    Change.Id changeId = psId.getParentKey();

    // Check if there is a new file.
    boolean newFile =
        (RawParseUtils.match(note, curr.value, FILE.getBytes(UTF_8))) != -1;
    if (newFile) {
      // If so, parse the new file name.
      currentFileName = parseFilename(note, curr, changeId);
    } else if (currentFileName == null) {
      throw parseException(changeId, "could not parse %s", FILE);
    }

    CommentRange range = parseCommentRange(note, curr);
    if (range == null) {
      throw parseException(changeId, "could not parse %s", COMMENT_RANGE);
    }

    Timestamp commentTime = parseTimestamp(note, curr, changeId);
    Account.Id aId = parseAuthor(note, curr, changeId);

    boolean hasParent =
        (RawParseUtils.match(note, curr.value, PARENT.getBytes(UTF_8))) != -1;
    String parentUUID = null;
    if (hasParent) {
      parentUUID = parseStringField(note, curr, changeId, PARENT);
    }

    String uuid = parseStringField(note, curr, changeId, UUID);

    boolean hasTag =
        (RawParseUtils.match(note, curr.value, TAG.getBytes(UTF_8))) != -1;
    String tag = null;
    if (hasTag) {
      tag = parseStringField(note, curr, changeId, TAG);
    }

    int commentLength = parseCommentLength(note, curr, changeId);

    String message = RawParseUtils.decode(
        UTF_8, note, curr.value, curr.value + commentLength);
    checkResult(message, "message contents", changeId);

    PatchLineComment plc = new PatchLineComment(
        new PatchLineComment.Key(new Patch.Key(psId, currentFileName), uuid),
        range.getEndLine(), aId, parentUUID, commentTime);
    plc.setMessage(message);
    plc.setTag(tag);
    plc.setSide((short) (isForBase ? 0 : 1));
    if (range.getStartCharacter() != -1) {
      plc.setRange(range);
    }
    plc.setRevId(revId);
    plc.setStatus(status);

    curr.value = RawParseUtils.nextLF(note, curr.value + commentLength);
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return plc;
  }

  private static String parseStringField(byte[] note, MutableInteger curr,
      Change.Id changeId, String fieldName) throws ConfigInvalidException {
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    checkHeaderLineFormat(note, curr, fieldName, changeId);
    int startOfField = RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    curr.value = endOfLine;
    return RawParseUtils.decode(UTF_8, note, startOfField, endOfLine - 1);
  }

  /**
   * @return a comment range. If the comment range line in the note only has
   *    one number, we return a CommentRange with that one number as the end
   *    line and the other fields as -1. If the comment range line in the note
   *    contains a whole comment range, then we return a CommentRange with all
   *    fields set. If the line is not correctly formatted, return null.
   */
  private static CommentRange parseCommentRange(byte[] note, MutableInteger ptr) {
    CommentRange range = new CommentRange(-1, -1, -1, -1);

    int last = ptr.value;
    int startLine = RawParseUtils.parseBase10(note, ptr.value, ptr);
    if (ptr.value == last) {
      return null;
    } else if (startLine == 0) {
      range.setEndLine(0);
      ptr.value += 1;
      return range;
    }

    if (note[ptr.value] == '\n') {
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

  private static PatchSet.Id parsePsId(byte[] note, MutableInteger curr,
      Change.Id changeId, String fieldName) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, fieldName, changeId);
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
      Change.Id changeId) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, FILE, changeId);
    int startOfFileName =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    curr.value = endOfLine;
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return QuotedString.GIT_PATH.dequote(
        RawParseUtils.decode(UTF_8, note, startOfFileName, endOfLine - 1));
  }

  private static Timestamp parseTimestamp(byte[] note, MutableInteger curr,
      Change.Id changeId) throws ConfigInvalidException {
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    Timestamp commentTime;
    String dateString =
        RawParseUtils.decode(UTF_8, note, curr.value, endOfLine - 1);
    try {
      commentTime = new Timestamp(
          GitDateParser.parse(dateString, null, Locale.US).getTime());
    } catch (ParseException e) {
      throw new ConfigInvalidException("could not parse comment timestamp", e);
    }
    curr.value = endOfLine;
    return checkResult(commentTime, "comment timestamp", changeId);
  }

  private Account.Id parseAuthor(byte[] note, MutableInteger curr,
      Change.Id changeId) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, AUTHOR, changeId);
    int startOfAccountId =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 2;
    PersonIdent ident =
        RawParseUtils.parsePersonIdent(note, startOfAccountId);
    Account.Id aId = parseIdent(ident, changeId);
    curr.value = RawParseUtils.nextLF(note, curr.value);
    return checkResult(aId, "comment author", changeId);
  }

  private static int parseCommentLength(byte[] note, MutableInteger curr,
      Change.Id changeId) throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, LENGTH, changeId);
    int startOfLength =
        RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    i.value = startOfLength;
    int commentLength =
        RawParseUtils.parseBase10(note, startOfLength, i);
    if (i.value == startOfLength) {
      throw parseException(changeId, "could not parse %s", LENGTH);
    }
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine - 1) {
      throw parseException(changeId, "could not parse %s", LENGTH);
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

  private void appendHeaderField(PrintWriter writer,
      String field, String value) {
    writer.print(field);
    writer.print(": ");
    writer.print(value);
    writer.print('\n');
  }

  private static void checkHeaderLineFormat(byte[] note, MutableInteger curr,
      String fieldName, Change.Id changeId) throws ConfigInvalidException {
    boolean correct =
        RawParseUtils.match(note, curr.value, fieldName.getBytes(UTF_8)) != -1;
    int p = curr.value + fieldName.length();
    correct &= (p < note.length && note[p] == ':');
    p++;
    correct &= (p < note.length && note[p] == ' ');
    if (!correct) {
      throw parseException(changeId, "could not parse %s", fieldName);
    }
  }

  public byte[] buildNote(List<PatchLineComment> comments) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    buildNote(comments, out);
    return out.toByteArray();
  }

  /**
   * Build a note that contains the metadata for and the contents of all of the
   * comments in the given list of comments.
   *
   * @param comments A list of the comments to be written to the
   *            output stream. All of the comments in this list must have the
   *            same side and must share the same patch set ID.
   * @param out output stream to write to.
   */
  void buildNote(List<PatchLineComment> comments, OutputStream out) {
    if (comments.isEmpty()) {
      return;
    }
    OutputStreamWriter streamWriter = new OutputStreamWriter(out, UTF_8);
    try (PrintWriter writer = new PrintWriter(streamWriter)) {
      PatchLineComment first = comments.get(0);

      short side = first.getSide();
      PatchSet.Id psId = PatchLineCommentsUtil.getCommentPsId(first);
      appendHeaderField(writer, side == 0
          ? BASE_PATCH_SET
          : PATCH_SET,
          Integer.toString(psId.get()));
      appendHeaderField(writer, REVISION, first.getRevId().get());

      String currentFilename = null;

      for (PatchLineComment c : comments) {
        PatchSet.Id currentPsId = PatchLineCommentsUtil.getCommentPsId(c);
        checkArgument(psId.equals(currentPsId),
            "All comments being added must all have the same PatchSet.Id. The "
            + "comment below does not have the same PatchSet.Id as the others "
            + "(%s).\n%s", psId.toString(), c.toString());
        checkArgument(side == c.getSide(),
            "All comments being added must all have the same side. The "
            + "comment below does not have the same side as the others "
            + "(%s).\n%s", side, c.toString());
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

        PersonIdent ident = newIdent(
            accountCache.get(c.getAuthor()).getAccount(),
            c.getWrittenOn(), serverIdent, anonymousCowardName);
        String nameString = ident.getName() + " <" + ident.getEmailAddress()
            + ">";
        appendHeaderField(writer, AUTHOR, nameString);

        String parent = c.getParentUuid();
        if (parent != null) {
          appendHeaderField(writer, PARENT, parent);
        }

        appendHeaderField(writer, UUID, c.getKey().get());

        if (c.getTag() != null) {
          appendHeaderField(writer, TAG, c.getTag());
        }

        byte[] messageBytes = c.getMessage().getBytes(UTF_8);
        appendHeaderField(writer, LENGTH,
            Integer.toString(messageBytes.length));

        writer.print(c.getMessage());
        writer.print("\n\n");
      }
    }
  }
}
