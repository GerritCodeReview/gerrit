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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class UniversalServerPluginProvider implements ServerPluginProvider {
  private static final Logger log = LoggerFactory.getLogger(UniversalServerPluginProvider.class);

  private final DynamicSet<ServerPluginProvider> serverPluginProviders;

  @Inject
  UniversalServerPluginProvider(DynamicSet<ServerPluginProvider> sf) {
    this.serverPluginProviders = sf;
  }

  @Override
  public ServerPlugin get(Path srcPath, FileSnapshot snapshot, PluginDescription pluginDescription)
      throws InvalidPluginException {
    return providerOf(srcPath).get(srcPath, snapshot, pluginDescription);
  }

  @Override
  public String getPluginName(Path srcPath) {
    return providerOf(srcPath).getPluginName(srcPath);
  }

  @Override
  public boolean handles(Path srcPath) {
    List<ServerPluginProvider> providers = providersForHandlingPlugin(srcPath);
    switch (providers.size()) {
      case 1:
        return true;
      case 0:
        return false;
      default:
        throw new MultipleProvidersForPluginException(srcPath, providers);
    }
  }

  @Override
  public String getProviderPluginName() {
    return "gerrit";
  }

  private ServerPluginProvider providerOf(Path srcPath) {
    List<ServerPluginProvider> providers = providersForHandlingPlugin(srcPath);
    switch (providers.size()) {
      case 1:
        return providers.get(0);
      case 0:
        throw new IllegalArgumentException(
            "No ServerPluginProvider found/loaded to handle plugin file "
                + srcPath.toAbsolutePath());
      default:
        throw new MultipleProvidersForPluginException(srcPath, providers);
    }
  }

  private List<ServerPluginProvider> providersForHandlingPlugin(final Path srcPath) {
    List<ServerPluginProvider> providers = new ArrayList<>();
    for (ServerPluginProvider serverPluginProvider : serverPluginProviders) {
      boolean handles = serverPluginProvider.handles(srcPath);
      log.debug(
          "File {} handled by {} ? => {}",
          srcPath,
          serverPluginProvider.getProviderPluginName(),
          handles);
      if (handles) {
        providers.add(serverPluginProvider);
      }
    }
    return providers;
  }
}
