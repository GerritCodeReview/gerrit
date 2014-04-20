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
import com.google.gerrit.server.PluginUser;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;

@Singleton
class UniversalServerPluginProvider implements ServerPluginProvider {

  private final DynamicSet<ServerPluginProvider> serverPluginProviders;

  @Inject
  public UniversalServerPluginProvider(DynamicSet<ServerPluginProvider> sf) {
    this.serverPluginProviders = sf;
  }

  @Override
  public ServerPlugin get(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) throws InvalidPluginException {
    return providerOf(srcFile).get(name, srcFile, pluginUser, snapshot);
  }

  @Override
  public String getPluginName(File srcFile) {
    return providerOf(srcFile).getPluginName(srcFile);
  }

  private ServerPluginProvider providerOf(File srcFile) {
    for (ServerPluginProvider provider : serverPluginProviders) {
      if (provider.handles(srcFile)) {
        return provider;
      }
    }
    throw new IllegalArgumentException(srcFile.getAbsolutePath()
        + " is not a supported Gerrit plugin format");
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
