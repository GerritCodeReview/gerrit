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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.eclipse.jgit.util.IO;

public class ObjectIdSerialization {
  public static void writeCanBeNull(OutputStream out, AnyObjectId id) throws IOException {
    if (id != null) {
      out.write((byte) 1);
      writeNotNull(out, id);
    } else {
      out.write((byte) 0);
    }
  }

  public static void writeNotNull(OutputStream out, AnyObjectId id) throws IOException {
    id.copyRawTo(out);
  }

  public static ObjectId readCanBeNull(InputStream in) throws IOException {
    switch (in.read()) {
      case 0:
        return null;
      case 1:
        return readNotNull(in);
      default:
        throw new IOException("Invalid flag before ObjectId");
    }
  }

  public static ObjectId readNotNull(InputStream in) throws IOException {
    final byte[] b = new byte[20];
    IO.readFully(in, b, 0, 20);
    return ObjectId.fromRaw(b);
  }

  private ObjectIdSerialization() {}
}
