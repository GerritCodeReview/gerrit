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

import com.google.gerrit.common.PlugInClassLoader;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;

import org.apache.sshd.server.Command;
import org.eclipse.jgit.lib.Config;

import java.util.Set;

public class MasterPluginsModule extends CommandModule {

  private Config config;
  private PlugInClassLoader plugInClassLoader;

  @Inject
  MasterPluginsModule(@GerritServerConfig Config config, PlugInClassLoader loader) {
    this.config = config;
    plugInClassLoader = loader;
  }

  @Override
  protected void configure() {
    final CommandName gerrit = Commands.named("gerrit");
    Set<String> sshCommandNames = config.getNames("plugins");
    for (String name : sshCommandNames) {
      String clazz = config.getString("plugins", null, name);
      try {
        Class<?> c = plugInClassLoader.loadClass(clazz);
        if (Command.class.isAssignableFrom(c)) {
          command(gerrit, name).to((Class<Command>) c);
        } else {
          System.out.println("Class " + clazz + " is not subtype of org.apache.sshd.server.Command");
        }
      } catch (ClassNotFoundException e) {
        throw new ProvisionException("Could not load plugin '" + name + "'", e);
      }
    }
  }
}
