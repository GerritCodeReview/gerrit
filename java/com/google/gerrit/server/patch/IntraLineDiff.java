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

import static com.google.gerrit.server.ioutil.BasicSerialization.readEnum;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeEnum;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.CodedEnum;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.ReplaceEdit;

public class IntraLineDiff implements Serializable {
  static final long serialVersionUID = IntraLineDiffKey.serialVersionUID;

  public enum Status implements CodedEnum {
    EDIT_LIST('e'),
    DISABLED('D'),
    TIMEOUT('T'),
    ERROR('E');

    private final char code;

    Status(char code) {
      this.code = code;
    }

    @Override
    public char getCode() {
      return code;
    }
  }

  private transient Status status;
  private transient ImmutableList<Edit> edits;

  IntraLineDiff(Status status) {
    this.status = status;
    this.edits = ImmutableList.of();
  }

  IntraLineDiff(List<Edit> edits) {
    this.status = Status.EDIT_LIST;
    this.edits = ImmutableList.copyOf(edits);
  }

  public Status getStatus() {
    return status;
  }

  public ImmutableList<Edit> getEdits() {
    // Edits are mutable objects. As we serialize IntraLineDiff asynchronously in H2CacheImpl, we
    // must ensure that its state isn't modified until it was properly stored in the cache.
    return deepCopyEdits(edits);
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    writeEnum(out, status);
    writeVarInt32(out, edits.size());
    for (Edit e : edits) {
      writeEdit(out, e);

      if (e instanceof ReplaceEdit) {
        ReplaceEdit r = (ReplaceEdit) e;
        writeVarInt32(out, r.getInternalEdits().size());
        for (Edit i : r.getInternalEdits()) {
          writeEdit(out, i);
        }
      } else {
        writeVarInt32(out, 0);
      }
    }
  }

  private void readObject(ObjectInputStream in) throws IOException {
    status = readEnum(in, Status.values());
    int editCount = readVarInt32(in);
    Edit[] editArray = new Edit[editCount];
    for (int i = 0; i < editCount; i++) {
      editArray[i] = readEdit(in);

      int innerCount = readVarInt32(in);
      if (0 < innerCount) {
        Edit[] inner = new Edit[innerCount];
        for (int j = 0; j < innerCount; j++) {
          inner[j] = readEdit(in);
        }
        editArray[i] = new ReplaceEdit(editArray[i], toList(inner));
      }
    }
    edits = ImmutableList.copyOf(editArray);
  }

  private static ImmutableList<Edit> deepCopyEdits(List<Edit> edits) {
    return edits.stream().map(IntraLineDiff::copy).collect(ImmutableList.toImmutableList());
  }

  private static Edit copy(Edit edit) {
    if (edit instanceof ReplaceEdit) {
      return copy((ReplaceEdit) edit);
    }
    return new Edit(edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB());
  }

  private static ReplaceEdit copy(ReplaceEdit edit) {
    List<Edit> internalEdits =
        edit.getInternalEdits().stream().map(IntraLineDiff::copy).collect(Collectors.toList());
    return new ReplaceEdit(
        edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB(), internalEdits);
  }

  private static void writeEdit(OutputStream out, Edit e) throws IOException {
    writeVarInt32(out, e.getBeginA());
    writeVarInt32(out, e.getEndA());
    writeVarInt32(out, e.getBeginB());
    writeVarInt32(out, e.getEndB());
  }

  private static Edit readEdit(InputStream in) throws IOException {
    int beginA = readVarInt32(in);
    int endA = readVarInt32(in);
    int beginB = readVarInt32(in);
    int endB = readVarInt32(in);
    return new Edit(beginA, endA, beginB, endB);
  }

  private static List<Edit> toList(Edit[] l) {
    return Collections.unmodifiableList(Arrays.asList(l));
  }
}
