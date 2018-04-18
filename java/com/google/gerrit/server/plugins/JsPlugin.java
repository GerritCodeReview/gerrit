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
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

class JsPlugin extends Plugin {
  private Injector sysInjector;

  JsPlugin(String name, Path srcFile, PluginUser pluginUser, FileSnapshot snapshot) {
    super(name, srcFile, pluginUser, snapshot, ApiType.JS);
  }

  @Override
  @Nullable
  public String getVersion() {
    String fileName = getSrcFile().getFileName().toString();
    int firstDash = fileName.indexOf("-");
    if (firstDash > 0) {
      int extension =
          fileName.endsWith(".js") ? fileName.lastIndexOf(".js") : fileName.lastIndexOf(".html");
      if (extension > 0) {
        return fileName.substring(firstDash + 1, extension);
      }
    }
    return "";
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    manager = new LifecycleManager();
    String fileName = getSrcFile().getFileName().toString();
    sysInjector = Guice.createInjector(new StandaloneJsPluginModule(getName(), fileName));
    manager.start();
  }

  @Override
  protected void stop(PluginGuiceEnvironment env) {
    if (manager != null) {
      manager.stop();
      sysInjector = null;
    }
  }

  @Override
  public Injector getSysInjector() {
    return sysInjector;
  }

  @Override
  @Nullable
  public Injector getSshInjector() {
    return null;
  }

  @Override
  @Nullable
  public Injector getHttpInjector() {
    return null;
  }

  @Override
  protected boolean canReload() {
    return true;
  }

  private static final class StandaloneJsPluginModule extends AbstractModule {
    private final String fileName;
    private final String pluginName;

    StandaloneJsPluginModule(String pluginName, String fileName) {
      this.pluginName = pluginName;
      this.fileName = fileName;
    }

    @Override
    protected void configure() {
      bind(String.class).annotatedWith(PluginName.class).toInstance(pluginName);
      DynamicSet.bind(binder(), WebUiPlugin.class).toInstance(WebUiPlugin.js(fileName));
    }
  }

  @Override
  public PluginContentScanner getContentScanner() {
    return PluginContentScanner.EMPTY;
  }
}
