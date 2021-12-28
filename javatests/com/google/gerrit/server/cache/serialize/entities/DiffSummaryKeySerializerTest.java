// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.DiffSummaryKey.Serializer;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Serializer test for {@link com.google.gerrit.server.patch.DiffSummaryKey}. */
public class DiffSummaryKeySerializerTest {
  @Test
  public void roundTrip() {
    DiffSummaryKey key =
        DiffSummaryKey.create(
            ObjectId.fromString("80d8a14e977423688cfb6fcd37e297f1c84bf623"),
            /* parentNum= */ 1,
            ObjectId.fromString("08003af59c806ac9320ebf30c13abb93665401d1"),
            Whitespace.IGNORE_NONE);

    assertThat(Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(key))).isEqualTo(key);
  }

  @Test
  public void roundTripWithNullableParent() {
    DiffSummaryKey key =
        DiffSummaryKey.create(
            ObjectId.fromString("80d8a14e977423688cfb6fcd37e297f1c84bf623"),
            /* parentNum= */ null,
            ObjectId.fromString("08003af59c806ac9320ebf30c13abb93665401d1"),
            Whitespace.IGNORE_NONE);

    assertThat(Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(key))).isEqualTo(key);
  }

  @Test
  public void roundTripWithNullableOldId() {
    DiffSummaryKey key =
        DiffSummaryKey.create(
            /* oldId= */ null,
            /* parentNum= */ 1,
            ObjectId.fromString("08003af59c806ac9320ebf30c13abb93665401d1"),
            Whitespace.IGNORE_NONE);

    assertThat(Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(key))).isEqualTo(key);
  }
}
