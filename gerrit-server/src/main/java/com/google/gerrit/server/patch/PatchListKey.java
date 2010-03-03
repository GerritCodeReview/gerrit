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

import com.google.gerrit.common.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.reviewdb.Project;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.annotation.Nullable;

public class PatchListKey implements Serializable {
  static final long serialVersionUID = 12L;

  private transient ObjectId oldId;
  private transient ObjectId newId;
  private transient Whitespace whitespace;

  transient Project.NameKey projectKey; // not required to form the key

  public PatchListKey(final Project.NameKey pk, final AnyObjectId a,
      final AnyObjectId b, final Whitespace ws) {
    projectKey = pk;
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
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    oldId = readCanBeNull(in);
    newId = readNotNull(in);
    whitespace = readEnum(in, Whitespace.values());
  }
}
