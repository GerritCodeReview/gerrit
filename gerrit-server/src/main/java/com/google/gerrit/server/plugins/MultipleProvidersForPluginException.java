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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import java.io.File;

class MultipleProvidersForPluginException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  MultipleProvidersForPluginException(File pluginSrcFile,
      Iterable<ServerPluginProvider> providersHandlers) {
    super(pluginSrcFile.getAbsolutePath()
        + " is claimed to be handled by more than one plugin provider: "
        + providersListToString(providersHandlers));
  }

  private static String providersListToString(
      Iterable<ServerPluginProvider> providersHandlers) {
    Iterable<String> providerNames =
        Iterables.transform(providersHandlers,
            new Function<ServerPluginProvider, String>() {
              @Override
              public String apply(ServerPluginProvider provider) {
                return provider.getProviderPluginName();
              }
            });
    return Joiner.on(", ").join(providerNames);
  }
}
