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

package com.google.gerrit.extensions.api.access;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

/** A global capability type permission used by a plugin. */
public class PluginPermission implements GlobalOrPluginPermission {
  private final String pluginName;
  private final String capability;
  private final boolean fallBackToAdmin;

  public PluginPermission(String pluginName, String capability) {
    this(pluginName, capability, true);
  }

  public PluginPermission(String pluginName, String capability, boolean fallBackToAdmin) {
    this.pluginName = checkNotNull(pluginName, "pluginName");
    this.capability = checkNotNull(capability, "capability");
    this.fallBackToAdmin = fallBackToAdmin;
  }

  public String pluginName() {
    return pluginName;
  }

  public String capability() {
    return capability;
  }

  public boolean fallBackToAdmin() {
    return fallBackToAdmin;
  }

  @Override
  public String describeForException() {
    return capability + " for plugin " + pluginName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pluginName, capability);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PluginPermission) {
      PluginPermission b = (PluginPermission) other;
      return pluginName.equals(b.pluginName) && capability.equals(b.capability);
    }
    return false;
  }

  @Override
  public String toString() {
    return "PluginPermission[plugin=" + pluginName + ", capability=" + capability + ']';
  }
}
