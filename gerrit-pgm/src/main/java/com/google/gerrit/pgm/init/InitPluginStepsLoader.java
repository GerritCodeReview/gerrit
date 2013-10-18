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

import com.google.common.base.Objects;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@Singleton
public class InitPluginStepsLoader {
  private final File pluginsDir;
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
    List<File> jars = scanJarsInPluginsDirectory();
    ArrayList<InitStep> pluginsInitSteps = new ArrayList<InitStep>();

    for (File jar : jars) {
      InitStep init = loadInitStep(jar);
      if (init != null) {
        pluginsInitSteps.add(init);
      }
    }
    return pluginsInitSteps;
  }

  private InitStep loadInitStep(File jar) {
    try {
      ClassLoader pluginLoader =
          new URLClassLoader(new URL[] {jar.toURI().toURL()},
              InitPluginStepsLoader.class.getClassLoader());
      JarFile jarFile = new JarFile(jar);
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
          jar.getName(), InitStep.class.getName(), e.getMessage());
      return null;
    } catch (Exception e) {
      ui.message(
          "WARN: Cannot load and get plugin init step for %s (Exception: %s)",
          jar, e.getMessage());
      return null;
    }
  }

  private Injector getPluginInjector(File jarFile) throws IOException {
    final String pluginName =
        Objects.firstNonNull(PluginLoader.getGerritPluginName(jarFile),
            PluginLoader.nameOf(jarFile));
    return initInjector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).annotatedWith(PluginName.class).toInstance(
            pluginName);
      }
    });
  }

  private List<File> scanJarsInPluginsDirectory() {
    if (pluginsDir == null || !pluginsDir.exists()) {
      return Collections.emptyList();
    }
    File[] matches = pluginsDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        String n = pathname.getName();
        return (n.endsWith(".jar") && pathname.isFile());
      }
    });
    if (matches == null) {
      ui.message("WARN: Cannot list %s", pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }
    Arrays.sort(matches, new Comparator<File>() {
      @Override
      public int compare(File o1, File o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return Arrays.asList(matches);
  }
}
