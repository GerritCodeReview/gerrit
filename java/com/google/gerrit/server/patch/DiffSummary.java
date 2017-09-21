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

import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;

import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class DiffSummary implements Serializable {
  private static final long serialVersionUID = DiffSummaryKey.serialVersionUID;

  private transient String[] paths;
  private transient int insertions;
  private transient int deletions;

  public DiffSummary(String[] paths, int insertions, int deletions) {
    this.paths = paths;
    this.insertions = insertions;
    this.deletions = deletions;
  }

  public List<String> getPaths() {
    return Collections.unmodifiableList(Arrays.asList(paths));
  }

  public ChangedLines getChangedLines() {
    return new ChangedLines(insertions, deletions);
  }

  private void writeObject(ObjectOutputStream output) throws IOException {
    writeVarInt32(output, insertions);
    writeVarInt32(output, deletions);
    writeVarInt32(output, paths.length);
    try (DeflaterOutputStream out = new DeflaterOutputStream(output)) {
      for (String p : paths) {
        writeString(out, p);
      }
    }
  }

  private void readObject(ObjectInputStream input) throws IOException {
    insertions = readVarInt32(input);
    deletions = readVarInt32(input);
    paths = new String[readVarInt32(input)];
    try (InflaterInputStream in = new InflaterInputStream(input)) {
      for (int i = 0; i < paths.length; i++) {
        paths[i] = readString(in);
      }
    }
  }
}
