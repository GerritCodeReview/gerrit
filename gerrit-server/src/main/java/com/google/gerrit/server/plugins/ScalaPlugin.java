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

import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;
import java.util.Set;

/**
 * Scala scripting plugins.
 *
 * Allows to define a Groovy class to implement any type of Gerrit plugin.
 *
 * Example of Scala SSH Plugin (hello-1.0.scala):
 * ------------------------------------------------ TBD
 *
 * The above example add a "hello scala" command to Gerrit SSH interface that
 * displays "Hello Gerrit from Scala !"
 *
 * import com.google.gerrit.sshd.SshCommand
 * import com.google.gerrit.extensions.annotations.Export
 *
 * @Export("scala")
 * class MyClass extends SshCommand {
 *    override def run = stdout println "Hello Gerrit from Scala !"
 * }
 *
 * @author lucamilanesio
 *
 */
class ScalaPlugin extends ScriptingPlugin {
  private static final String SCALA_EXTENSION = "scala";

  private ScalaPluginScriptEngine scriptEngine;

  public static class Factory implements ScriptingPlugin.Factory {

    @Override
    public ScriptingPlugin get(String name, File srcFile,
        PluginUser pluginUser, FileSnapshot snapshot) {
      return new ScalaPlugin(name, srcFile, pluginUser, snapshot);
    }

    @Override
    public boolean isMyScriptExtension(String scriptExtension) {
      return SCALA_EXTENSION.equalsIgnoreCase(scriptExtension);
    }

  }

  ScalaPlugin(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) {
    super(name, srcFile, pluginUser, snapshot);
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    super.start(env);

    scriptEngine = getHttpInjector().getInstance(ScalaPluginScriptEngine.class);
    Set<Class<?>> scriptClasses = scriptEngine.eval(getSrcFile());

    AutoRegisterScript auto = new AutoRegisterScript(this);
    for (Class<?> clazz : scriptClasses) {
      auto.scan(clazz);
    }
    auto.registerFinal();
  }

  @Override
  protected AbstractModule getModule() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        install(ScalaPlugin.super.getModule());
        bind(ScalaPluginScriptEngine.class);
      }
    };
  }
}
