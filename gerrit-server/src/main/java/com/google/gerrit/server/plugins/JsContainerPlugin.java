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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;
import java.util.jar.JarFile;

class JsContainerPlugin extends Plugin {
  private Injector httpInjector;

  JsContainerPlugin(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) {
    super(name, srcFile, pluginUser, snapshot, ApiType.JS);
  }

  @Override
  @Nullable
  public String getVersion() {
    return "DEV";
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    manager = new LifecycleManager();
    httpInjector =
        Guice.createInjector(new ContainerJsPluginModule(getName()));
    manager.start();
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
    return true;
  }

  private static final class ContainerJsPluginModule extends AbstractModule {
    private final String pluginName;

    ContainerJsPluginModule(String pluginName) {
      this.pluginName = pluginName;
    }

    @Override
    protected void configure() {
      bind(String.class).annotatedWith(PluginName.class).toInstance(pluginName);
      DynamicSet.bind(binder(), WebUiPlugin.class).toInstance(
          new JavaScriptPlugin(JavaScriptPlugin.DEFAULT_INIT_FILE_NAME));
    }
  }
}
