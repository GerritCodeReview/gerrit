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

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.AccountGroup;

/**
 * Definition of all properties necessary for a group creation.
 *
 * <p>An instance of {@code InternalGroupCreation} is a blueprint for a group which should be
 * created.
 */
@AutoValue
public abstract class InternalGroupCreation {

  /** Defines the numeric ID the group should have. */
  public abstract AccountGroup.Id getId();

  /** Defines the name the group should have. */
  public abstract AccountGroup.NameKey getNameKey();

  /** Defines the UUID the group should have. */
  public abstract AccountGroup.UUID getGroupUUID();

  public static Builder builder() {
    return new AutoValue_InternalGroupCreation.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** @see #getId() */
    public abstract InternalGroupCreation.Builder setId(AccountGroup.Id id);

    /** @see #getNameKey() */
    public abstract InternalGroupCreation.Builder setNameKey(AccountGroup.NameKey name);

    /** @see #getGroupUUID() */
    public abstract InternalGroupCreation.Builder setGroupUUID(AccountGroup.UUID groupUuid);

    public abstract InternalGroupCreation build();
  }
}
