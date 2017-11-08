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
import org.eclipse.jgit.lib.Config;

// TODO(aliceks): Add Javadoc descriptions to this file. Mention that this class must only be used
// by GroupConfig and that other classes have to use InternalGroupUpdate!
enum GroupConfigEntry {
  ID("id") {
    @Override
    void readFromConfig(InternalGroup.Builder group, Config config) {
      AccountGroup.Id id = new AccountGroup.Id(config.getInt(SECTION_NAME, super.keyName, 0));
      group.setId(id);
    }

    @Override
    void initNewConfig(Config config, InternalGroupCreation group) {
      AccountGroup.Id id = group.getId();
      config.setInt(SECTION_NAME, null, super.keyName, id.get());
    }

    @Override
    void updateConfigValue(Config config, InternalGroupUpdate groupUpdate) {
      // Updating the ID is not supported.
    }
  },
  NAME("name") {
    @Override
    void readFromConfig(InternalGroup.Builder group, Config config) {
      AccountGroup.NameKey name =
          new AccountGroup.NameKey(config.getString(SECTION_NAME, null, super.keyName));
      group.setNameKey(name);
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
    void readFromConfig(InternalGroup.Builder group, Config config) {
      String description = config.getString(SECTION_NAME, null, super.keyName);
      group.setDescription(description);
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
  // TODO(hiesel) or TODO(ekempin): Replace this property by a permission mechanism.
  OWNER_GROUP_UUID("ownerGroupUuid") {
    @Override
    void readFromConfig(InternalGroup.Builder group, Config config) {
      String ownerGroupUuid = config.getString(SECTION_NAME, null, super.keyName);
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
    void readFromConfig(InternalGroup.Builder group, Config config) {
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

  abstract void readFromConfig(InternalGroup.Builder group, Config config);

  abstract void initNewConfig(Config config, InternalGroupCreation group);

  abstract void updateConfigValue(Config config, InternalGroupUpdate groupUpdate);
}
