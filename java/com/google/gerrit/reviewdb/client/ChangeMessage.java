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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Objects;

/** A message attached to a {@link Change}. */
public final class ChangeMessage {
  public static Key key(Change.Id changeId, String uuid) {
    return new AutoValue_ChangeMessage_Key(changeId, uuid);
  }

  @AutoValue
  public abstract static class Key {
    public abstract Change.Id changeId();

    public abstract String uuid();
  }

  protected Key key;

  /** Who wrote this comment; null if it was written by the Gerrit system. */
  @Nullable protected Account.Id author;

  /** When this comment was drafted. */
  protected Timestamp writtenOn;

  /** The text left by the user. */
  @Nullable protected String message;

  /** Which patchset (if any) was this message generated from? */
  @Nullable protected PatchSet.Id patchset;

  /** Tag associated with change message */
  @Nullable protected String tag;

  /** Real user that added this message on behalf of the user recorded in {@link #author}. */
  @Nullable protected Account.Id realAuthor;

  protected ChangeMessage() {}

  public ChangeMessage(final ChangeMessage.Key k, Account.Id a, Timestamp wo, PatchSet.Id psid) {
    key = k;
    author = a;
    writtenOn = wo;
    patchset = psid;
  }

  public ChangeMessage.Key getKey() {
    return key;
  }

  /** If null, the message was written 'by the Gerrit system'. */
  public Account.Id getAuthor() {
    return author;
  }

  public void setAuthor(Account.Id accountId) {
    if (author != null) {
      throw new IllegalStateException("Cannot modify author once assigned");
    }
    author = accountId;
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

  public void setWrittenOn(Timestamp ts) {
    writtenOn = ts;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String s) {
    message = s;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public PatchSet.Id getPatchSetId() {
    return patchset;
  }

  public void setPatchSetId(PatchSet.Id id) {
    patchset = id;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ChangeMessage)) {
      return false;
    }
    ChangeMessage m = (ChangeMessage) o;
    return Objects.equals(key, m.key)
        && Objects.equals(author, m.author)
        && Objects.equals(writtenOn, m.writtenOn)
        && Objects.equals(message, m.message)
        && Objects.equals(patchset, m.patchset)
        && Objects.equals(tag, m.tag)
        && Objects.equals(realAuthor, m.realAuthor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, author, writtenOn, message, patchset, tag, realAuthor);
  }

  @Override
  public String toString() {
    return "ChangeMessage{"
        + "key="
        + key
        + ", author="
        + author
        + ", realAuthor="
        + realAuthor
        + ", writtenOn="
        + writtenOn
        + ", patchset="
        + patchset
        + ", tag="
        + tag
        + ", message=["
        + message
        + "]}";
  }
}
