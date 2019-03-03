// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.CapabilityConstants;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PluginPermissionsUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/** List capabilities visible to the calling user. */
@Singleton
public class ListCapabilities implements RestReadView<ConfigResource> {
  private final PermissionBackend permissionBackend;
  private final PluginPermissionsUtil pluginPermissionsUtil;

  @Inject
  public ListCapabilities(
      PermissionBackend permissionBackend, PluginPermissionsUtil pluginPermissionsUtil) {
    this.permissionBackend = permissionBackend;
    this.pluginPermissionsUtil = pluginPermissionsUtil;
  }

  @Override
  public Map<String, CapabilityInfo> apply(ConfigResource resource)
      throws ResourceNotFoundException, IllegalAccessException, NoSuchFieldException {
    permissionBackend.checkUsesDefaultCapabilities();
    return ImmutableMap.<String, CapabilityInfo>builder()
        .putAll(collectCoreCapabilities())
        .putAll(collectPluginCapabilities())
        .build();
  }

  public Map<String, CapabilityInfo> collectPluginCapabilities() {
    return convertToPermissionInfos(pluginPermissionsUtil.collectPluginCapabilities());
  }

  private static ImmutableMap<String, CapabilityInfo> convertToPermissionInfos(
      ImmutableMap<String, String> permissionIdNames) {
    return permissionIdNames
        .entrySet()
        .stream()
        .collect(
            toImmutableMap(Map.Entry::getKey, e -> new CapabilityInfo(e.getKey(), e.getValue())));
  }

  private Map<String, CapabilityInfo> collectCoreCapabilities()
      throws IllegalAccessException, NoSuchFieldException {
    Map<String, CapabilityInfo> output = new HashMap<>();
    Class<? extends CapabilityConstants> bundleClass = CapabilityConstants.get().getClass();
    CapabilityConstants c = CapabilityConstants.get();
    for (String id : GlobalCapability.getAllNames()) {
      String name = (String) bundleClass.getField(id).get(c);
      output.put(id, new CapabilityInfo(id, name));
    }
    return output;
  }

  public static class CapabilityInfo {
    public String id;
    public String name;

    public CapabilityInfo(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
