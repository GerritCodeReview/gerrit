// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

public class PatchListKey implements Serializable {
  public static final long serialVersionUID = 24L;

  public static final BiMap<Whitespace, Character> WHITESPACE_TYPES =
      ImmutableBiMap.of(
          Whitespace.IGNORE_NONE, 'N',
          Whitespace.IGNORE_TRAILING, 'E',
          Whitespace.IGNORE_LEADING_AND_TRAILING, 'S',
          Whitespace.IGNORE_ALL, 'A');

  static {
    checkState(WHITESPACE_TYPES.size() == Whitespace.values().length);
  }

  public static PatchListKey againstDefaultBase(AnyObjectId newId, Whitespace ws) {
    return new PatchListKey(null, newId, ws);
  }

  public static PatchListKey againstParentNum(int parentNum, AnyObjectId newId, Whitespace ws) {
    return new PatchListKey(parentNum, newId, ws);
  }

  /**
   * Old patch-set ID
   *
   * <p>When null, it represents the Base of the newId for a non-merge commit.
   *
   * <p>When newId is a merge commit, null value of the oldId represents either the auto-merge
   * commit of the newId or a parent commit of the newId. These two cases are distinguished by the
   * parentNum.
   */
  private transient ObjectId oldId;

  /**
   * 1-based parent number when newId is a merge commit
   *
   * <p>For the auto-merge case this field is null.
   *
   * <p>Used only when oldId is null and newId is a merge commit
   */
  private transient Integer parentNum;

  private transient ObjectId newId;
  private transient Whitespace whitespace;

  public PatchListKey(AnyObjectId a, AnyObjectId b, Whitespace ws) {
    oldId = a != null ? a.copy() : null;
    newId = b.copy();
    whitespace = ws;
  }

  private PatchListKey(int parentNum, AnyObjectId b, Whitespace ws) {
    this.parentNum = Integer.valueOf(parentNum);
    newId = b.copy();
    whitespace = ws;
  }

  /** For use only by DiffSummaryKey. */
  PatchListKey(ObjectId oldId, Integer parentNum, ObjectId newId, Whitespace whitespace) {
    this.oldId = oldId;
    this.parentNum = parentNum;
    this.newId = newId;
    this.whitespace = whitespace;
  }

  /** Old side commit, or null to assume ancestor or combined merge. */
  @Nullable
  public ObjectId getOldId() {
    return oldId;
  }

  /** Parent number (old side) of the new side (merge) commit */
  @Nullable
  public Integer getParentNum() {
    return parentNum;
  }

  /** New side commit name. */
  public ObjectId getNewId() {
    return newId;
  }

  public Whitespace getWhitespace() {
    return whitespace;
  }

  @Override
  public int hashCode() {
    return Objects.hash(oldId, parentNum, newId, whitespace);
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PatchListKey) {
      PatchListKey k = (PatchListKey) o;
      return Objects.equals(oldId, k.oldId)
          && Objects.equals(parentNum, k.parentNum)
          && Objects.equals(newId, k.newId)
          && whitespace == k.whitespace;
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder n = new StringBuilder();
    n.append("PatchListKey[");
    n.append(oldId != null ? oldId.name() : "BASE");
    n.append("..");
    n.append(newId.name());
    n.append(" ");
    if (parentNum != null) {
      n.append(parentNum);
      n.append(" ");
    }
    n.append(whitespace.name());
    n.append("]");
    return n.toString();
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    writeCanBeNull(out, oldId);
    out.writeInt(parentNum == null ? 0 : parentNum);
    writeNotNull(out, newId);
    Character c = WHITESPACE_TYPES.get(whitespace);
    if (c == null) {
      throw new IOException("Invalid whitespace type: " + whitespace);
    }
    out.writeChar(c);
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    oldId = readCanBeNull(in);
    int n = in.readInt();
    parentNum = n == 0 ? null : Integer.valueOf(n);
    newId = readNotNull(in);
    char t = in.readChar();
    whitespace = WHITESPACE_TYPES.inverse().get(t);
    if (whitespace == null) {
      throw new IOException("Invalid whitespace type code: " + t);
    }
  }
}
