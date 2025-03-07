// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.common.collect.Iterables;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/** Represent {@link com.google.gerrit.entities.ChangeMessage} in the REST API. */
public class ChangeMessageInfo {
  public String id;
  public String tag;
  public AccountInfo author;
  public AccountInfo realAuthor;

  // TODO(issue-40014498): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp date;

  public String message;
  public Collection<AccountInfo> accountsInMessage;
  public Integer _revisionNumber;

  public ChangeMessageInfo() {}

  public ChangeMessageInfo(String message) {
    this.message = message;
  }

  // TODO(issue-40014498): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setDate(Instant when) {
    date = Timestamp.from(when);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ChangeMessageInfo) {
      ChangeMessageInfo cmi = (ChangeMessageInfo) o;
      return Objects.equals(id, cmi.id)
          && Objects.equals(tag, cmi.tag)
          && Objects.equals(author, cmi.author)
          && Objects.equals(realAuthor, cmi.realAuthor)
          && Objects.equals(date, cmi.date)
          && Objects.equals(message, cmi.message)
          && ((accountsInMessage == null && cmi.accountsInMessage == null)
              || (accountsInMessage != null
                  && cmi.accountsInMessage != null
                  && Iterables.elementsEqual(accountsInMessage, cmi.accountsInMessage)))
          && Objects.equals(_revisionNumber, cmi._revisionNumber);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, tag, author, realAuthor, date, message, accountsInMessage, _revisionNumber);
  }

  @Override
  public String toString() {
    return "ChangeMessageInfo{"
        + "id="
        + id
        + ", tag="
        + tag
        + ", author="
        + author
        + ", realAuthor="
        + realAuthor
        + ", date="
        + date
        + ", _revisionNumber"
        + _revisionNumber
        + ", message=["
        + message
        + "], accountsForTemplate="
        + accountsInMessage
        + "}";
  }
}
