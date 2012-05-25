// Copyright (C) 2010 The Android Open Source Project
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

import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public class IntraLineDiffKey implements Serializable {
  static final long serialVersionUID = 3L;

  private transient ObjectId aId;
  private transient ObjectId bId;

  // Transient data passed through on cache misses to the loader.

  private transient Text aText;
  private transient Text bText;
  private transient List<Edit> edits;

  private transient Project.NameKey projectKey;
  private transient ObjectId commit;
  private transient String path;

  public IntraLineDiffKey(ObjectId aId, Text aText, ObjectId bId, Text bText,
      List<Edit> edits, Project.NameKey projectKey, ObjectId commit, String path) {
    this.aId = aId;
    this.bId = bId;

    this.aText = aText;
    this.bText = bText;
    this.edits = edits;

    this.projectKey = projectKey;
    this.commit = commit;
    this.path = path;
  }

  Text getTextA() {
    return aText;
  }

  Text getTextB() {
    return bText;
  }

  List<Edit> getEdits() {
    return edits;
  }

  ObjectId getBlobA() {
    return aId;
  }

  ObjectId getBlobB() {
    return bId;
  }

  Project.NameKey getProject() {
    return projectKey;
  }

  ObjectId getCommit() {
    return commit;
  }

  String getPath() {
    return path;
  }

  @Override
  public int hashCode() {
    int h = 0;

    h = h * 31 + aId.hashCode();
    h = h * 31 + bId.hashCode();

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof IntraLineDiffKey) {
      final IntraLineDiffKey k = (IntraLineDiffKey) o;
      return aId.equals(k.aId) //
          && bId.equals(k.bId);
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder n = new StringBuilder();
    n.append("IntraLineDiffKey[");
    if (projectKey != null) {
      n.append(projectKey.get()).append(" ");
    }
    n.append(aId.name());
    n.append("..");
    n.append(bId.name());
    n.append("]");
    return n.toString();
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    writeNotNull(out, aId);
    writeNotNull(out, bId);
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    aId = readNotNull(in);
    bId = readNotNull(in);
  }
}
