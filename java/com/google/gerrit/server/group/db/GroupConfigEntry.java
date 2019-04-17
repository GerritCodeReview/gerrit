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

import com.google.common.base.Strings;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.InternalGroup;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * A basic property of a group.
 *
 * <p>Each property knows how to read and write its value from/to a JGit {@link Config} file.
 *
 * <p><strong>Warning:</strong> This class is a low-level API for properties of groups in NoteDb. It
 * may only be used by {@link GroupConfig}. Other classes should use {@link InternalGroupUpdate} to
 * modify the properties of a group.
 */
enum GroupConfigEntry {
  /**
   * The numeric ID of a group. This property is equivalent to {@link InternalGroup#getId()}.
   *
   * <p>This is a mandatory property which may not be changed.
   */
  ID("id") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
        throws ConfigInvalidException {
      int id = config.getInt(SECTION_NAME, super.keyName, -1);
      if (id < 0) {
        throw new ConfigInvalidException(
            String.format(
                "ID of the group %s must not be negative, found %d", groupUuid.get(), id));
      }
      group.setId(AccountGroup.id(id));
    }

    @Override
    void initNewConfig(Config config, InternalGroupCreation group) {
      AccountGroup.Id id = group.getId();

      // Do not use config.setInt(...) to write the group ID because config.setInt(...) persists
      // integers that can be expressed in KiB as a unit strings, e.g. "1024" is stored as "1k".
      // Using config.setString(...) ensures that group IDs are human readable.
      config.setString(SECTION_NAME, null, super.keyName, Integer.toString(id.get()));
    }

    @Override
    void updateConfigValue(Config config, InternalGroupUpdate groupUpdate) {
      // Updating the ID is not supported.
    }
  },
  /**
   * The name of a group. This property is equivalent to {@link InternalGroup#getNameKey()}.
   *
   * <p>This is a mandatory property.
   */
  NAME("name") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config) {
      String name = config.getString(SECTION_NAME, null, super.keyName);
      // An empty name is invalid in NoteDb; GroupConfig will refuse to store it and it might be
      // unusable in permissions. But, it was technically valid in the ReviewDb storage layer, and
      // the NoteDb migration converted such groups faithfully, so we need to be able to read them
      // back here.
      name = Strings.nullToEmpty(name);
      group.setNameKey(AccountGroup.nameKey(name));
    }

    @Override
    void initNewConfig(Config config, InternalGroupCreation group) {
      AccountGroup.NameKey name = group.getNameKey();
      config.setString(SECTION_NAME, null, super.keyName, name.get());
    }

    @Override
    void updateConfigValue(Config config, InternalGroupUpdate groupUpdate) {
      groupUpdate
          .getName()
          .ifPresent(name -> config.setString(SECTION_NAME, null, super.keyName, name.get()));
    }
  },
  /**
   * The description of a group. This property is equivalent to {@link
   * InternalGroup#getDescription()}.
   *
   * <p>It defaults to {@code null} if not set.
   */
  DESCRIPTION("description") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config) {
      String description = config.getString(SECTION_NAME, null, super.keyName);
      group.setDescription(Strings.emptyToNull(description));
    }

    @Override
    void initNewConfig(Config config, InternalGroupCreation group) {
      config.setString(SECTION_NAME, null, super.keyName, null);
    }

    @Override
    void updateConfigValue(Config config, InternalGroupUpdate groupUpdate) {
      groupUpdate
          .getDescription()
          .ifPresent(
              description ->
                  config.setString(
                      SECTION_NAME, null, super.keyName, Strings.emptyToNull(description)));
    }
  },
  /**
   * The owner of a group. This property is equivalent to {@link InternalGroup#getOwnerGroupUUID()}.
   *
   * <p>It defaults to the group itself if not set.
   */
  OWNER_GROUP_UUID("ownerGroupUuid") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
        throws ConfigInvalidException {
      String ownerGroupUuid = config.getString(SECTION_NAME, null, super.keyName);
      if (Strings.isNullOrEmpty(ownerGroupUuid)) {
        throw new ConfigInvalidException(
            String.format("Owner UUID of the group %s must be defined", groupUuid.get()));
      }
      group.setOwnerGroupUUID(AccountGroup.uuid(ownerGroupUuid));
    }

    @Override
    void initNewConfig(Config config, InternalGroupCreation group) {
      config.setString(SECTION_NAME, null, super.keyName, group.getGroupUUID().get());
    }

    @Override
    void updateConfigValue(Config config, InternalGroupUpdate groupUpdate) {
      groupUpdate
          .getOwnerGroupUUID()
          .ifPresent(
              ownerGroupUuid ->
                  config.setString(SECTION_NAME, null, super.keyName, ownerGroupUuid.get()));
    }
  },
  /**
   * A flag indicating the visibility of a group. This property is equivalent to {@link
   * InternalGroup#isVisibleToAll()}.
   *
   * <p>It defaults to {@code false} if not set.
   */
  VISIBLE_TO_ALL("visibleToAll") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config) {
      boolean visibleToAll = config.getBoolean(SECTION_NAME, super.keyName, false);
      group.setVisibleToAll(visibleToAll);
    }

    @Override
    void initNewConfig(Config config, InternalGroupCreation group) {
      config.setBoolean(SECTION_NAME, null, super.keyName, false);
    }

    @Override
    void updateConfigValue(Config config, InternalGroupUpdate groupUpdate) {
      groupUpdate
          .getVisibleToAll()
          .ifPresent(
              visibleToAll -> config.setBoolean(SECTION_NAME, null, super.keyName, visibleToAll));
    }
  };

  private static final String SECTION_NAME = "group";

  private final String keyName;

  GroupConfigEntry(String keyName) {
    this.keyName = keyName;
  }

  /**
   * Reads the corresponding property of this {@code GroupConfigEntry} from the given {@code
   * Config}. The read value is written to the corresponding property of {@code
   * InternalGroup.Builder}.
   *
   * @param groupUuid the UUID of the group (necessary for helpful error messages)
   * @param group the {@code InternalGroup.Builder} whose property value should be set
   * @param config the {@code Config} from which the value of the property should be read
   * @throws ConfigInvalidException if the property has an unexpected value
   */
  abstract void readFromConfig(
      AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
      throws ConfigInvalidException;

  /**
   * Initializes the corresponding property of this {@code GroupConfigEntry} in the given {@code
   * Config}.
   *
   * <p>If the specified {@code InternalGroupCreation} has an entry for the property, that value is
   * used. If not, the default value for the property is set. In any case, an existing entry for the
   * property in the {@code Config} will be overwritten.
   *
   * @param config a new {@code Config}, typically without an entry for the property
   * @param group an {@code InternalGroupCreation} detailing the initial value of mandatory group
   *     properties
   */
  abstract void initNewConfig(Config config, InternalGroupCreation group);

  /**
   * Updates the corresponding property of this {@code GroupConfigEntry} in the given {@code Config}
   * if the {@code InternalGroupUpdate} mentions a modification.
   *
   * <p>This call is a no-op if the {@code InternalGroupUpdate} doesn't contain a modification for
   * the property.
   *
   * @param config a {@code Config} for which the property should be updated
   * @param groupUpdate an {@code InternalGroupUpdate} detailing the modifications on a group
   */
  abstract void updateConfigValue(Config config, InternalGroupUpdate groupUpdate);
}
