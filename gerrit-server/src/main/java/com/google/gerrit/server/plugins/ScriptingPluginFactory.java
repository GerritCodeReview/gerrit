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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.plugins.ScriptingPlugin.Factory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;

@Singleton
public class ScriptingPluginFactory implements ScriptingPlugin.Factory {

  private final DynamicSet<ScriptingPlugin.Factory> scriptingFactories;

  @Inject
  public ScriptingPluginFactory(DynamicSet<ScriptingPlugin.Factory> sf) {
    this.scriptingFactories = sf;
  }

  @Override
  public ScriptingPlugin get(String name, File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot) {

    String srcFilename = srcFile.getName();
    String scrFileExtension = PluginLoader.getExtension(srcFilename);
    ScriptingPlugin.Factory scriptingFactory = getFactory(scrFileExtension);
    if (scriptingFactory == null) {
      throw new IllegalArgumentException("Script file " + srcFile.getAbsolutePath() +
          " is not a known scripting language for a Gerrit plugin");
    }

    return scriptingFactory.get(name, srcFile, pluginUser, snapshot);
  }

  private Factory getFactory(String srcFileExtension) {
    for (ScriptingPlugin.Factory scriptingFactory : scriptingFactories) {
      if (scriptingFactory.handlesExtension(srcFileExtension)) {
        return scriptingFactory;
      }
    }
    return null;
  }

  @Override
  public boolean handlesExtension(String scriptExtension) {
    return getFactory(scriptExtension) != null;
  }
}
