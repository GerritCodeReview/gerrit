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

package com.google.gerrit.server.config;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** List capabilities visible to the calling user. */
public class ListCapabilities implements RestReadView<ConfigResource> {
  static final Logger log = LoggerFactory.getLogger(ListCapabilities.class);
  private final DynamicMap<CapabilityDefinition> pluginCapabilities;

  @Inject
  public ListCapabilities(DynamicMap<CapabilityDefinition> pluginCapabilities) {
    this.pluginCapabilities = pluginCapabilities;
  }

  @Override
  public Map<String, CapabilityInfo> apply(ConfigResource resource)
      throws AuthException, BadRequestException, ResourceConflictException,
      IllegalArgumentException, SecurityException, IllegalAccessException,
      NoSuchFieldException {
    Map<String, CapabilityInfo> output = Maps.newTreeMap();
    collectCoreCapabilities(output);
    collectPluginCapabilities(output);
    return output;
  }

  private void collectCoreCapabilities(Map<String, CapabilityInfo> output)
      throws IllegalAccessException, NoSuchFieldException {
    Class<? extends CapabilityConstants> bundleClass =
        CapabilityConstants.get().getClass();
    CapabilityConstants c = CapabilityConstants.get();
    for (String id : GlobalCapability.getAllNames()) {
      String name = (String) bundleClass.getField(id).get(c);
      output.put(id, new CapabilityInfo(id, name));
    }
  }

  private void collectPluginCapabilities(Map<String, CapabilityInfo> output) {
    for (String pluginName : pluginCapabilities.plugins()) {
      for (Map.Entry<String, Provider<CapabilityDefinition>> entry :
          pluginCapabilities.byPlugin(pluginName).entrySet()) {
        if (isPluginNameIsSane(pluginName)) {
          String id = String.format("%s-%s", pluginName, entry.getKey());
          output.put(id, new CapabilityInfo(
              id,
              entry.getValue().get().getDescription()));
        } else {
          // May be put this warning in PluginLoader
          // to warn as early as possible?
          log.warn(String.format("Plugin name is not sane: <%s> "
              + "dropping plugin owned capability %s; "
              + "consider to change the plugin file name.", pluginName,
              entry.getKey()));
        }
      }
    }
  }

  // Where to put this? I guessed the characters that are not supported.
  // May be i excluded too match?
  private static boolean isPluginNameIsSane(String pluginName) {
    return CharMatcher.noneOf("._#%$&§\"'?\\`:;,#()[]{}@|^°²")
        .matchesAllOf(pluginName);
  }

  public static class CapabilityInfo {
    final String kind = "gerritcodereview#capability";
    public String id;
    public String name;

    public CapabilityInfo(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
