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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gwtorm.client.Column;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.annotation.Nullable;

public class PatchListKey implements Serializable {
  static final long serialVersionUID = 13L;

  @Column(id = 1)
  protected transient String oldIdName;

  @Column(id = 2)
  protected transient String newIdName;

  @Column(id = 3)
  protected transient char whitespaceChar;

  private transient ObjectId oldId;
  private transient ObjectId newId;
  private transient Whitespace whitespace;

  transient Project.NameKey projectKey; // not required to form the key

  protected PatchListKey(){
  }

  public PatchListKey(final Project.NameKey pk, final AnyObjectId a,
      final AnyObjectId b, final Whitespace ws) {
    projectKey = pk;
    oldId = a != null ? a.copy() : null;
    newId = b.copy();
    oldIdName = oldId != null ? oldId.name() : null;
    newIdName = newId.name();
    whitespace = ws;
    whitespaceChar = ws.getCode();
  }

  /** Old side commit, or null to assume ancestor or combined merge. */
  @Nullable
  public ObjectId getOldId() {
    if (oldId == null && oldIdName != null) {
      oldId = ObjectId.fromString(oldIdName);
    }
    return oldId;
  }

  /** New side commit name. */
  public ObjectId getNewId() {
    if (newId == null) {
      newId = ObjectId.fromString(newIdName);
    }
    return newId;
  }

  public Whitespace getWhitespace() {
    if (whitespace == null) {
      whitespace = Whitespace.forCode(whitespaceChar);
    }
    return whitespace;
  }

  @Override
  public int hashCode() {
    refreshObjectIds();

    int h = 0;

    if (oldId != null) {
      h = h * 31 + oldId.hashCode();
    }

    h = h * 31 + newId.hashCode();
    h = h * 31 + getWhitespace().name().hashCode();

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PatchListKey) {
      final PatchListKey k = (PatchListKey) o;
      return oldIdName.equals(k.oldIdName) //
          && newIdName.equals(k.newIdName) //
          && getWhitespace() == k.getWhitespace();
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
    n.append(oldIdName != null ? oldIdName : "BASE");
    n.append("..");
    n.append(newIdName);
    n.append(" ");
    n.append(getWhitespace().name());
    n.append("]");
    return n.toString();
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    refreshObjectIds();
    writeCanBeNull(out, oldId);
    writeNotNull(out, newId);
    writeEnum(out, getWhitespace());
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    oldId = readCanBeNull(in);
    newId = readNotNull(in);
    oldIdName = (oldId != null ? oldId.name() : null);
    newIdName = newId.name();
    whitespace = readEnum(in, Whitespace.values());
    whitespaceChar = whitespace.getCode();
  }

  private void refreshObjectIds(){
    if (oldId == null && oldIdName != null) {
      oldId = ObjectId.fromString(oldIdName);
    }
    if (newId == null) {
      newId = ObjectId.fromString(newIdName);
    }
  }
}
