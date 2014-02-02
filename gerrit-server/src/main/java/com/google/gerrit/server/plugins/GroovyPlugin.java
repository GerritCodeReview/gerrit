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

import com.google.common.collect.Sets;
import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;

import groovy.util.GroovyScriptEngine;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;
import java.util.Set;

/**
 * Groovy scripting plugins.
 *
 * Allows to define a Groovy class to implement any type of Gerrit
 * plugin.
 *
 * Example of Groovy SSH Plugin (hello-1.0.groovy):
 * ------------------------------------------------
 * import com.google.gerrit.sshd.SshCommand
 * import com.google.gerrit.extensions.annotations.Export
 * @Export("groovy")
 * class GroovyCommand extends SshCommand {
 *
 *  public void run() {
 *        stdout.println("Hello Gerrit from Groovy !")
 *  }
 *
 *  The above example add a "hello groovy" command to
 *  Gerrit SSH interface that displays "Hello Gerrit from Groovy !".
 *
}
 * @author lucamilanesio
 *
 */
class GroovyPlugin extends ScriptingPlugin {
  private static final Set<String> GROOVY_EXTENSIONS = Sets.newHashSet(
      "groovy", "gvy", "gy", "gsh");

  private GroovyScriptEngine scriptEngine;
  private Class scriptClass;

  public static class Factory implements ScriptingPlugin.Factory {

    @Override
    public ScriptingPlugin get(String name, File srcFile,
        PluginUser pluginUser, FileSnapshot snapshot) {
      return new GroovyPlugin(name, srcFile, pluginUser, snapshot);
    }

    @Override
    public boolean isMyScriptExtension(String scriptExtension) {
      return GROOVY_EXTENSIONS.contains(scriptExtension.toLowerCase());
    }

  }

  GroovyPlugin(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) {
    super(name, srcFile, pluginUser, snapshot);
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    super.start(env);

    scriptEngine = getHttpInjector().getInstance(GroovyScriptEngine.class);
    scriptClass = scriptEngine.loadScriptByName(getSrcFile().getName());

    AutoRegisterScript auto = new AutoRegisterScript(scriptClass, this);
    auto.register();
  }

  @Override
  protected AbstractModule getModule() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        install(GroovyPlugin.super.getModule());
        bind(GroovyScriptEngine.class).to(GroovyPluginScriptEngine.class);
      }
    };
  }
}
