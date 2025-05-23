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

package com.google.gerrit.entities;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * This class is a base class that can be extended by the different types of inline comment
 * entities.
 *
 * <p>Changing fields in this class changes the storage format of inline comments in NoteDb and may
 * require a corresponding data migration (adding new optional fields is generally okay).
 *
 * <p>Consider updating {@link #getCommentFieldApproximateSize()} when adding/changing fields.
 */
@ConvertibleToProto
public abstract class Comment {
  public enum Status {
    DRAFT('d'),

    PUBLISHED('P');

    private final char code;

    Status(char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    @Nullable
    public static Status forCode(char c) {
      for (Status s : Status.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  public static final class Key {
    public String uuid;
    public String filename;
    public int patchSetId;

    public Key(Key k) {
      this(k.uuid, k.filename, k.patchSetId);
    }

    public Key(String uuid, String filename, int patchSetId) {
      this.uuid = uuid;
      this.filename = filename;
      this.patchSetId = patchSetId;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("uuid", uuid)
          .add("filename", filename)
          .add("patchSetId", patchSetId)
          .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Key) {
        Key k = (Key) o;
        return Objects.equals(uuid, k.uuid)
            && Objects.equals(filename, k.filename)
            && patchSetId == k.patchSetId;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(uuid, filename, patchSetId);
    }
  }

  public static final class Identity {
    int id;

    public Identity(Account.Id id) {
      this.id = id.get();
    }

    public Account.Id getId() {
      return Account.id(id);
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Identity) {
        return id == ((Identity) o).id;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("id", id).toString();
    }
  }

  /**
   * The Range class defines continuous range of character.
   *
   * <p>The pair (startLine, startChar) defines the first character in the range. The pair (endLine,
   * endChar) defines the first character AFTER the range (i.e. it doesn't belong the range).
   * (endLine, endChar) must be a valid character inside text, except EOF case.
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>Zero length range: (startLine, startChar) = (endLine, endChar). Range defines insert
   *       position right before the (startLine, startChar) character (for {@link FixReplacement})
   *   <li>EOF case - range includes the last character in the file:
   *       <ul>
   *         <li>if a file ends with EOL mark, then (endLine, endChar) = (num_of_lines + 1, 0)
   *         <li>if a file doesn't end with EOL mark, then (endLine, endChar) = (num_of_lines,
   *             num_of_chars_in_last_line)
   *       </ul>
   * </ul>
   */
  public static final class Range implements Comparable<Range> {
    private static final Comparator<Range> RANGE_COMPARATOR =
        Comparator.<Range>comparingInt(range -> range.startLine)
            .thenComparingInt(range -> range.startChar)
            .thenComparingInt(range -> range.endLine)
            .thenComparingInt(range -> range.endChar);

    public int startLine; // 1-based
    public int startChar; // 0-based
    public int endLine; // 1-based
    public int endChar; // 0-based

    public Range(Range r) {
      this(r.startLine, r.startChar, r.endLine, r.endChar);
    }

    public Range(com.google.gerrit.extensions.client.Comment.Range r) {
      this(r.startLine, r.startCharacter, r.endLine, r.endCharacter);
    }

    public Range(int startLine, int startChar, int endLine, int endChar) {
      this.startLine = startLine;
      this.startChar = startChar;
      this.endLine = endLine;
      this.endChar = endChar;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Range) {
        Range r = (Range) o;
        return startLine == r.startLine
            && startChar == r.startChar
            && endLine == r.endLine
            && endChar == r.endChar;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(startLine, startChar, endLine, endChar);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("startLine", startLine)
          .add("startChar", startChar)
          .add("endLine", endLine)
          .add("endChar", endChar)
          .toString();
    }

    @Override
    public int compareTo(Range otherRange) {
      return RANGE_COMPARATOR.compare(this, otherRange);
    }
  }

  public Key key;

  /** The line number (1-based) to which the comment refers, or 0 for a file comment. */
  public int lineNbr;

  public Identity author;
  public Identity realAuthor;

  // TODO(issue-15525): Migrate this field from Timestamp to Instant
  public Timestamp writtenOn;

  public short side;
  public String message;
  public String parentUuid;
  public Range range;
  public String tag;

  @Nullable public List<FixSuggestion> fixSuggestions;

  /**
   * Hex commit SHA1 of the commit of the patchset to which this comment applies. Other classes call
   * this "commitId", but this class uses the old ReviewDb term "revId", and this field name is
   * serialized into JSON in NoteDb, so it can't easily be changed. Callers do not access this field
   * directly, and instead use the public getter/setter that wraps an ObjectId.
   */
  private String revId;

  public String serverId;

  public Comment(Comment c) {
    this(
        new Key(c.key),
        c.author.getId(),
        c.writtenOn.toInstant(),
        c.side,
        c.message,
        c.serverId,
        c.revId,
        c.parentUuid,
        c.tag,
        c.fixSuggestions,
        c.realAuthor == null ? null : c.realAuthor.getId());
    this.lineNbr = c.lineNbr;
    this.range = c.range != null ? new Range(c.range) : null;
  }

  public Comment(
      Key key, Account.Id author, Instant writtenOn, short side, String message, String serverId) {
    this(
        key,
        author,
        writtenOn,
        side,
        message,
        serverId,
        /* revId= */ null,
        /* parentUuid= */ null,
        /* tag= */ null,
        /* fixSuggestions= */ null,
        /* realAuthor= */ null);
  }

  public Comment(
      Key key,
      Account.Id author,
      Instant writtenOn,
      short side,
      String message,
      String serverId,
      @Nullable String revId,
      @Nullable String parentUuid,
      @Nullable String tag,
      @Nullable List<FixSuggestion> fixSuggestions,
      @Nullable Account.Id realAuthor) {
    this.key = key;
    this.author = new Comment.Identity(author);
    this.realAuthor = this.author;
    this.writtenOn = Timestamp.from(writtenOn);
    this.side = side;
    this.message = message;
    this.serverId = serverId;
    this.revId = revId;
    this.parentUuid = parentUuid;
    this.tag = tag;
    this.fixSuggestions = fixSuggestions;
    this.setRealAuthor(realAuthor);
  }

  public void setWrittenOn(Instant writtenOn) {
    this.writtenOn = Timestamp.from(writtenOn);
  }

  public void setLineNbrAndRange(
      Integer lineNbr, com.google.gerrit.extensions.client.Comment.Range range) {
    this.lineNbr = range != null ? range.endLine : lineNbr != null ? lineNbr : 0;
    if (range != null) {
      this.range = new Comment.Range(range);
    }
  }

  public void setRange(CommentRange range) {
    this.range = range != null ? range.asCommentRange() : null;
  }

  @Nullable
  public ObjectId getCommitId() {
    return revId != null ? ObjectId.fromString(revId) : null;
  }

  public void setCommitId(@Nullable AnyObjectId commitId) {
    this.revId = commitId != null ? commitId.name() : null;
  }

  public void setRealAuthor(Account.Id id) {
    realAuthor = id != null && id.get() != author.id ? new Comment.Identity(id) : null;
  }

  public Identity getRealAuthor() {
    return realAuthor != null ? realAuthor : author;
  }

  /**
   * Returns the comment's approximate size. This is used to enforce size limits and should
   * therefore include all unbounded fields (e.g. String-s).
   */
  protected int getCommentFieldApproximateSize() {
    return nullableLength(message, parentUuid, tag, revId, serverId)
        + (key != null ? nullableLength(key.filename, key.uuid) : 0);
  }

  public int getApproximateSize() {
    int approximateSize = getCommentFieldApproximateSize();
    approximateSize +=
        fixSuggestions != null
            ? fixSuggestions.stream().mapToInt(FixSuggestion::getApproximateSize).sum()
            : 0;
    return approximateSize;
  }

  static int nullableLength(String... strings) {
    int length = 0;
    for (String s : strings) {
      length += s == null ? 0 : s.length();
    }
    return length;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Comment)) {
      return false;
    }
    Comment c = (Comment) o;
    return Objects.equals(key, c.key)
        && lineNbr == c.lineNbr
        && Objects.equals(author, c.author)
        && Objects.equals(realAuthor, c.realAuthor)
        && Objects.equals(writtenOn, c.writtenOn)
        && side == c.side
        && Objects.equals(message, c.message)
        && Objects.equals(parentUuid, c.parentUuid)
        && Objects.equals(range, c.range)
        && Objects.equals(tag, c.tag)
        && Objects.equals(revId, c.revId)
        && Objects.equals(serverId, c.serverId)
        && Objects.equals(fixSuggestions, c.fixSuggestions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        key,
        lineNbr,
        author,
        realAuthor,
        writtenOn,
        side,
        message,
        parentUuid,
        range,
        tag,
        revId,
        serverId,
        fixSuggestions);
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected ToStringHelper toStringHelper() {
    return MoreObjects.toStringHelper(this)
        .add("key", key)
        .add("lineNbr", lineNbr)
        .add("author", author.getId())
        .add("realAuthor", realAuthor != null ? realAuthor.getId() : "")
        .add("writtenOn", writtenOn)
        .add("side", side)
        .add("message", Objects.toString(message, ""))
        .add("parentUuid", Objects.toString(parentUuid, ""))
        .add("range", Objects.toString(range, ""))
        .add("revId", Objects.toString(revId, ""))
        .add("tag", Objects.toString(tag, ""))
        .add("fixSuggestions", Objects.toString(fixSuggestions, ""));
  }
}
