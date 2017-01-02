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

package com.google.gerrit.sshd;

import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import org.apache.sshd.server.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
class SshPluginStarterCallback
    implements StartPluginListener, ReloadPluginListener {
  private static final Logger log = LoggerFactory
      .getLogger(SshPluginStarterCallback.class);

  private final DispatchCommandProvider root;

  @Inject
  SshPluginStarterCallback(
      @CommandName(Commands.ROOT) DispatchCommandProvider root) {
    this.root = root;
  }

  @Override
  public void onStartPlugin(Plugin plugin) {
    Provider<Command> cmd = load(plugin);
    if (cmd != null) {
      plugin.add(root.register(plugin.getName(), cmd));
      for (Map.Entry<String, Provider<Command>> alias : aliases(plugin)
          .entrySet()) {
        plugin.add(root.register(alias.getKey(), alias.getValue()));
      }
    }
  }

  @Override
  public void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    Provider<Command> cmd = load(newPlugin);
    if (cmd != null) {
      newPlugin.add(root.replace(newPlugin.getName(), cmd));
      for (Map.Entry<String, Provider<Command>> alias : aliases(newPlugin)
          .entrySet()) {
        newPlugin.add(root.replace(alias.getKey(), alias.getValue()));
      }
    }
  }

  private Map<String, Provider<Command>> aliases(
      Plugin plugin) {
    HashMap<String, Provider<Command>> aliases =
        new HashMap<>();
    List<Binding<Command>> bindings = plugin.getSshInjector()
        .findBindingsByType(new TypeLiteral<Command>() {});
    for (Binding<Command> binding : bindings) {
      if (binding.getKey().getAnnotation() instanceof GlobalAliasCommandName) {
        aliases.put(
            ((GlobalAliasCommandName) binding.getKey().getAnnotation()).value(),
            binding.getProvider());
      }
    }
    return aliases;
  }

  private Provider<Command> load(Plugin plugin) {
    if (plugin.getSshInjector() != null) {
      Key<Command> key = Commands.key(plugin.getName());
      try {
        return plugin.getSshInjector().getProvider(key);
      } catch (RuntimeException err) {
        log.warn(String.format(
            "Plugin %s did not define its top-level command",
            plugin.getName()), err);
      }
    }
    return null;
  }
}
