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

package com.google.gerrit.server.group.db;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.errorprone.annotations.InlineMe;
import com.google.gerrit.entities.AccountGroup;

/**
 * Definition of all properties necessary for a group creation.
 *
 * <p>An instance of {@link InternalGroupCreation} is a blueprint for a group which should be
 * created.
 *
 * @param id Defines the numeric ID the group should have
 * @param nameKey Defines the name the group should have
 * @param groupUUID Defines the UUID the group should have
 */
public record InternalGroupCreation(
    AccountGroup.Id id, AccountGroup.NameKey nameKey, AccountGroup.UUID groupUUID) {
  public InternalGroupCreation {
    requireNonNull(id, "id");
    requireNonNull(nameKey, "nameKey");
    requireNonNull(groupUUID, "groupUUID");
  }

  @InlineMe(replacement = "this.id()")
  public AccountGroup.Id getId() {
    return id();
  }

  @InlineMe(replacement = "this.nameKey()")
  public AccountGroup.NameKey getNameKey() {
    return nameKey();
  }

  @InlineMe(replacement = "this.groupUUID()")
  public AccountGroup.UUID getGroupUUID() {
    return groupUUID();
  }

  public static Builder builder() {
    return new AutoBuilder_InternalGroupCreation_Builder();
  }

  @AutoBuilder
  public abstract static class Builder {
    /** Defines the name the group should have */
    public abstract InternalGroupCreation.Builder setId(AccountGroup.Id id);

    /** Defines the name the group should have */
    public abstract InternalGroupCreation.Builder setNameKey(AccountGroup.NameKey name);

    /** Defines the UUID the group should have */
    public abstract InternalGroupCreation.Builder setGroupUUID(AccountGroup.UUID groupUUID);

    public abstract InternalGroupCreation build();
  }
}
