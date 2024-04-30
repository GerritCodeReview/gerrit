// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.common.ConvertibleToProto;
import java.time.Instant;
import java.util.Objects;

/**
 * This class represents inline human comments in NoteDb. This means it determines the JSON format
 * for inline comments in the revision notes that NoteDb uses to persist inline comments.
 *
 * <p>Changing fields in this class changes the storage format of inline comments in NoteDb and may
 * require a corresponding data migration (adding new optional fields is generally okay).
 *
 * <p>Consider updating {@link #getApproximateSize()} when adding/changing fields.
 */
@ConvertibleToProto
public class HumanComment extends Comment {

  public boolean unresolved;

  public HumanComment(
      Key key,
      Account.Id author,
      Instant writtenOn,
      short side,
      String message,
      String serverId,
      boolean unresolved) {
    super(key, author, writtenOn, side, message, serverId);
    this.unresolved = unresolved;
  }

  public HumanComment(HumanComment comment) {
    super(comment);
  }

  @Override
  public String toString() {
    return toStringHelper().add("unresolved", unresolved).toString();
  }

  @Override
  public boolean equals(Object otherObject) {
    if (!(otherObject instanceof HumanComment)) {
      return false;
    }
    if (!super.equals(otherObject)) {
      return false;
    }
    HumanComment otherComment = (HumanComment) otherObject;
    return unresolved == otherComment.unresolved;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), unresolved);
  }
}
