// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.diff.IntraLineDiff;
import com.google.gerrit.server.diff.Text;
import com.google.gerrit.server.patch.PatchListCacheImpl.LargeObjectTombstone;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.junit.Test;

public class PatchListCacheTest {
  @Test
  public void largeObjectTombstoneCanBeSerializedAndDeserialized() throws Exception {
    // Serialize
    byte[] serializedObject;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream objectStream = new ObjectOutputStream(baos)) {
      objectStream.writeObject(new LargeObjectTombstone());
      serializedObject = baos.toByteArray();
      assertThat(serializedObject).isNotNull();
    }
    // Deserialize
    try (InputStream is = new ByteArrayInputStream(serializedObject);
        ObjectInputStream ois = new ObjectInputStream(is)) {
      assertThat(ois.readObject()).isInstanceOf(LargeObjectTombstone.class);
    }
  }
}
