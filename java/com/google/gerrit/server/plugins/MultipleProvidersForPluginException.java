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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.Streams;
import java.nio.file.Path;

class MultipleProvidersForPluginException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  MultipleProvidersForPluginException(
      Path pluginSrcPath, Iterable<ServerPluginProvider> providersHandlers) {
    super(
        pluginSrcPath.toAbsolutePath()
            + " is claimed to be handled by more than one plugin provider: "
            + providersListToString(providersHandlers));
  }

  private static String providersListToString(Iterable<ServerPluginProvider> providersHandlers) {
    return Streams.stream(providersHandlers)
        .map(ServerPluginProvider::getProviderPluginName)
        .collect(joining(", "));
  }
}
