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

package com.google.gerrit.server.restapi.config;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.api.config.ConfigUpdateEntryInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ConfigUpdatedEvent.ConfigUpdateEntry;
import com.google.gerrit.server.config.ConfigUpdatedEvent.UpdateResult;
import com.google.gerrit.server.config.GerritServerConfigReloader;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReloadConfig implements RestModifyView<ConfigResource, Input> {

  private GerritServerConfigReloader config;
  private PermissionBackend permissions;

  @Inject
  ReloadConfig(GerritServerConfigReloader config, PermissionBackend permissions) {
    this.config = config;
    this.permissions = permissions;
  }

  @Override
  public Map<String, List<ConfigUpdateEntryInfo>> apply(ConfigResource resource, Input input)
      throws RestApiException, PermissionBackendException {
    permissions.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    Multimap<UpdateResult, ConfigUpdateEntry> updates = config.reloadConfig();
    if (updates.isEmpty()) {
      return Collections.emptyMap();
    }
    return updates
        .asMap()
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                e -> e.getKey().name().toLowerCase(), e -> toEntryInfos(e.getValue())));
  }

  private static List<ConfigUpdateEntryInfo> toEntryInfos(
      Collection<ConfigUpdateEntry> updateEntries) {
    return updateEntries
        .stream()
        .map(ReloadConfig::toConfigUpdateEntryInfo)
        .collect(toImmutableList());
  }

  private static ConfigUpdateEntryInfo toConfigUpdateEntryInfo(ConfigUpdateEntry e) {
    ConfigUpdateEntryInfo uei = new ConfigUpdateEntryInfo();
    uei.configKey = e.key.toString();
    uei.oldValue = e.oldVal;
    uei.newValue = e.newVal;
    return uei;
  }
}
