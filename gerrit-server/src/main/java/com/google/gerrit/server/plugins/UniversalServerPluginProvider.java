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

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.PluginUser;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;

@Singleton
class UniversalServerPluginProvider implements ServerPluginProvider {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(UniversalServerPluginProvider.class);

  private final DynamicSet<ServerPluginProvider> serverPluginProviders;

  @Inject
  public UniversalServerPluginProvider(DynamicSet<ServerPluginProvider> sf) {
    this.serverPluginProviders = sf;
  }

  @Override
  public ServerPlugin get(File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot, String pluginCanonicalWebUrl, File pluginDataDir)
      throws InvalidPluginException {
    return providerOf(srcFile).get(srcFile, pluginUser, snapshot,
        pluginCanonicalWebUrl, pluginDataDir);
  }

  @Override
  public String getPluginName(File srcFile) {
    return providerOf(srcFile).getPluginName(srcFile);
  }

  private ServerPluginProvider providerOf(final File srcFile) {
    Iterable<ServerPluginProvider> providerHandlers =
        Iterables.filter(serverPluginProviders,
            new com.google.common.base.Predicate<ServerPluginProvider>() {
              @Override
              public boolean apply(ServerPluginProvider input) {
                boolean handles = input.handles(srcFile);
                log.debug("File {} handled by {} ? => {}", srcFile, input,
                    handles);
                return handles;
              }
            });

    Iterator<ServerPluginProvider> providerIter = providerHandlers.iterator();
    ServerPluginProvider providerMatch;
    if (providerIter.hasNext()) {
      providerMatch = providerIter.next();

      if (providerIter.hasNext()) {
        throw new IllegalArgumentException(srcFile.getAbsolutePath()
            + " is claimed to be handled by more than one plugin provider: "
            + providerHandlers);
      }
    } else {
      throw new IllegalArgumentException(srcFile.getAbsolutePath()
          + " is not a supported Gerrit plugin format");
    }

    return providerMatch;
  }

  @Override
  public boolean handles(File srcFile) {
    for (ServerPluginProvider scriptingFactory : serverPluginProviders) {
      if (scriptingFactory.handles(srcFile)) {
        return true;
      }
    }
    return false;
  }
}
