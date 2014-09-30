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

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Singleton
class UniversalServerPluginProvider implements ServerPluginProvider {
  private static final Logger log = LoggerFactory.getLogger(UniversalServerPluginProvider.class);

  private final DynamicSet<ServerPluginProvider> serverPluginProviders;

  @Inject
  UniversalServerPluginProvider(DynamicSet<ServerPluginProvider> sf) {
    this.serverPluginProviders = sf;
  }

  @Override
  public ServerPlugin get(File srcFile, FileSnapshot snapshot,
      PluginDescription pluginDescription) throws InvalidPluginException {
    return providerOf(srcFile).get(srcFile, snapshot, pluginDescription);
  }

  @Override
  public String getPluginName(File srcFile) {
    return providerOf(srcFile).getPluginName(srcFile);
  }

  @Override
  public boolean handles(File srcFile) {
    List<ServerPluginProvider> providers =
        providersForHandlingPlugin(srcFile);
    switch (providers.size()) {
      case 1:
        return true;
      case 0:
        return false;
      default:
        throw new MultipleProvidersForPluginException(srcFile, providers);
    }
  }

  @Override
  public String getProviderPluginName() {
    return "gerrit";
  }

  private ServerPluginProvider providerOf(File srcFile) {
    List<ServerPluginProvider> providers =
        providersForHandlingPlugin(srcFile);
    switch (providers.size()) {
      case 1:
        return providers.get(0);
      case 0:
        throw new IllegalArgumentException(
            "No ServerPluginProvider found/loaded to handle plugin file "
                + srcFile.getAbsolutePath());
      default:
        throw new MultipleProvidersForPluginException(srcFile, providers);
    }
  }

  private List<ServerPluginProvider> providersForHandlingPlugin(
      final File srcFile) {
    List<ServerPluginProvider> providers = new ArrayList<>();
    for (ServerPluginProvider serverPluginProvider : serverPluginProviders) {
      boolean handles = serverPluginProvider.handles(srcFile);
      log.debug("File {} handled by {} ? => {}", srcFile,
          serverPluginProvider.getProviderPluginName(), handles);
      if (handles) {
        providers.add(serverPluginProvider);
      }
    }
    return providers;
  }
}
