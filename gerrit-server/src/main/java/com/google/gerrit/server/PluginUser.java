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

package com.google.gerrit.server;

import com.google.gerrit.server.account.CapabilityControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** User identity for plugin code that needs an identity. */
public class PluginUser extends InternalUser {
  public interface Factory {
    PluginUser create(String pluginName);
  }

  private final String pluginName;

  @Inject
  protected PluginUser(
      CapabilityControl.Factory capabilityControlFactory,
      @Assisted String pluginName) {
    super(capabilityControlFactory);
    this.pluginName = pluginName;
  }

  @Override
  public String getUserName() {
    return "plugin " + pluginName;
  }

  @Override
  public String toString() {
    return "PluginUser[" + pluginName + "]";
  }
}
