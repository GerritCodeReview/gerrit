// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Ordering;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.JarPluginProvider;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@Singleton
public class InitPluginStepsLoader {
  private final Path pluginsDir;
  private final Injector initInjector;
  final ConsoleUI ui;

  @Inject
  public InitPluginStepsLoader(final ConsoleUI ui, final SitePaths sitePaths,
      final Injector initInjector) {
    this.pluginsDir = sitePaths.plugins_dir;
    this.initInjector = initInjector;
    this.ui = ui;
  }

  public Collection<InitStep> getInitSteps() {
    List<Path> jars = scanJarsInPluginsDirectory();
    ArrayList<InitStep> pluginsInitSteps = new ArrayList<>();

    for (Path jar : jars) {
      InitStep init = loadInitStep(jar);
      if (init != null) {
        pluginsInitSteps.add(init);
      }
    }
    return pluginsInitSteps;
  }

  @SuppressWarnings("resource")
  private InitStep loadInitStep(Path jar) {
    try {
      URLClassLoader pluginLoader =
          new URLClassLoader(new URL[] {jar.toUri().toURL()},
             InitPluginStepsLoader.class.getClassLoader());
      try (JarFile jarFile = new JarFile(jar.toFile())) {
        Attributes jarFileAttributes = jarFile.getManifest().getMainAttributes();
        String initClassName = jarFileAttributes.getValue("Gerrit-InitStep");
        if (initClassName == null) {
          return null;
        }
        @SuppressWarnings("unchecked")
        Class<? extends InitStep> initStepClass =
            (Class<? extends InitStep>) pluginLoader.loadClass(initClassName);
        return getPluginInjector(jar).getInstance(initStepClass);
      } catch (ClassCastException e) {
        ui.message(
            "WARN: InitStep from plugin %s does not implement %s (Exception: %s)",
            jar.getFileName(), InitStep.class.getName(), e.getMessage());
        return null;
      }
    } catch (Exception e) {
      ui.message(
          "WARN: Cannot load and get plugin init step for %s (Exception: %s)",
          jar, e.getMessage());
      return null;
    }
  }

  private Injector getPluginInjector(Path jarPath) throws IOException {
    final String pluginName = MoreObjects.firstNonNull(
        JarPluginProvider.getJarPluginName(jarPath),
        PluginLoader.nameOf(jarPath));
    return initInjector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).annotatedWith(PluginName.class).toInstance(
            pluginName);
      }
    });
  }

  private List<Path> scanJarsInPluginsDirectory() {
    if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
      return Collections.emptyList();
    }
    DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path entry) throws IOException {
        return entry.getFileName().toString().endsWith(".jar")
            && Files.isRegularFile(entry);
      }
    };
    try (DirectoryStream<Path> paths =
        Files.newDirectoryStream(pluginsDir, filter)) {
      return Ordering.natural().sortedCopy(paths);
    } catch (IOException e) {
      ui.message("WARN: Cannot list %s: %s", pluginsDir.toAbsolutePath(),
          e.getMessage());
      return Collections.emptyList();
    }
  }
}
