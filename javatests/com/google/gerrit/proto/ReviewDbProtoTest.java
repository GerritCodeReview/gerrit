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

package com.google.gerrit.proto;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.gerrit.proto.reviewdb.Reviewdb.Change;
import com.google.gerrit.proto.reviewdb.Reviewdb.Change_Id;
import org.junit.Test;

public class ReviewDbProtoTest {
  @Test
  public void generatedProtoApi() {
    Change c1 = Change.newBuilder().setChangeId(Change_Id.newBuilder().setId(1234).build()).build();
    Change c2 = Change.newBuilder().setChangeId(Change_Id.newBuilder().setId(5678).build()).build();
    assertThat(c1).isEqualTo(c1);
    assertThat(c1).isNotEqualTo(c2);
  }
}
