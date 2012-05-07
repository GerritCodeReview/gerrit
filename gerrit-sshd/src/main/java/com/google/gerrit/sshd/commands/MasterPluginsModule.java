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
import com.google.gerrit.plugins.HelloworldCommandModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.gerrit.sshd.DispatchCommandProvider;
import com.google.inject.Inject;

import org.apache.sshd.server.Command;
import org.eclipse.jgit.lib.Config;
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
      Class<PluginCommandModule> c = (Class<PluginCommandModule>) p.moduleClass;
      try {
        PluginCommandModule module = c.newInstance();
        module.initSshModule(p.name);
        CommandName cmd = Commands.named(p.name);
        command(cmd).toProvider(new DispatchCommandProvider(cmd));
        install(module);
      } catch (InstantiationException e) {
        log.warn("Initialization of plugin module '" + p.name + "' failed");
      } catch (IllegalAccessException e) {
        log.warn("Initialization of plugin module '" + p.name + "' failed");
      }
    }
  }
}
