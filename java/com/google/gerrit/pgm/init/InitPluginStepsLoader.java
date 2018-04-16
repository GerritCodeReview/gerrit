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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.JarPluginProvider;
import com.google.gerrit.server.plugins.PluginUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@Singleton
public class InitPluginStepsLoader {
  private final Path pluginsDir;
  private final Injector initInjector;
  final ConsoleUI ui;

  @Inject
  public InitPluginStepsLoader(final ConsoleUI ui, SitePaths sitePaths, Injector initInjector) {
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
          URLClassLoader.newInstance(
              new URL[] {jar.toUri().toURL()}, InitPluginStepsLoader.class.getClassLoader());
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
            "WARN: InitStep from plugin %s does not implement %s (Exception: %s)\n",
            jar.getFileName(), InitStep.class.getName(), e.getMessage());
        return null;
      } catch (NoClassDefFoundError e) {
        ui.message(
            "WARN: Failed to run InitStep from plugin %s (Missing class: %s)\n",
            jar.getFileName(), e.getMessage());
        return null;
      }
    } catch (Exception e) {
      ui.message(
          "WARN: Cannot load and get plugin init step for %s (Exception: %s)\n",
          jar, e.getMessage());
      return null;
    }
  }

  private Injector getPluginInjector(Path jarPath) throws IOException {
    final String pluginName =
        MoreObjects.firstNonNull(
            JarPluginProvider.getJarPluginName(jarPath), PluginUtil.nameOf(jarPath));
    return initInjector.createChildInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).annotatedWith(PluginName.class).toInstance(pluginName);
          }
        });
  }

  private List<Path> scanJarsInPluginsDirectory() {
    try {
      return PluginUtil.listPlugins(pluginsDir, ".jar");
    } catch (IOException e) {
      ui.message("WARN: Cannot list %s: %s", pluginsDir.toAbsolutePath(), e.getMessage());
      return ImmutableList.of();
    }
  }
}
