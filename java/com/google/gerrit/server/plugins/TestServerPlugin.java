// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.config.GerritRuntime;
import java.nio.file.Path;

public class TestServerPlugin extends ServerPlugin {
  private final ClassLoader classLoader;
  private String sysName;
  private String httpName;
  private String sshName;

  public TestServerPlugin(
      String name,
      String pluginCanonicalWebUrl,
      PluginUser user,
      ClassLoader classLoader,
      String sysName,
      String httpName,
      String sshName,
      Path dataDir)
      throws InvalidPluginException {
    super(
        name,
        pluginCanonicalWebUrl,
        user,
        null,
        null,
        null,
        dataDir,
        classLoader,
        null,
        GerritRuntime.DAEMON);
    this.classLoader = classLoader;
    this.sysName = sysName;
    this.httpName = httpName;
    this.sshName = sshName;
    loadGuiceModules();
  }

  private void loadGuiceModules() throws InvalidPluginException {
    try {
      this.sysModule = load(sysName, classLoader);
      this.httpModule = load(httpName, classLoader);
      this.sshModule = load(sshName, classLoader);
    } catch (ClassNotFoundException e) {
      throw new InvalidPluginException("Unable to load plugin Guice Modules", e);
    }
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  protected boolean canReload() {
    return false;
  }

  @Override
  // Widen access modifier in derived class
  public void start(PluginGuiceEnvironment env) throws Exception {
    super.start(env);
  }

  @Override
  // Widen access modifier in derived class
  public void stop(PluginGuiceEnvironment env) {
    super.stop(env);
  }

  @Override
  public PluginContentScanner getContentScanner() {
    return null;
  }
}
