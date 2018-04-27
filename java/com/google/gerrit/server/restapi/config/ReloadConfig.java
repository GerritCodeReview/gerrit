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

import com.google.gerrit.extensions.api.config.ConfigUpdateEntryInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ConfigUpdatedEvent;
import com.google.gerrit.server.config.ConfigUpdatedEvent.ConfigUpdateEntry;
import com.google.gerrit.server.config.ConfigUpdatedEvent.UpdateResult;
import com.google.gerrit.server.config.GerritServerConfigReloader;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    List<ConfigUpdatedEvent.Update> updates = config.reloadConfig();

    Map<String, List<ConfigUpdateEntryInfo>> reply = new HashMap<>();
    for (UpdateResult result : UpdateResult.values()) {
      reply.put(result.name().toLowerCase(), new ArrayList<>());
    }
    if (updates.isEmpty()) {
      return reply;
    }
    updates
        .stream()
        .forEach(u -> reply.get(u.getResult().name().toLowerCase()).addAll(toEntryInfos(u)));
    return reply;
  }

  private static List<ConfigUpdateEntryInfo> toEntryInfos(ConfigUpdatedEvent.Update update) {
    return update
        .getConfigUpdates()
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
