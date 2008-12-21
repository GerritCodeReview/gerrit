// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;

/** A comment left by a user on a specific line of a {@link Patch}. */
public final class PatchLineComment {
  public static class Id extends StringKey<Patch.Id> {
    @Column(name = Column.NONE)
    protected Patch.Id patchId;

    @Column(length = 40)
    protected String uuid;

    protected Id() {
      patchId = new Patch.Id();
    }

    public Id(final Patch.Id p, final String uuid) {
      this.patchId = p;
      this.uuid = uuid;
    }

    @Override
    public Patch.Id getParentKey() {
      return patchId;
    }

    @Override
    public String get() {
      return uuid;
    }
  }

  protected static final char STATUS_DRAFT = 'd';
  protected static final char STATUS_PUBLISHED = 'P';

  public static enum Status {
    DRAFT(STATUS_DRAFT),

    PUBLISHED(STATUS_PUBLISHED);

    private final char code;

    private Status(final char c) {
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

  @Column(name = Column.NONE)
  protected Id key;

  /** Line number this comment applies to; it should display after the line. */
  @Column
  protected int lineNbr;

  /** Who wrote this comment. */
  @Column(name = "author_id")
  protected Account.Id author;

  /** When this comment was drafted. */
  @Column
  protected Timestamp writtenOn;

  /** Current publication state of the comment; see {@link Status}. */
  @Column
  protected char status;

  /** Which file is this comment; 0 is ancestor, 1 is new version. */
  @Column
  protected short side;

  /** The text left by the user. */
  @Column(notNull = false, length = Integer.MAX_VALUE)
  protected String message;

  protected PatchLineComment() {
  }

  public PatchLineComment(final PatchLineComment.Id id, final int line,
      final Account.Id a) {
    key = id;
    lineNbr = line;
    author = a;
    writtenOn = new Timestamp(System.currentTimeMillis());
    setStatus(Status.DRAFT);
  }

  public PatchLineComment.Id getKey() {
    return key;
  }

  public int getLine() {
    return lineNbr;
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
}
