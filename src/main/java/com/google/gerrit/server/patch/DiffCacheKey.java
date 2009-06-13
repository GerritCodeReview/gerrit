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

import com.google.gerrit.client.patches.PatchScriptSettings;
import com.google.gerrit.client.patches.PatchScriptSettings.Whitespace;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.Project;

import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectIdSerialization;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class DiffCacheKey implements Serializable {
  private static final long serialVersionUID = 4L;

  private transient Project.NameKey projectKey;
  private transient ObjectId oldId;
  private transient ObjectId newId;
  private transient String fileName;
  private transient String sourceFileName;
  private transient Whitespace whitespace;

  public DiffCacheKey(final Project.NameKey pnk, final AnyObjectId a,
      final AnyObjectId b, final Patch p, final PatchScriptSettings s) {
    this(pnk, a, b, p.getFileName(), p.getSourceFileName(), s.getWhitespace());
  }

  public DiffCacheKey(final Project.NameKey p, final AnyObjectId a,
      final AnyObjectId b, final String dname, final String sname,
      final Whitespace ws) {
    projectKey = p;
    oldId = a != null ? a.copy() : null;
    newId = b.copy();
    fileName = dname;
    sourceFileName = sname;
    whitespace = ws;
  }

  public Project.NameKey getProjectKey() {
    return projectKey;
  }

  public ObjectId getOldId() {
    return oldId;
  }

  public ObjectId getNewId() {
    return newId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getSourceFileName() {
    return sourceFileName;
  }

  public Whitespace getWhitespace() {
    return whitespace;
  }

  @Override
  public int hashCode() {
    int h = projectKey.hashCode();

    if (oldId != null) {
      h *= 31;
      h += oldId.hashCode();
    }

    h *= 31;
    h += newId.hashCode();

    h *= 31;
    h += fileName.hashCode();

    if (sourceFileName != null) {
      h *= 31;
      h += sourceFileName.hashCode();
    }

    h *= 31;
    h += whitespace.ordinal();

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof DiffCacheKey) {
      final DiffCacheKey k = (DiffCacheKey) o;
      return projectKey.equals(k.projectKey) && eq(oldId, k.oldId)
          && eq(newId, k.newId) && eq(fileName, k.fileName)
          && eq(sourceFileName, k.sourceFileName) && whitespace == k.whitespace;
    }
    return false;
  }

  private static boolean eq(final ObjectId a, final ObjectId b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null && b != null && AnyObjectId.equals(a, b);
  }

  private static boolean eq(final String a, final String b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null && a.equals(b);
  }

  @Override
  public String toString() {
    final StringBuilder r = new StringBuilder();
    r.append("DiffCache[");
    r.append(whitespace.name());
    r.append(" ");
    r.append(projectKey.toString());
    r.append(" ");
    if (oldId != null) {
      r.append(oldId.name());
      r.append("..");
    }
    r.append(newId.name());
    r.append(" -- ");
    r.append(fileName);
    if (sourceFileName != null) {
      r.append(" ");
      r.append(sourceFileName);
    }
    r.append("]");
    return r.toString();
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    out.writeUTF(projectKey.get());
    ObjectIdSerialization.write(out, oldId);
    ObjectIdSerialization.write(out, newId);
    writeString(out, fileName);
    writeString(out, sourceFileName);
    writeString(out, whitespace.name());
  }

  private void readObject(final ObjectInputStream in) throws IOException {
    projectKey = new Project.NameKey(in.readUTF());
    oldId = ObjectIdSerialization.read(in);
    newId = ObjectIdSerialization.read(in);
    fileName = readString(in);
    sourceFileName = readString(in);
    whitespace = Whitespace.valueOf(readString(in));
  }

  private static void writeString(final ObjectOutputStream out, final String s)
      throws IOException {
    out.writeUTF(s != null ? s : "");
  }

  private static String readString(final ObjectInputStream in)
      throws IOException {
    final String s = in.readUTF();
    return s.length() > 0 ? s : null;
  }
}
