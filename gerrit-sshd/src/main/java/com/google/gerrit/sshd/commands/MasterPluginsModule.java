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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.Plugin;
import com.google.gerrit.common.PluginLoader;
import com.google.gerrit.sshd.CommandModule;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class MasterPluginsModule extends CommandModule {
  private static final Logger log =
      LoggerFactory.getLogger(MasterPluginsModule.class);

  private PluginLoader pluginLoader;

  @Inject
  MasterPluginsModule(PluginLoader loader) {
    pluginLoader = loader;
  }

  @Override
  protected void configure() {
    Collection<Plugin> plugins = pluginLoader.getPlugins();
    for (Plugin p : plugins) {
      if (PluginCommandModule.class.isAssignableFrom(p.sshModule)) {
        @SuppressWarnings("unchecked")
        Class<PluginCommandModule> c = (Class<PluginCommandModule>) p.sshModule;
        try {
          PluginCommandModule module = c.newInstance();
          module.initSshModule(p.name);
          install(module);
        } catch (InstantiationException e) {
          log.warn("Initialization of plugin module '" + p.name + "' failed");
        } catch (IllegalAccessException e) {
          log.warn("Initialization of plugin module '" + p.name + "' failed");
        }
      }
    }
  }
}
