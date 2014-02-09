// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;
import java.util.Collections;
import java.util.jar.JarFile;

public abstract class ScriptingPlugin extends Plugin {

  public interface Factory {
    ScriptingPlugin get(String name, File srcFile, PluginUser pluginUser,
        FileSnapshot snapshot);

    boolean handlesExtension(String scriptExtension);
  }

  private Injector httpInjector;
  private Injector sshInjector;
  private Injector sysInjector;
  private PluginGuiceEnvironment guiceEnv;

  public ScriptingPlugin(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) {
    super(StringUtils.substringBeforeLast(name, "-"),
        Collections.<String>emptySet(),
        srcFile, pluginUser,
        snapshot, ApiType.SCRIPTING);
  }

  @Override
  @Nullable
  public String getVersion() {
    String fileName = getSrcFile().getName();
    int firstDash = fileName.indexOf("-");
    if (firstDash > 0) {
      return fileName.substring(firstDash + 1, fileName.lastIndexOf("."));
    }
    return "";
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    guiceEnv = env;
    manager = new LifecycleManager();
    httpInjector =
        Guice.createInjector(env.getSysModule(), getModule());

    manager.start();
  }

  protected AbstractModule getModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).annotatedWith(PluginName.class)
            .toInstance(getName());
      }
    };
  }

  @Override
  protected void stop(PluginGuiceEnvironment env) {
    if (manager != null) {
      manager.stop();
      httpInjector = null;
      sshInjector = null;
      sysInjector = null;
    }
  }

  @Override
  public Injector getSysInjector() {
    return sysInjector;
  }

  protected void setSysInjector(Injector sysInjector) {
    this.sysInjector = sysInjector;
  }

  @Override
  @Nullable
  public Injector getSshInjector() {
    return sshInjector;
  }

  protected void setSshInjector(Injector sshInjector) {
    this.sshInjector = sshInjector;
  }

  @Override
  @Nullable
  public Injector getHttpInjector() {
    return httpInjector;
  }

  protected void setHttpInjector(Injector httpInjector) {
    this.httpInjector = httpInjector;
  }

  @Override
  public JarFile getJarFile() {
    return null;
  }

  @Override
  protected boolean canReload() {
    return true;
  }

  public PluginGuiceEnvironment getGuiceEnvironment() {
    return guiceEnv;
  }
}
