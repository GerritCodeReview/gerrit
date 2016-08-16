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

import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;

import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class IntraLineDiffKey implements Serializable {
  public static final long serialVersionUID = 4L;

  private transient Whitespace whitespace;
  private transient ObjectId aId;
  private transient ObjectId bId;

  public IntraLineDiffKey(ObjectId aId, ObjectId bId,
      Whitespace whitespace) {
    this.aId = aId;
    this.bId = bId;
    this.whitespace = whitespace;
  }

  public ObjectId getBlobA() {
    return aId;
  }

  public ObjectId getBlobB() {
    return bId;
  }

  public Whitespace getWhitespace() {
    return whitespace;
  }

  @Override
  public int hashCode() {
    int h = 0;

    h = h * 31 + aId.hashCode();
    h = h * 31 + bId.hashCode();
    h = h * 31 + whitespace.hashCode();

    return h;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof IntraLineDiffKey) {
      final IntraLineDiffKey k = (IntraLineDiffKey) o;
      return aId.equals(k.aId) //
          && bId.equals(k.bId) //
          && whitespace == k.whitespace;
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder n = new StringBuilder();
    n.append("IntraLineDiffKey[");
    n.append(aId.name());
    n.append("..");
    n.append(bId.name());
    n.append("]");
    return n.toString();
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    writeNotNull(out, aId);
    writeNotNull(out, bId);
    out.writeObject(whitespace);
  }

  private void readObject(final ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    aId = readNotNull(in);
    bId = readNotNull(in);
    whitespace = (Whitespace) in.readObject();
  }
}
