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

// TODO(aliceks): Add Javadoc descriptions to this file. Mention that this class must only be used
// by GroupConfig and that other classes have to use InternalGroupUpdate!
enum GroupConfigEntry {
  ID("id") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
        throws ConfigInvalidException {
      int id = config.getInt(SECTION_NAME, super.keyName, -1);
      if (id < 0) {
        throw new ConfigInvalidException(
            String.format("ID of the group %s must not be negative", groupUuid.get()));
      }
      group.setId(new AccountGroup.Id(id));
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
  NAME("name") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
        throws ConfigInvalidException {
      String name = config.getString(SECTION_NAME, null, super.keyName);
      // An empty name is invalid in NoteDb; GroupConfig will refuse to store it and it might be
      // unusable in permissions. But, it was technically valid in the ReviewDb storage layer, and
      // the NoteDb migrated such groups faithfully, so we need to be able to read them back here.
      name = Strings.nullToEmpty(name);
      group.setNameKey(new AccountGroup.NameKey(name));
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
  OWNER_GROUP_UUID("ownerGroupUuid") {
    @Override
    void readFromConfig(AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
        throws ConfigInvalidException {
      String ownerGroupUuid = config.getString(SECTION_NAME, null, super.keyName);
      if (Strings.isNullOrEmpty(ownerGroupUuid)) {
        throw new ConfigInvalidException(
            String.format("Owner UUID of the group %s must be defined", groupUuid.get()));
      }
      group.setOwnerGroupUUID(new AccountGroup.UUID(ownerGroupUuid));
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

  abstract void readFromConfig(
      AccountGroup.UUID groupUuid, InternalGroup.Builder group, Config config)
      throws ConfigInvalidException;

  abstract void initNewConfig(Config config, InternalGroupCreation group);

  abstract void updateConfigValue(Config config, InternalGroupUpdate groupUpdate);
}
