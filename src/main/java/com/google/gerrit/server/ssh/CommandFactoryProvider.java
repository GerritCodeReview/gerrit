// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import org.apache.sshd.server.CommandFactory;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a CommandFactory using commands registered by {@link CommandModule}.
 */
class CommandFactoryProvider implements Provider<CommandFactory> {
  private final Injector injector;

  @Inject
  CommandFactoryProvider(final Injector i) {
    injector = i;
  }

  @Override
  public CommandFactory get() {
    return new GuiceCommandFactory(createMap());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Provider<CommandFactory.Command>> createMap() {
    final Map<String, Provider<CommandFactory.Command>> m;
    m = new HashMap<String, Provider<CommandFactory.Command>>();

    for (final Binding<?> binding : allCommands()) {
      final Annotation annotation = binding.getKey().getAnnotation();
      if (annotation == null || !(annotation instanceof CommandName)) {
        continue;
      }

      final CommandName name = (CommandName) annotation;
      m.put(name.value(), (Provider<CommandFactory.Command>) binding
          .getProvider());
    }

    return Collections.unmodifiableMap(m);
  }

  private List<Binding<CommandFactory.Command>> allCommands() {
    return injector
        .findBindingsByType(new TypeLiteral<CommandFactory.Command>() {});
  }
}
