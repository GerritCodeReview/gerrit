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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** List capabilities visible to the calling user. */
@Singleton
public class ListCapabilities implements RestReadView<ConfigResource> {
  private static final Logger log = LoggerFactory.getLogger(ListCapabilities.class);
  private static final Pattern PLUGIN_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");

  private final DynamicMap<CapabilityDefinition> pluginCapabilities;

  @Inject
  public ListCapabilities(DynamicMap<CapabilityDefinition> pluginCapabilities) {
    this.pluginCapabilities = pluginCapabilities;
  }

  @Override
  public Map<String, CapabilityInfo> apply(ConfigResource resource)
      throws IllegalAccessException, NoSuchFieldException {
    Map<String, CapabilityInfo> output = new TreeMap<>();
    collectCoreCapabilities(output);
    collectPluginCapabilities(output);
    return output;
  }

  private void collectCoreCapabilities(Map<String, CapabilityInfo> output)
      throws IllegalAccessException, NoSuchFieldException {
    Class<? extends CapabilityConstants> bundleClass = CapabilityConstants.get().getClass();
    CapabilityConstants c = CapabilityConstants.get();
    for (String id : GlobalCapability.getAllNames()) {
      String name = (String) bundleClass.getField(id).get(c);
      output.put(id, new CapabilityInfo(id, name));
    }
  }

  private void collectPluginCapabilities(Map<String, CapabilityInfo> output) {
    for (String pluginName : pluginCapabilities.plugins()) {
      if (!PLUGIN_NAME_PATTERN.matcher(pluginName).matches()) {
        log.warn(
            "Plugin name '{}' must match '{}' to use capabilities; rename the plugin",
            pluginName,
            PLUGIN_NAME_PATTERN.pattern());
        continue;
      }
      for (Map.Entry<String, Provider<CapabilityDefinition>> entry :
          pluginCapabilities.byPlugin(pluginName).entrySet()) {
        String id = String.format("%s-%s", pluginName, entry.getKey());
        output.put(id, new CapabilityInfo(id, entry.getValue().get().getDescription()));
      }
    }
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
