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

package com.google.gerrit.common.data.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;

public class GroupReferenceSubject extends Subject<GroupReferenceSubject, GroupReference> {

  public static GroupReferenceSubject assertThat(GroupReference group) {
    return assertAbout(GroupReferenceSubject::new).that(group);
  }

  private GroupReferenceSubject(FailureMetadata metadata, GroupReference group) {
    super(metadata, group);
  }

  public ComparableSubject<?, AccountGroup.UUID> groupUuid() {
    isNotNull();
    GroupReference group = actual();
    return Truth.assertThat(group.getUUID()).named("groupUuid");
  }

  public StringSubject name() {
    isNotNull();
    GroupReference group = actual();
    return Truth.assertThat(group.getName()).named("name");
  }
}
