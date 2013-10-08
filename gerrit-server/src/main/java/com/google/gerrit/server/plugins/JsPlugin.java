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

package com.google.gerrit.server.plugins;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.UniqueAnnotations;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;
import java.util.jar.JarFile;

class JsPlugin extends Plugin {
  private static final String INIT_FILE_NAME = "init.js";

  static boolean isPlugin(File file) {
    if (file.isFile()) {
      return file.getName().endsWith(".js");
    }
    return isContainerPlugin(file);
  }

  static String getName(File file) {
    if (file.isFile()) {
      return file.getName().substring(0, file.getName().length() - 3);
    }
    return file.getName();
  }

  private Injector httpInjector;

  JsPlugin(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) {
    super(name, srcFile, pluginUser, snapshot, ApiType.JS);
  }

  @Override
  @Nullable
  public String getVersion() {
    String fileName = computeFileName();
    int firstDash = fileName.indexOf("-");
    if (firstDash > 0) {
      return fileName.substring(firstDash + 1, fileName.lastIndexOf(".js"));
    }
    return "";
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    manager = new LifecycleManager();
    String fileName = computeFileName();
    httpInjector =
        Guice.createInjector(new StandaloneJsPluginModule(getName(), fileName));
    manager.start();
  }

  private String computeFileName() {
    if (isContainerPlugin(getSrcFile())) {
      return INIT_FILE_NAME;
    }
    return getSrcFile().getName();
  }

  @Override
  void stop(PluginGuiceEnvironment env) {
    if (manager != null) {
      manager.stop();
      httpInjector = null;
    }
  }

  @Override
  public JarFile getJarFile() {
    return null;
  }

  @Override
  public Injector getSysInjector() {
    return null;
  }

  @Override
  @Nullable
  public Injector getSshInjector() {
    return null;
  }

  @Override
  @Nullable
  public Injector getHttpInjector() {
    return httpInjector;
  }

  @Override
  boolean canReload() {
    return false;
  }

  private static boolean isContainerPlugin(File srcFile) {
    return getContainerInitFile(srcFile).exists();
  }

  private static File getContainerInitFile(File container) {
    return new File(container, INIT_FILE_NAME);
  }

  private static final class StandaloneJsPluginModule extends AbstractModule {
    private final String fileName;
    private final String pluginName;

    public StandaloneJsPluginModule(String pluginName, String fileName) {
      this.pluginName = pluginName;
      this.fileName = fileName;
    }

    @Override
    protected void configure() {
      bind(String.class).annotatedWith(PluginName.class).toInstance(pluginName);
      bind(WebUiPlugin.class).annotatedWith(UniqueAnnotations.create())
          .toInstance(new JavaScriptPlugin(fileName));
    }
  }
}
