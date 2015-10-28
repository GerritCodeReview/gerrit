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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class PatchListKey implements Serializable {
  static final long serialVersionUID = 19L;

  public static final BiMap<Whitespace, Character> WHITESPACE_TYPES = ImmutableBiMap.of(
      Whitespace.IGNORE_NONE, 'N',
      Whitespace.IGNORE_TRAILING, 'E',
      Whitespace.IGNORE_LEADING_AND_TRAILING, 'S',
      Whitespace.IGNORE_ALL, 'A');

  static {
    checkState(WHITESPACE_TYPES.size() == Whitespace.values().length);
  }

  private transient ObjectId oldId;
  private transient ObjectId newId;
  private transient Whitespace whitespace;

  public PatchListKey(AnyObjectId a, AnyObjectId b, Whitespace ws) {
    oldId = a != null ? a.copy() : null;
    newId = b.copy();
    whitespace = ws;
  }

  /** Old side commit, or null to assume ancestor or combined merge. */
  @Nullable
  public ObjectId getOldId() {
    return oldId;
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
    int h = 0;

    if (oldId != null) {
      h = h * 31 + oldId.hashCode();
    }

    h = h * 31 + newId.hashCode();
    h = h * 31 + whitespace.name().hashCode();

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PatchListKey) {
      final PatchListKey k = (PatchListKey) o;
      return eq(oldId, k.oldId) //
          && eq(newId, k.newId) //
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
    n.append(whitespace.name());
    n.append("]");
    return n.toString();
  }

  private static boolean eq(final ObjectId a, final ObjectId b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null && b != null && AnyObjectId.equals(a, b);
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    writeCanBeNull(out, oldId);
    writeNotNull(out, newId);
    Character c = WHITESPACE_TYPES.get(whitespace);
    if (c == null) {
      throw new IOException("Invalid whitespace type: " + whitespace);
    }
    out.writeChar(c);
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    oldId = readCanBeNull(in);
    newId = readNotNull(in);
    char t = in.readChar();
    whitespace = WHITESPACE_TYPES.inverse().get(t);
    if (whitespace == null) {
      throw new IOException("Invalid whitespace type code: " + t);
    }
  }
}
