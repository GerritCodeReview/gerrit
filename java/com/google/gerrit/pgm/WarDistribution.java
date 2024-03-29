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

package com.google.gerrit.pgm;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.pgm.init.InitPlugins.JAR;
import static com.google.gerrit.pgm.init.InitPlugins.PLUGIN_DIR;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.init.PluginsDistribution;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Singleton
public class WarDistribution implements PluginsDistribution {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void foreach(Processor processor) throws IOException {
    File myWar = GerritLauncher.getDistributionArchive();
    if (myWar.isFile()) {
      try (ZipFile zf = new ZipFile(myWar)) {
        for (ZipEntry ze : entriesOf(zf)) {
          if (ze.isDirectory()) {
            continue;
          }

          if (ze.getName().startsWith(PLUGIN_DIR) && ze.getName().endsWith(JAR)) {
            String pluginJarName = new File(ze.getName()).getName();
            String pluginName = pluginJarName.substring(0, pluginJarName.length() - JAR.length());
            try (InputStream in = zf.getInputStream(ze)) {
              processor.process(pluginName, in);
            } catch (IOException ioe) {
              logger.atSevere().log("Error opening plugin %s: %s", ze.getName(), ioe.getMessage());
            }
          }
        }
      }
    }
  }

  @Override
  public List<String> listPluginNames() throws FileNotFoundException {
    // not yet used
    throw new UnsupportedOperationException();
  }

  private static ImmutableList<? extends ZipEntry> entriesOf(ZipFile zipFile) {
    return zipFile.stream().collect(toImmutableList());
  }
}
