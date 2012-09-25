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

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@Singleton
public class InitPluginStepsLoader {
  private static final Logger log = LoggerFactory
      .getLogger(InitPluginStepsLoader.class);

  private final File pluginsDir;
  private final Injector initInjector;

  @Inject
  public InitPluginStepsLoader(final SitePaths sitePaths,
      final Injector initInjector) {
    this.pluginsDir = sitePaths.plugins_dir;
    this.initInjector = initInjector;
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
      return initInjector.getInstance(initStepClass);
    } catch (ClassCastException e) {
      log.warn("InitStep from plugin " + jar.getName() + " does not implement "
          + InitStep.class.getName(), e);
      return null;
    } catch (Exception e) {
      log.error("Cannot load and get plugin init step for " + jar, e);
      return null;
    }
  }

  private List<File> scanJarsInPluginsDirectory() {
    if (pluginsDir == null || !pluginsDir.exists()) {
      return Collections.emptyList();
    }
    File[] matches = pluginsDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        String n = pathname.getName();
        return (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
            && pathname.isFile();
      }
    });
    if (matches == null) {
      log.error("Cannot list " + pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }
    return Arrays.asList(matches);
  }
}
