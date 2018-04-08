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
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
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
import java.util.Optional;
import java.util.Set;
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

public class ChangeNoteUtil {
  public static final FooterKey FOOTER_ASSIGNEE = new FooterKey("Assignee");
  public static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  public static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  public static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  public static final FooterKey FOOTER_CURRENT = new FooterKey("Current");
  public static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  public static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  public static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  public static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  public static final FooterKey FOOTER_PATCH_SET_DESCRIPTION =
      new FooterKey("Patch-set-description");
  public static final FooterKey FOOTER_PRIVATE = new FooterKey("Private");
  public static final FooterKey FOOTER_READ_ONLY_UNTIL = new FooterKey("Read-only-until");
  public static final FooterKey FOOTER_REAL_USER = new FooterKey("Real-user");
  public static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  public static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  public static final FooterKey FOOTER_SUBMISSION_ID = new FooterKey("Submission-id");
  public static final FooterKey FOOTER_SUBMITTED_WITH = new FooterKey("Submitted-with");
  public static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");
  public static final FooterKey FOOTER_TAG = new FooterKey("Tag");
  public static final FooterKey FOOTER_WORK_IN_PROGRESS = new FooterKey("Work-in-progress");
  public static final FooterKey FOOTER_REVERT_OF = new FooterKey("Revert-of");

  private static final String AUTHOR = "Author";
  private static final String BASE_PATCH_SET = "Base-for-patch-set";
  private static final String COMMENT_RANGE = "Comment-range";
  private static final String FILE = "File";
  private static final String LENGTH = "Bytes";
  private static final String PARENT = "Parent";
  private static final String PARENT_NUMBER = "Parent-number";
  private static final String PATCH_SET = "Patch-set";
  private static final String REAL_AUTHOR = "Real-author";
  private static final String REVISION = "Revision";
  private static final String UUID = "UUID";
  private static final String UNRESOLVED = "Unresolved";
  private static final String TAG = FOOTER_TAG.getName();

  public static String formatTime(PersonIdent ident, Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    // TODO(dborowitz): Use a ThreadLocal or use Joda.
    PersonIdent newIdent = new PersonIdent(ident, t);
    return dateFormatter.formatDate(newIdent);
  }

  static Gson newGson() {
    return new GsonBuilder()
        .registerTypeAdapter(Timestamp.class, new CommentTimestampAdapter().nullSafe())
        .setPrettyPrinting()
        .create();
  }

  private final AccountCache accountCache;
  private final PersonIdent serverIdent;
  private final String serverId;
  private final Gson gson = newGson();
  private final boolean writeJson;

  @Inject
  public ChangeNoteUtil(
      AccountCache accountCache,
      @GerritPersonIdent PersonIdent serverIdent,
      @GerritServerId String serverId,
      @GerritServerConfig Config config) {
    this.accountCache = accountCache;
    this.serverIdent = serverIdent;
    this.serverId = serverId;
    this.writeJson = config.getBoolean("notedb", "writeJson", true);
  }

  public PersonIdent newIdent(Account.Id authorId, Date when, PersonIdent serverIdent) {
    Optional<Account> author = accountCache.get(authorId).map(AccountState::getAccount);
    return new PersonIdent(
        author.map(Account::getName).orElseGet(() -> Account.getName(authorId)),
        authorId.get() + "@" + serverId,
        when,
        serverIdent.getTimeZone());
  }

  @VisibleForTesting
  public PersonIdent newIdent(Account author, Date when, PersonIdent serverIdent) {
    return new PersonIdent(
        author.getName(), author.getId().get() + "@" + serverId, when, serverIdent.getTimeZone());
  }

  public boolean getWriteJson() {
    return writeJson;
  }

  public Gson getGson() {
    return gson;
  }

  public String getServerId() {
    return serverId;
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
    byte[] psb = PATCH_SET.getBytes(UTF_8);
    byte[] bpsb = BASE_PATCH_SET.getBytes(UTF_8);
    byte[] bpn = PARENT_NUMBER.getBytes(UTF_8);

    RevId revId = new RevId(parseStringField(note, p, changeId, REVISION));
    String fileName = null;
    PatchSet.Id psId = null;
    boolean isForBase = false;
    Integer parentNumber = null;

    while (p.value < sizeOfNote) {
      boolean matchPs = match(note, p, psb);
      boolean matchBase = match(note, p, bpsb);
      if (matchPs) {
        fileName = null;
        psId = parsePsId(note, p, changeId, PATCH_SET);
        isForBase = false;
      } else if (matchBase) {
        fileName = null;
        psId = parsePsId(note, p, changeId, BASE_PATCH_SET);
        isForBase = true;
        if (match(note, p, bpn)) {
          parentNumber = parseParentNumber(note, p, changeId);
        }
      } else if (psId == null) {
        throw parseException(changeId, "missing %s or %s header", PATCH_SET, BASE_PATCH_SET);
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
    Change.Id changeId = psId.getParentKey();

    // Check if there is a new file.
    boolean newFile = (RawParseUtils.match(note, curr.value, FILE.getBytes(UTF_8))) != -1;
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
    Account.Id aId = parseAuthor(note, curr, changeId, AUTHOR);
    boolean hasRealAuthor =
        (RawParseUtils.match(note, curr.value, REAL_AUTHOR.getBytes(UTF_8))) != -1;
    Account.Id raId = null;
    if (hasRealAuthor) {
      raId = parseAuthor(note, curr, changeId, REAL_AUTHOR);
    }

    boolean hasParent = (RawParseUtils.match(note, curr.value, PARENT.getBytes(UTF_8))) != -1;
    String parentUUID = null;
    boolean unresolved = false;
    if (hasParent) {
      parentUUID = parseStringField(note, curr, changeId, PARENT);
    }
    boolean hasUnresolved =
        (RawParseUtils.match(note, curr.value, UNRESOLVED.getBytes(UTF_8))) != -1;
    if (hasUnresolved) {
      unresolved = parseBooleanField(note, curr, changeId, UNRESOLVED);
    }

    String uuid = parseStringField(note, curr, changeId, UUID);

    boolean hasTag = (RawParseUtils.match(note, curr.value, TAG.getBytes(UTF_8))) != -1;
    String tag = null;
    if (hasTag) {
      tag = parseStringField(note, curr, changeId, TAG);
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
    return new PatchSet.Id(changeId, patchSetId);
  }

  private static Integer parseParentNumber(byte[] note, MutableInteger curr, Change.Id changeId)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, PARENT_NUMBER, changeId);

    int start = RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    int parentNumber = RawParseUtils.parseBase10(note, start, i);
    int endOfLine = RawParseUtils.nextLF(note, curr.value);
    if (i.value != endOfLine - 1) {
      throw parseException(changeId, "could not parse %s", PARENT_NUMBER);
    }
    checkResult(parentNumber, "parent number", changeId);
    curr.value = endOfLine;
    return Integer.valueOf(parentNumber);
  }

  private static String parseFilename(byte[] note, MutableInteger curr, Change.Id changeId)
      throws ConfigInvalidException {
    checkHeaderLineFormat(note, curr, FILE, changeId);
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
    checkHeaderLineFormat(note, curr, LENGTH, changeId);
    int startOfLength = RawParseUtils.endOfFooterLineKey(note, curr.value) + 1;
    MutableInteger i = new MutableInteger();
    i.value = startOfLength;
    int commentLength = RawParseUtils.parseBase10(note, startOfLength, i);
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

  private void appendHeaderField(PrintWriter writer, String field, String value) {
    writer.print(field);
    writer.print(": ");
    writer.print(value);
    writer.print('\n');
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

  /**
   * Build a note that contains the metadata for and the contents of all of the comments in the
   * given comments.
   *
   * @param comments Comments to be written to the output stream, keyed by patch set ID; multiple
   *     patch sets are allowed since base revisions may be shared across patch sets. All of the
   *     comments must share the same RevId, and all the comments for a given patch set must have
   *     the same side.
   * @param out output stream to write to.
   */
  void buildNote(ListMultimap<Integer, Comment> comments, OutputStream out) {
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
        appendHeaderField(writer, side <= 0 ? BASE_PATCH_SET : PATCH_SET, Integer.toString(psId));
        if (side < 0) {
          appendHeaderField(writer, PARENT_NUMBER, Integer.toString(-side));
        }

        String currentFilename = null;

        for (Comment c : psComments) {
          checkArgument(
              revId.equals(c.revId),
              "All comments being added must have all the same RevId. The "
                  + "comment below does not have the same RevId as the others "
                  + "(%s).\n%s",
              revId,
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

    appendHeaderField(writer, UNRESOLVED, Boolean.toString(c.unresolved));
    appendHeaderField(writer, UUID, c.key.uuid);

    if (c.tag != null) {
      appendHeaderField(writer, TAG, c.tag);
    }

    byte[] messageBytes = c.message.getBytes(UTF_8);
    appendHeaderField(writer, LENGTH, Integer.toString(messageBytes.length));

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

  private static final CharMatcher INVALID_FOOTER_CHARS = CharMatcher.anyOf("\r\n\0");

  static String sanitizeFooter(String value) {
    // Remove characters that would confuse JGit's footer parser if they were
    // included in footer values, for example by splitting the footer block into
    // multiple paragraphs.
    //
    // One painful example: RevCommit#getShorMessage() might return a message
    // containing "\r\r", which RevCommit#getFooterLines() will treat as an
    // empty paragraph for the purposes of footer parsing.
    return INVALID_FOOTER_CHARS.trimAndCollapseFrom(value, ' ');
  }
}
