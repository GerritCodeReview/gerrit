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

package com.google.gerrit.server.api.plugins;

import com.google.gerrit.extensions.api.plugins.PluginApi;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.plugins.DisablePlugin;
import com.google.gerrit.server.plugins.EnablePlugin;
import com.google.gerrit.server.plugins.GetStatus;
import com.google.gerrit.server.plugins.PluginResource;
import com.google.gerrit.server.plugins.ReloadPlugin;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class PluginApiImpl implements PluginApi {
  public interface Factory {
    PluginApiImpl create(PluginResource resource);
  }

  private final GetStatus getStatus;
  private final EnablePlugin enable;
  private final DisablePlugin disable;
  private final ReloadPlugin reload;
  private final PluginResource resource;

  @Inject
  PluginApiImpl(
      GetStatus getStatus,
      EnablePlugin enable,
      DisablePlugin disable,
      ReloadPlugin reload,
      @Assisted PluginResource resource) {
    this.getStatus = getStatus;
    this.enable = enable;
    this.disable = disable;
    this.reload = reload;
    this.resource = resource;
  }

  @Override
  public PluginInfo get() throws RestApiException {
    return getStatus.apply(resource);
  }

  @Override
  public void enable() throws RestApiException {
    enable.apply(resource, new Input());
  }

  @Override
  public void disable() throws RestApiException {
    disable.apply(resource, new Input());
  }

  @Override
  public void reload() throws RestApiException {
    reload.apply(resource, new Input());
  }
}
