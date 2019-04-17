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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.Comment.Range;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * A comment left by a user on a specific line of a {@link Patch}.
 *
 * <p>New APIs should not expose this class.
 *
 * @see Comment
 */
public final class PatchLineComment {
  public static final char STATUS_DRAFT = 'd';
  public static final char STATUS_PUBLISHED = 'P';

  public enum Status {
    DRAFT(STATUS_DRAFT),

    PUBLISHED(STATUS_PUBLISHED);

    private final char code;

    Status(char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static Status forCode(char c) {
      for (Status s : Status.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  public static PatchLineComment from(
      Change.Id changeId, PatchLineComment.Status status, Comment c) {
    Patch.Key patchKey = new Patch.Key(new PatchSet.Id(changeId, c.key.patchSetId), c.key.filename);
    PatchLineComment plc =
        new PatchLineComment(
            patchKey, c.key.uuid, c.lineNbr, c.author.getId(), c.parentUuid, c.writtenOn);
    plc.setSide(c.side);
    plc.setMessage(c.message);
    if (c.range != null) {
      Comment.Range r = c.range;
      plc.setRange(new CommentRange(r.startLine, r.startChar, r.endLine, r.endChar));
    }
    plc.setTag(c.tag);
    plc.setRevId(new RevId(c.revId));
    plc.setStatus(status);
    plc.setRealAuthor(c.getRealAuthor().getId());
    plc.setUnresolved(c.unresolved);
    return plc;
  }

  protected Patch.Key patchKey;

  protected String uuid;

  /** Line number this comment applies to; it should display after the line. */
  protected int lineNbr;

  /** Who wrote this comment. */
  protected Account.Id author;

  /** When this comment was drafted. */
  protected Timestamp writtenOn;

  /** Current publication state of the comment; see {@link Status}. */
  protected char status;

  /** Which file is this comment; 0 is ancestor, 1 is new version. */
  protected short side;

  /** The text left by the user. */
  @Nullable protected String message;

  /** The parent of this comment, or null if this is the first comment on this line */
  @Nullable protected String parentUuid;

  @Nullable protected CommentRange range;

  @Nullable protected String tag;

  /** Real user that added this comment on behalf of the user recorded in {@link #author}. */
  @Nullable protected Account.Id realAuthor;

  /** True if this comment requires further action. */
  protected boolean unresolved;

  /** The RevId for the commit to which this comment is referring. */
  protected RevId revId;

  protected PatchLineComment() {}

  public PatchLineComment(
      Patch.Key patchKey, String uuid, int line, Account.Id a, String parentUuid, Timestamp when) {
    this.patchKey = patchKey;
    this.uuid = uuid;
    lineNbr = line;
    author = a;
    setParentUuid(parentUuid);
    setStatus(Status.DRAFT);
    setWrittenOn(when);
  }

  public PatchLineComment(PatchLineComment o) {
    patchKey = o.patchKey;
    uuid = o.uuid;
    lineNbr = o.lineNbr;
    author = o.author;
    realAuthor = o.realAuthor;
    writtenOn = o.writtenOn;
    status = o.status;
    side = o.side;
    message = o.message;
    parentUuid = o.parentUuid;
    revId = o.revId;
    if (o.range != null) {
      range =
          new CommentRange(
              o.range.getStartLine(),
              o.range.getStartCharacter(),
              o.range.getEndLine(),
              o.range.getEndCharacter());
    }
  }

  public PatchSet.Id getPatchSetId() {
    return patchKey.getParentKey();
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

  public Account.Id getRealAuthor() {
    return realAuthor != null ? realAuthor : getAuthor();
  }

  public void setRealAuthor(Account.Id id) {
    // Use null for same real author, as before the column was added.
    realAuthor = Objects.equals(getAuthor(), id) ? null : id;
  }

  public Timestamp getWrittenOn() {
    return writtenOn;
  }

  public Status getStatus() {
    return Status.forCode(status);
  }

  public void setStatus(Status s) {
    status = s.getCode();
  }

  public short getSide() {
    return side;
  }

  public void setSide(short s) {
    side = s;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String s) {
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
      range =
          new CommentRange(
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

  public void setUnresolved(Boolean unresolved) {
    this.unresolved = unresolved;
  }

  public Boolean getUnresolved() {
    return unresolved;
  }

  public Comment asComment(String serverId) {
    Comment c =
        new Comment(
            new Comment.Key(uuid, patchKey.getFileName(), patchKey.getParentKey().get()),
            author,
            writtenOn,
            side,
            message,
            serverId,
            unresolved);
    c.setRevId(revId);
    c.setRange(range);
    c.lineNbr = lineNbr;
    c.parentUuid = parentUuid;
    c.tag = tag;
    c.setRealAuthor(getRealAuthor());
    return c;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof PatchLineComment) {
      PatchLineComment c = (PatchLineComment) o;
      return Objects.equals(patchKey, c.patchKey)
          && Objects.equals(uuid, c.uuid)
          && Objects.equals(lineNbr, c.getLine())
          && Objects.equals(author, c.getAuthor())
          && Objects.equals(writtenOn, c.getWrittenOn())
          && Objects.equals(status, c.getStatus().getCode())
          && Objects.equals(side, c.getSide())
          && Objects.equals(message, c.getMessage())
          && Objects.equals(parentUuid, c.getParentUuid())
          && Objects.equals(range, c.getRange())
          && Objects.equals(revId, c.getRevId())
          && Objects.equals(tag, c.getTag())
          && Objects.equals(unresolved, c.getUnresolved());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(patchKey, uuid);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PatchLineComment{");
    builder.append("patchKey=").append(patchKey).append(',');
    builder.append("uuid=").append(uuid).append(',');
    builder.append("lineNbr=").append(lineNbr).append(',');
    builder.append("author=").append(author.get()).append(',');
    builder.append("realAuthor=").append(realAuthor != null ? realAuthor.get() : "").append(',');
    builder.append("writtenOn=").append(writtenOn.toString()).append(',');
    builder.append("status=").append(status).append(',');
    builder.append("side=").append(side).append(',');
    builder.append("message=").append(Objects.toString(message, "")).append(',');
    builder.append("parentUuid=").append(Objects.toString(parentUuid, "")).append(',');
    builder.append("range=").append(Objects.toString(range, "")).append(',');
    builder.append("revId=").append(revId != null ? revId.get() : "").append(',');
    builder.append("tag=").append(Objects.toString(tag, "")).append(',');
    builder.append("unresolved=").append(unresolved);
    builder.append('}');
    return builder.toString();
  }
}
