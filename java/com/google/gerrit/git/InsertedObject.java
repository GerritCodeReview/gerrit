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

package com.google.gerrit.git;

import com.google.auto.value.AutoValue;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;

@AutoValue
public abstract class InsertedObject {
  static InsertedObject create(int type, InputStream in) throws IOException {
    return create(type, ByteString.readFrom(in));
  }

  static InsertedObject create(int type, ByteString bs) {
    ObjectId id;
    try (ObjectInserter.Formatter fmt = new ObjectInserter.Formatter()) {
      id = fmt.idFor(type, bs.size(), bs.newInput());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return new AutoValue_InsertedObject(id, type, bs);
  }

  static InsertedObject create(int type, byte[] src, int off, int len) {
    return create(type, ByteString.copyFrom(src, off, len));
  }

  public abstract ObjectId id();

  public abstract int type();

  public abstract ByteString data();

  ObjectLoader newLoader() {
    return new ObjectLoader.SmallObject(type(), data().toByteArray());
  }
}
