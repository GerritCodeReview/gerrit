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

/** A message attached to a {@link Change}. */
public final class ChangeMessage {
  public static class Id extends StringKey<Change.Id> {
    @Column
    protected Change.Id changeId;

    @Column(length = 40)
    protected String uuid;

    protected Id() {
      changeId = new Change.Id();
    }

    public Id(final Change.Id change, final String uuid) {
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
  }

  @Column(name = Column.NONE)
  protected Id key;

  /** Who wrote this comment; null if it was written by the Gerrit system. */
  @Column(name = "author_id", notNull = false)
  protected Account.Id author;

  /** When this comment was drafted. */
  @Column
  protected Timestamp writtenOn;

  /** The text left by the user. */
  @Column(notNull = false, length = Integer.MAX_VALUE)
  protected String message;

  protected ChangeMessage() {
  }

  public ChangeMessage(final ChangeMessage.Id k, final Account.Id a) {
    key = k;
    author = a;
    writtenOn = new Timestamp(System.currentTimeMillis());
  }

  public ChangeMessage.Id getKey() {
    return key;
  }

  /** If null, the message was written 'by the Gerrit system'. */
  public Account.Id getAuthor() {
    return author;
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
}
