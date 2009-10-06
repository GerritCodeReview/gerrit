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

package org.eclipse.jgit.lib;

import static com.google.gerrit.server.ioutil.BasicSerialization.readFixInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeFixInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ObjectIdSerialization {
  public static void writeCanBeNull(final OutputStream out, final AnyObjectId id)
      throws IOException {
    if (id != null) {
      writeVarInt32(out, 1);
      writeNotNull(out, id);
    } else {
      writeVarInt32(out, 0);
    }
  }

  public static void writeNotNull(final OutputStream out, final AnyObjectId id)
      throws IOException {
    writeFixInt32(out, id.w1);
    writeFixInt32(out, id.w2);
    writeFixInt32(out, id.w3);
    writeFixInt32(out, id.w4);
    writeFixInt32(out, id.w5);
  }

  public static ObjectId readCanBeNull(final InputStream in) throws IOException {
    switch (readVarInt32(in)) {
      case 0:
        return null;
      case 1:
        return readNotNull(in);
      default:
        throw new IOException("Invalid flag before ObjectId");
    }
  }

  public static ObjectId readNotNull(final InputStream in) throws IOException {
    final int w1 = readFixInt32(in);
    final int w2 = readFixInt32(in);
    final int w3 = readFixInt32(in);
    final int w4 = readFixInt32(in);
    final int w5 = readFixInt32(in);
    return new ObjectId(w1, w2, w3, w4, w5);
  }

  private ObjectIdSerialization() {
  }
}
