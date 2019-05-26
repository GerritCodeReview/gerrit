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

import com.google.gerrit.entities.Patch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import org.junit.Test;

public class PatchListTest {
  @Test
  public void fileOrder() {
    String[] names = {
      "zzz", "def/g", "/!xxx", "abc", Patch.MERGE_LIST, "qrx", Patch.COMMIT_MSG,
    };
    String[] want = {
      Patch.COMMIT_MSG, Patch.MERGE_LIST, "/!xxx", "abc", "def/g", "qrx", "zzz",
    };

    Arrays.sort(names, 0, names.length, PatchList::comparePaths);
    assertThat(names).isEqualTo(want);
  }

  @Test
  public void fileOrderNoMerge() {
    String[] names = {
      "zzz", "def/g", "/!xxx", "abc", "qrx", Patch.COMMIT_MSG,
    };
    String[] want = {
      Patch.COMMIT_MSG, "/!xxx", "abc", "def/g", "qrx", "zzz",
    };

    Arrays.sort(names, 0, names.length, PatchList::comparePaths);
    assertThat(names).isEqualTo(want);
  }

  @Test
  public void largeObjectTombstoneCanBeSerializedAndDeserialized() throws Exception {
    // Serialize
    byte[] serializedObject;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream objectStream = new ObjectOutputStream(baos)) {
      objectStream.writeObject(new PatchListCacheImpl.LargeObjectTombstone());
      serializedObject = baos.toByteArray();
      assertThat(serializedObject).isNotNull();
    }
    // Deserialize
    try (InputStream is = new ByteArrayInputStream(serializedObject);
        ObjectInputStream ois = new ObjectInputStream(is)) {
      assertThat(ois.readObject()).isInstanceOf(PatchListCacheImpl.LargeObjectTombstone.class);
    }
  }
}
