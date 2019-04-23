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

package com.google.gerrit.reviewdb.client;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Objects;

/**
 * This class represents inline comments in NoteDb. This means it determines the JSON format for
 * inline comments in the revision notes that NoteDb uses to persist inline comments.
 *
 * <p>Changing fields in this class changes the storage format of inline comments in NoteDb and may
 * require a corresponding data migration (adding new optional fields is generally okay).
 *
 * <p>{@link PatchLineComment} historically represented comments in ReviewDb. There are a few
 * notable differences:
 *
 * <ul>
 *   <li>PatchLineComment knows the comment status (published or draft). For comments in NoteDb the
 *       status is determined by the branch in which they are stored (published comments are stored
 *       in the change meta ref; draft comments are store in refs/draft-comments branches in
 *       All-Users). Hence Comment doesn't need to contain the status, but the status is implicitly
 *       known by where the comments are read from.
 *   <li>PatchLineComment knows the change ID. For comments in NoteDb, the change ID is determined
 *       by the branch in which they are stored (the ref name contains the change ID). Hence Comment
 *       doesn't need to contain the change ID, but the change ID is implicitly known by where the
 *       comments are read from.
 * </ul>
 *
 * <p>For all utility classes and middle layer functionality using Comment over PatchLineComment is
 * preferred, as ReviewDb is gone so PatchLineComment is slated for deletion as well. This means
 * Comment should be used everywhere and only for storing inline comment in ReviewDb a conversion to
 * PatchLineComment is done. Converting Comments to PatchLineComments and vice verse is done by
 * CommentsUtil#toPatchLineComments(Change.Id, PatchLineComment.Status, Iterable) and
 * CommentsUtil#toComments(String, Iterable).
 */
public class Comment {
  public static class Key {
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
      return new StringBuilder()
          .append("Comment.Key{")
          .append("uuid=")
          .append(uuid)
          .append(',')
          .append("filename=")
          .append(filename)
          .append(',')
          .append("patchSetId=")
          .append(patchSetId)
          .append('}')
          .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Key) {
        Key k = (Key) o;
        return Objects.equals(uuid, k.uuid)
            && Objects.equals(filename, k.filename)
            && Objects.equals(patchSetId, k.patchSetId);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(uuid, filename, patchSetId);
    }
  }

  public static class Identity {
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
        return Objects.equals(id, ((Identity) o).id);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append("Comment.Identity{")
          .append("id=")
          .append(id)
          .append('}')
          .toString();
    }
  }

  public static class Range implements Comparable<Range> {
    private static final Comparator<Range> RANGE_COMPARATOR =
        Comparator.<Range>comparingInt(range -> range.startLine)
            .thenComparingInt(range -> range.startChar)
            .thenComparingInt(range -> range.endLine)
            .thenComparingInt(range -> range.endChar);

    public int startLine; // 1-based, inclusive
    public int startChar; // 0-based, inclusive
    public int endLine; // 1-based, exclusive
    public int endChar; // 0-based, exclusive

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
        return Objects.equals(startLine, r.startLine)
            && Objects.equals(startChar, r.startChar)
            && Objects.equals(endLine, r.endLine)
            && Objects.equals(endChar, r.endChar);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(startLine, startChar, endLine, endChar);
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append("Comment.Range{")
          .append("startLine=")
          .append(startLine)
          .append(',')
          .append("startChar=")
          .append(startChar)
          .append(',')
          .append("endLine=")
          .append(endLine)
          .append(',')
          .append("endChar=")
          .append(endChar)
          .append('}')
          .toString();
    }

    @Override
    public int compareTo(Range otherRange) {
      return RANGE_COMPARATOR.compare(this, otherRange);
    }
  }

  public Key key;
  public int lineNbr;
  public Identity author;
  protected Identity realAuthor;
  public Timestamp writtenOn;
  public short side;
  public String message;
  public String parentUuid;
  public Range range;
  public String tag;

  // Hex commit SHA1 of the commit of the patchset to which this comment applies.
  public String revId;
  public String serverId;
  public boolean unresolved;

  /**
   * Whether the comment was parsed from a JSON representation (false) or the legacy custom notes
   * format (true).
   */
  public transient boolean legacyFormat;

  public Comment(Comment c) {
    this(
        new Key(c.key),
        c.author.getId(),
        new Timestamp(c.writtenOn.getTime()),
        c.side,
        c.message,
        c.serverId,
        c.unresolved);
    this.lineNbr = c.lineNbr;
    this.realAuthor = c.realAuthor;
    this.range = c.range != null ? new Range(c.range) : null;
    this.tag = c.tag;
    this.revId = c.revId;
    this.unresolved = c.unresolved;
  }

  public Comment(
      Key key,
      Account.Id author,
      Timestamp writtenOn,
      short side,
      String message,
      String serverId,
      boolean unresolved) {
    this.key = key;
    this.author = new Comment.Identity(author);
    this.realAuthor = this.author;
    this.writtenOn = writtenOn;
    this.side = side;
    this.message = message;
    this.serverId = serverId;
    this.unresolved = unresolved;
  }

  public void setLineNbrAndRange(
      Integer lineNbr, com.google.gerrit.extensions.client.Comment.Range range) {
    this.lineNbr = lineNbr != null ? lineNbr : range != null ? range.endLine : 0;
    if (range != null) {
      this.range = new Comment.Range(range);
    }
  }

  public void setRange(CommentRange range) {
    this.range = range != null ? range.asCommentRange() : null;
  }

  public void setRevId(RevId revId) {
    this.revId = revId != null ? revId.get() : null;
  }

  public void setRealAuthor(Account.Id id) {
    realAuthor = id != null && id.get() != author.id ? new Comment.Identity(id) : null;
  }

  public Identity getRealAuthor() {
    return realAuthor != null ? realAuthor : author;
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
        && unresolved == c.unresolved;
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
        unresolved);
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append("Comment{")
        .append("key=")
        .append(key)
        .append(',')
        .append("lineNbr=")
        .append(lineNbr)
        .append(',')
        .append("author=")
        .append(author.getId().get())
        .append(',')
        .append("realAuthor=")
        .append(realAuthor != null ? realAuthor.getId().get() : "")
        .append(',')
        .append("writtenOn=")
        .append(writtenOn.toString())
        .append(',')
        .append("side=")
        .append(side)
        .append(',')
        .append("message=")
        .append(Objects.toString(message, ""))
        .append(',')
        .append("parentUuid=")
        .append(Objects.toString(parentUuid, ""))
        .append(',')
        .append("range=")
        .append(Objects.toString(range, ""))
        .append(',')
        .append("revId=")
        .append(revId != null ? revId : "")
        .append(',')
        .append("tag=")
        .append(Objects.toString(tag, ""))
        .append(',')
        .append("unresolved=")
        .append(unresolved)
        .append('}')
        .toString();
  }
}
