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

package com.google.gerrit.acceptance;

import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.TestServerPlugin;
import com.google.gerrit.server.PluginUser;
import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;

public class LightweightPluginDaemonTest extends AbstractDaemonTest {
  @Inject private PluginGuiceEnvironment env;

  @Inject private PluginUser.Factory pluginUserFactory;

  private TestServerPlugin plugin;

  @Before
  public void setUp() throws Exception {
    TestPlugin testPlugin = getTestPlugin(getClass());
    String name = testPlugin.name();
    plugin =
        new TestServerPlugin(
            name,
            canonicalWebUrl.get() + "plugins/" + name,
            pluginUserFactory.create(name),
            getClass().getClassLoader(),
            testPlugin.sysModule(),
            testPlugin.httpModule(),
            testPlugin.sshModule());

    plugin.start(env);
    env.onStartPlugin(plugin);
  }

  @After
  public void tearDown() {
    if (plugin != null) {
      // plugin will be null if the plugin test requires ssh, but the command
      // line flag says we are running tests without ssh as the assume()
      // statement in AbstractDaemonTest will prevent the execution of setUp()
      // in this class
      plugin.stop(env);
      env.onStopPlugin(plugin);
    }
  }

  private static TestPlugin getTestPlugin(Class<?> clazz) {
    for (; clazz != null; clazz = clazz.getSuperclass()) {
      if (clazz.getAnnotation(TestPlugin.class) != null) {
        return clazz.getAnnotation(TestPlugin.class);
      }
    }
    throw new IllegalStateException("TestPlugin annotation missing");
  }
}
