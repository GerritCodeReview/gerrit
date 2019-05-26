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
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.group.InternalGroup;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectId;

public class InternalGroupSubject extends Subject {

  public static InternalGroupSubject assertThat(InternalGroup group) {
    return assertAbout(internalGroups()).that(group);
  }

  public static Subject.Factory<InternalGroupSubject, InternalGroup> internalGroups() {
    return InternalGroupSubject::new;
  }

  private final InternalGroup group;

  private InternalGroupSubject(FailureMetadata metadata, InternalGroup group) {
    super(metadata, group);
    this.group = group;
  }

  public ComparableSubject<AccountGroup.UUID> groupUuid() {
    isNotNull();
    return check("getGroupUUID()").that(group.getGroupUUID());
  }

  public ComparableSubject<AccountGroup.NameKey> nameKey() {
    isNotNull();
    return check("getNameKey()").that(group.getNameKey());
  }

  public StringSubject name() {
    isNotNull();
    return check("getName()").that(group.getName());
  }

  public Subject id() {
    isNotNull();
    return check("getId()").that(group.getId());
  }

  public StringSubject description() {
    isNotNull();
    return check("getDescription()").that(group.getDescription());
  }

  public ComparableSubject<AccountGroup.UUID> ownerGroupUuid() {
    isNotNull();
    return check("getOwnerGroupUUID()").that(group.getOwnerGroupUUID());
  }

  public BooleanSubject visibleToAll() {
    isNotNull();
    return check("isVisibleToAll()").that(group.isVisibleToAll());
  }

  public ComparableSubject<Timestamp> createdOn() {
    isNotNull();
    return check("getCreatedOn()").that(group.getCreatedOn());
  }

  public IterableSubject members() {
    isNotNull();
    return check("getMembers()").that(group.getMembers());
  }

  public IterableSubject subgroups() {
    isNotNull();
    return check("getSubgroups()").that(group.getSubgroups());
  }

  public ComparableSubject<ObjectId> refState() {
    isNotNull();
    return check("getRefState()").that(group.getRefState());
  }
}
