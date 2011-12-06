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

import static com.google.gerrit.server.ioutil.BasicSerialization.readEnum;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeEnum;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.annotation.Nullable;

public class PatchListKey implements Serializable {
  static final long serialVersionUID = 16L;

  private transient ObjectId oldId;
  private transient ObjectId newId;
  private transient Whitespace whitespace;
  //if rebase happens, let rebase be transparent or not to user.
  private transient Boolean isRebaseTransparent;

  transient Project.NameKey projectKey; // not required to form the key

  public PatchListKey(final Project.NameKey pk, final AnyObjectId a,
      final AnyObjectId b, final Whitespace ws) {
    init(pk, a, b, ws, false);
  }

  public PatchListKey(final Project.NameKey pk, final AnyObjectId a,
      final AnyObjectId b, final Whitespace ws, final Boolean makeRebaseTransparent) {
    init(pk, a, b, ws, makeRebaseTransparent);
  }

  private void init(final Project.NameKey pk, final AnyObjectId a,
      final AnyObjectId b, final Whitespace ws, final Boolean rebaseTransparent) {
    projectKey = pk;
    oldId = a != null ? a.copy() : null;
    newId = b.copy();
    whitespace = ws;
    isRebaseTransparent = rebaseTransparent;
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

  public Boolean isRebaseTransparent() {
    return isRebaseTransparent;
  }

  @Override
  public int hashCode() {
    int h = 0;

    if (oldId != null) {
      h = h * 31 + oldId.hashCode();
    }

    h = h * 31 + newId.hashCode();
    h = h * 31 + whitespace.name().hashCode();
    h = h * 31 + isRebaseTransparent.hashCode();

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PatchListKey) {
      final PatchListKey k = (PatchListKey) o;
      return eq(oldId, k.oldId) //
          && eq(newId, k.newId) //
          && whitespace == k.whitespace
          && isRebaseTransparent == k.isRebaseTransparent();
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder n = new StringBuilder();
    n.append("PatchListKey[");
    if (projectKey != null) {
      n.append(projectKey.get());
      n.append(" ");
    }
    n.append(oldId != null ? oldId.name() : "BASE");
    n.append("..");
    n.append(newId.name());
    n.append(" ");
    n.append(whitespace.name());
    n.append(isRebaseTransparent.toString() );
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
    writeEnum(out, whitespace);
    out.writeBoolean(isRebaseTransparent);
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    oldId = readCanBeNull(in);
    newId = readNotNull(in);
    whitespace = readEnum(in, Whitespace.values());
    isRebaseTransparent = in.readBoolean();
  }
}
