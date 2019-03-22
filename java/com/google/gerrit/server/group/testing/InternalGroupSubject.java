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

package com.google.gerrit.server.group.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.DefaultSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.InternalGroup;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectId;

public class InternalGroupSubject extends Subject<InternalGroupSubject, InternalGroup> {

  public static InternalGroupSubject assertThat(InternalGroup group) {
    return assertAbout(internalGroups()).that(group);
  }

  public static Subject.Factory<InternalGroupSubject, InternalGroup> internalGroups() {
    return InternalGroupSubject::new;
  }

  private InternalGroupSubject(FailureMetadata metadata, InternalGroup actual) {
    super(metadata, actual);
  }

  public ComparableSubject<?, AccountGroup.UUID> groupUuid() {
    isNotNull();
    InternalGroup group = actual();
    return check("groupUuid()").that(group.getGroupUUID());
  }

  public ComparableSubject<?, AccountGroup.NameKey> nameKey() {
    isNotNull();
    InternalGroup group = actual();
    return check("nameKey()").that(group.getNameKey());
  }

  public StringSubject name() {
    isNotNull();
    InternalGroup group = actual();
    return check("name()").that(group.getName());
  }

  public Subject<DefaultSubject, Object> id() {
    isNotNull();
    InternalGroup group = actual();
    return check("id()").that(group.getId());
  }

  public StringSubject description() {
    isNotNull();
    InternalGroup group = actual();
    return check("description()").that(group.getDescription());
  }

  public ComparableSubject<?, AccountGroup.UUID> ownerGroupUuid() {
    isNotNull();
    InternalGroup group = actual();
    return check("ownerGroupUuid()").that(group.getOwnerGroupUUID());
  }

  public BooleanSubject visibleToAll() {
    isNotNull();
    InternalGroup group = actual();
    return check("visibleToAll()").that(group.isVisibleToAll());
  }

  public ComparableSubject<?, Timestamp> createdOn() {
    isNotNull();
    InternalGroup group = actual();
    return check("createdOn()").that(group.getCreatedOn());
  }

  public IterableSubject members() {
    isNotNull();
    InternalGroup group = actual();
    return check("members()").that(group.getMembers());
  }

  public IterableSubject subgroups() {
    isNotNull();
    InternalGroup group = actual();
    return check("subgroups()").that(group.getSubgroups());
  }

  public ComparableSubject<?, ObjectId> refState() {
    isNotNull();
    InternalGroup group = actual();
    return check("refState()").that(group.getRefState());
  }
}
