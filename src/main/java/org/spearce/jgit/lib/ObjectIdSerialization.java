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

package org.spearce.jgit.lib;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ObjectIdSerialization {
  public static void write(final ObjectOutputStream out, final AnyObjectId id)
      throws IOException {
    if (id != null) {
      out.writeBoolean(true);
      out.writeInt(id.w1);
      out.writeInt(id.w2);
      out.writeInt(id.w3);
      out.writeInt(id.w4);
      out.writeInt(id.w5);
    } else {
      out.writeBoolean(false);
    }
  }

  public static ObjectId read(final ObjectInputStream in) throws IOException {
    if (in.readBoolean()) {
      final int w1 = in.readInt();
      final int w2 = in.readInt();
      final int w3 = in.readInt();
      final int w4 = in.readInt();
      final int w5 = in.readInt();
      return new ObjectId(w1, w2, w3, w4, w5);
    } else {
      return null;
    }
  }

  private ObjectIdSerialization() {
  }
}
