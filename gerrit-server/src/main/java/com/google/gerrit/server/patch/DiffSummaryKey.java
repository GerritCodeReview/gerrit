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

package com.google.gerrit.server.patch;

import static org.eclipse.jgit.lib.ObjectIdSerialization.readCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;

public class DiffSummaryKey implements Serializable {
  public static final long serialVersionUID = 1L;

  /** see PatchListKey#oldId */
  private transient ObjectId oldId;

  /** see PatchListKey#parentNum */
  private transient Integer parentNum;

  private transient ObjectId newId;
  private transient Whitespace whitespace;

  public static DiffSummaryKey fromPatchListKey(PatchListKey plk) {
    return new DiffSummaryKey(
        plk.getOldId(), plk.getParentNum(), plk.getNewId(), plk.getWhitespace());
  }

  private DiffSummaryKey(ObjectId oldId, Integer parentNum, ObjectId newId, Whitespace whitespace) {
    this.oldId = oldId;
    this.parentNum = parentNum;
    this.newId = newId;
    this.whitespace = whitespace;
  }

  PatchListKey toPatchListKey() {
    return new PatchListKey(oldId, parentNum, newId, whitespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(oldId, parentNum, newId, whitespace);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof DiffSummaryKey) {
      DiffSummaryKey k = (DiffSummaryKey) o;
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
    n.append("DiffSummaryKey[");
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

  private void writeObject(ObjectOutputStream out) throws IOException {
    writeCanBeNull(out, oldId);
    out.writeInt(parentNum == null ? 0 : parentNum);
    writeNotNull(out, newId);
    Character c = PatchListKey.WHITESPACE_TYPES.get(whitespace);
    if (c == null) {
      throw new IOException("Invalid whitespace type: " + whitespace);
    }
    out.writeChar(c);
  }

  private void readObject(ObjectInputStream in) throws IOException {
    oldId = readCanBeNull(in);
    int n = in.readInt();
    parentNum = n == 0 ? null : Integer.valueOf(n);
    newId = readNotNull(in);
    char t = in.readChar();
    whitespace = PatchListKey.WHITESPACE_TYPES.inverse().get(t);
    if (whitespace == null) {
      throw new IOException("Invalid whitespace type code: " + t);
    }
  }
}
