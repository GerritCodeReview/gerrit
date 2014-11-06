// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigEntries;
import com.google.gerrit.server.git.ProjectLevelConfig;
import com.google.inject.Singleton;

@Singleton
public class GetPluginConfig implements RestReadView<PluginConfigResource> {

  @Override
  public ConfigEntries apply(PluginConfigResource rsrc) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    ProjectLevelConfig cfg = rsrc.getConfig();
    if (!rsrc.getControl().isOwner() || cfg == null) {
      throw new ResourceNotFoundException(rsrc.getName());
    }
    return ConfigEntries.fromConfig(cfg.get(), rsrc.getFileName());
  }
}
