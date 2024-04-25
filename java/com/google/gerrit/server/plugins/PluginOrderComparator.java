// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

class PluginOrderComparator implements Comparator<Map.Entry<String, Path>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final ManifestLoader DEFAULT_LOADER =
      pluginPath -> {
        try (JarFile jarFile = new JarFile(pluginPath.toFile())) {
          return jarFile.getManifest();
        }
      };

  @FunctionalInterface
  interface ManifestLoader {
    Manifest load(Path pluginPath) throws IOException;
  }

  private final ManifestLoader manifestLoader;

  PluginOrderComparator() {
    this(DEFAULT_LOADER);
  }

  PluginOrderComparator(ManifestLoader manifestLoader) {
    this.manifestLoader = manifestLoader;
  }

  @Override
  public int compare(Map.Entry<String, Path> e1, Map.Entry<String, Path> e2) {
    Path n1 = e1.getValue().getFileName();
    Path n2 = e2.getValue().getFileName();

    try {
      boolean e1IsApi = isApi(e1.getValue());
      boolean e2IsApi = isApi(e2.getValue());
      return ComparisonChain.start()
          .compareTrueFirst(e1IsApi, e2IsApi)
          .compareTrueFirst(isJar(n1), isJar(n2))
          .compare(n1, n2)
          .result();
    } catch (IOException ioe) {
      logger.atSevere().withCause(ioe).log("Unable to compare %s and %s", n1, n2);
      return 0;
    }
  }

  private boolean isJar(Path pluginPath) {
    return pluginPath.toString().endsWith(".jar");
  }

  private boolean isApi(Path pluginPath) throws IOException {
    return isJar(pluginPath) && hasApiModuleEntryInManifest(pluginPath);
  }

  private boolean hasApiModuleEntryInManifest(Path pluginPath) throws IOException {
    return !Strings.isNullOrEmpty(
        manifestLoader.load(pluginPath).getMainAttributes().getValue(ServerPlugin.API_MODULE));
  }
}
