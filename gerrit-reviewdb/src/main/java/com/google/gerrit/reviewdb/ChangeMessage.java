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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;

/** A message attached to a {@link Change}. */
public final class ChangeMessage {
  public static class Key extends StringKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Change.Id changeId;

    @Column(id = 2, length = 40)
    protected String uuid;

    protected Key() {
      changeId = new Change.Id();
    }

    public Key(final Change.Id change, final String uuid) {
      this.changeId = change;
      this.uuid = uuid;
    }

    @Override
    public Change.Id getParentKey() {
      return changeId;
    }

    @Override
    public String get() {
      return uuid;
    }

    @Override
    protected void set(String newValue) {
      uuid = newValue;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  /** Who wrote this comment; null if it was written by the Gerrit system. */
  @Column(id = 2, name = "author_id", notNull = false)
  protected Account.Id author;

  /** When this comment was drafted. */
  @Column(id = 3)
  protected Timestamp writtenOn;

  /** The text left by the user. */
  @Column(id = 4, notNull = false, length = Integer.MAX_VALUE)
  protected String message;

  /** Which patchset (if any) was this message generated from? */
  @Column(id = 5, notNull = false)
  protected PatchSet.Id patchset;

  protected ChangeMessage() {
  }

  public ChangeMessage(final ChangeMessage.Key k, final Account.Id a) {
    this(k, a, new Timestamp(System.currentTimeMillis()));
  }

  public ChangeMessage(final ChangeMessage.Key k, final Account.Id a,
      final Timestamp wo) {
    key = k;
    author = a;
    writtenOn = wo;
  }

  public ChangeMessage.Key getKey() {
    return key;
  }

  /** If null, the message was written 'by the Gerrit system'. */
  public Account.Id getAuthor() {
    return author;
  }

  public void setAuthor(final Account.Id accountId) {
    if (author != null) {
      throw new IllegalStateException("Cannot modify author once assigned");
    }
    author = accountId;
  }

  public Timestamp getWrittenOn() {
    return writtenOn;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String s) {
    message = s;
  }

  public PatchSet.Id getPatchSetId() {
    return patchset;
  }

  public void setPatchSetId(PatchSet.Id id) {
    patchset = id;
  }
}
