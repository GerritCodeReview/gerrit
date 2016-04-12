// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;
import java.util.Objects;

/** A comment left by a user on a specific line of a {@link Patch}. */
public final class PatchLineComment {
  public static class Key extends StringKey<Patch.Key> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected Patch.Key patchKey;

    @Column(id = 2, length = 40)
    protected String uuid;

    protected Key() {
      patchKey = new Patch.Key();
    }

    public Key(final Patch.Key p, final String uuid) {
      this.patchKey = p;
      this.uuid = uuid;
    }

    @Override
    public Patch.Key getParentKey() {
      return patchKey;
    }

    @Override
    public String get() {
      return uuid;
    }

    @Override
    public void set(String newValue) {
      uuid = newValue;
    }
  }

  public static final char STATUS_DRAFT = 'd';
  public static final char STATUS_PUBLISHED = 'P';

  public enum Status {
    DRAFT(STATUS_DRAFT),

    PUBLISHED(STATUS_PUBLISHED);

    private final char code;

    Status(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static Status forCode(final char c) {
      for (final Status s : Status.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  /** Line number this comment applies to; it should display after the line. */
  @Column(id = 2)
  protected int lineNbr;

  /** Who wrote this comment. */
  @Column(id = 3, name = "author_id")
  protected Account.Id author;

  /** When this comment was drafted. */
  @Column(id = 4)
  protected Timestamp writtenOn;

  /** Current publication state of the comment; see {@link Status}. */
  @Column(id = 5)
  protected char status;

  /** Which file is this comment; 0 is ancestor, 1 is new version. */
  @Column(id = 6)
  protected short side;

  /** The text left by the user. */
  @Column(id = 7, notNull = false, length = Integer.MAX_VALUE)
  protected String message;

  /**
   * The parent of this comment, or null if this is the first comment on this
   * line
   */
  @Column(id = 8, length = 40, notNull = false)
  protected String parentUuid;

  @Column(id = 9, notNull = false)
  protected CommentRange range;

  @Column(id = 10, notNull = false)
  protected String tag;

  /**
   * The RevId for the commit to which this comment is referring.
   *
   * Note that this field is not stored in the database. It is just provided
   * for users of this class to avoid a lookup when they don't have easy access
   * to a ReviewDb.
   */
  protected RevId revId;

  protected PatchLineComment() {
  }

  public PatchLineComment(PatchLineComment.Key id, int line, Account.Id a,
      String parentUuid, Timestamp when) {
    key = id;
    lineNbr = line;
    author = a;
    setParentUuid(parentUuid);
    setStatus(Status.DRAFT);
    setWrittenOn(when);
  }

  public PatchLineComment(PatchLineComment o) {
    key = o.key;
    lineNbr = o.lineNbr;
    author = o.author;
    writtenOn = o.writtenOn;
    status = o.status;
    side = o.side;
    message = o.message;
    parentUuid = o.parentUuid;
    revId = o.revId;
    if (o.range != null) {
      range = new CommentRange(
          o.range.getStartLine(),
          o.range.getStartCharacter(),
          o.range.getEndLine(),
          o.range.getEndCharacter());
    }
  }

  public PatchLineComment.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSetId() {
    return key.getParentKey().getParentKey();
  }

  public int getLine() {
    return lineNbr;
  }

  public void setLine(int line) {
    lineNbr = line;
  }

  public Account.Id getAuthor() {
    return author;
  }

  public Timestamp getWrittenOn() {
    return writtenOn;
  }

  public Status getStatus() {
    return Status.forCode(status);
  }

  public void setStatus(final Status s) {
    status = s.getCode();
  }

  public short getSide() {
    return side;
  }

  public void setSide(final short s) {
    side = s;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String s) {
    message = s;
  }

  public void setWrittenOn(Timestamp ts) {
    writtenOn = ts;
  }

  public String getParentUuid() {
    return parentUuid;
  }

  public void setParentUuid(String inReplyTo) {
    parentUuid = inReplyTo;
  }

  public void setRange(Range r) {
    if (r != null) {
      range = new CommentRange(
          r.startLine, r.startCharacter,
          r.endLine, r.endCharacter);
    } else {
      range = null;
    }
  }

  public void setRange(CommentRange r) {
    range = r;
  }

  public CommentRange getRange() {
    return range;
  }

  public void setRevId(RevId rev) {
    revId = rev;
  }

  public RevId getRevId() {
    return revId;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getTag() {
    return tag;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof PatchLineComment) {
      PatchLineComment c = (PatchLineComment) o;
      return Objects.equals(key, c.getKey())
          && Objects.equals(lineNbr, c.getLine())
          && Objects.equals(author, c.getAuthor())
          && Objects.equals(writtenOn, c.getWrittenOn())
          && Objects.equals(status, c.getStatus().getCode())
          && Objects.equals(side, c.getSide())
          && Objects.equals(message, c.getMessage())
          && Objects.equals(parentUuid, c.getParentUuid())
          && Objects.equals(range, c.getRange())
          && Objects.equals(revId, c.getRevId())
          && Objects.equals(tag, c.getTag());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PatchLineComment{");
    builder.append("key=").append(key).append(',');
    builder.append("lineNbr=").append(lineNbr).append(',');
    builder.append("author=").append(author.get()).append(',');
    builder.append("writtenOn=").append(writtenOn.toString()).append(',');
    builder.append("status=").append(status).append(',');
    builder.append("side=").append(side).append(',');
    builder.append("message=").append(Objects.toString(message, ""))
      .append(',');
    builder.append("parentUuid=").append(Objects.toString(parentUuid, ""))
      .append(',');
    builder.append("range=").append(Objects.toString(range, ""))
      .append(',');
    builder.append("revId=").append(revId != null ? revId.get() : "");
    builder.append("tag=").append(Objects.toString(tag, ""));
    builder.append('}');
    return builder.toString();
  }
}
