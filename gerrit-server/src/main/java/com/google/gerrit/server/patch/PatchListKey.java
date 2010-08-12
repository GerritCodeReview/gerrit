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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gwtorm.client.Column;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

import javax.annotation.Nullable;

public class PatchListKey {
  @Column(id = 1)
  protected String oldIdName;

  @Column(id = 2)
  protected String newIdName;

  @Column(id = 3)
  protected char whitespaceCode;

  private volatile ObjectId oldId;
  private volatile ObjectId newId;
  private volatile Whitespace whitespace;

  transient Project.NameKey projectKey; // not required to form the key

  protected PatchListKey() {
  }

  public PatchListKey(final Project.NameKey pk, final AnyObjectId a,
      final AnyObjectId b, final Whitespace ws) {
    projectKey = pk;
    oldId = a != null ? a.copy() : null;
    newId = b.copy();
    oldIdName = oldId != null ? oldId.name() : null;
    newIdName = newId.name();
    whitespace = ws;
    whitespaceCode = ws.getCode();
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
      whitespace = Whitespace.forCode(whitespaceCode);
    }
    return whitespace;
  }

  @Override
  public int hashCode() {
    int h = 0;

    if (oldIdName != null) {
      h = h * 31 + getOldId().hashCode();
    }

    h = h * 31 + getNewId().hashCode();
    h = h * 31 + whitespaceCode;

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof PatchListKey) {
      final PatchListKey k = (PatchListKey) o;
      return oldIdName != null ? oldIdName.equals(k.oldIdName)
          : k.oldIdName == null //
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
}
