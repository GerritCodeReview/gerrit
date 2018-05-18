// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.SubmitType.FAST_FORWARD_ONLY;
import static com.google.gerrit.extensions.client.SubmitType.MERGE_IF_NECESSARY;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ConflictKeyTest {
  @Test
  public void ffOnlyPreservesInputOrder() {
    ObjectId id1 = ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee");
    ObjectId id2 = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    ConflictKey id1First = ConflictKey.create(id1, id2, FAST_FORWARD_ONLY, true);
    ConflictKey id2First = ConflictKey.create(id2, id1, FAST_FORWARD_ONLY, true);

    assertThat(id1First).isEqualTo(new ConflictKey(id1, id2, FAST_FORWARD_ONLY, true));
    assertThat(id2First).isEqualTo(new ConflictKey(id2, id1, FAST_FORWARD_ONLY, true));
    assertThat(id1First).isNotEqualTo(id2First);
  }

  @Test
  public void nonFfOnlyNormalizesInputOrder() {
    ObjectId id1 = ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee");
    ObjectId id2 = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    ConflictKey id1First = ConflictKey.create(id1, id2, MERGE_IF_NECESSARY, true);
    ConflictKey id2First = ConflictKey.create(id2, id1, MERGE_IF_NECESSARY, true);
    ConflictKey expected = new ConflictKey(id1, id2, MERGE_IF_NECESSARY, true);

    assertThat(id1First).isEqualTo(expected);
    assertThat(id2First).isEqualTo(expected);
  }
}
