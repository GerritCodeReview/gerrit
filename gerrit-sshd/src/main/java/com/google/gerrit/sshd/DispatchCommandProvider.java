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

package com.google.gerrit.sshd;

import com.google.common.collect.Maps;
import com.google.gerrit.server.plugins.RegistrationHandle;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import org.apache.sshd.server.Command;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates DispatchCommand using commands registered by {@link CommandModule}.
 */
public class DispatchCommandProvider implements Provider<DispatchCommand> {
  @Inject
  private Injector injector;

  @Inject
  private DispatchCommand.Factory factory;

  private final String dispatcherName;
  private final CommandName parent;

  private volatile ConcurrentMap<String, Provider<Command>> map;

  public DispatchCommandProvider(final CommandName cn) {
    this(Commands.nameOf(cn), cn);
  }

  public DispatchCommandProvider(final String dispatcherName,
      final CommandName cn) {
    this.dispatcherName = dispatcherName;
    this.parent = cn;
  }

  @Override
  public DispatchCommand get() {
    return factory.create(dispatcherName, getMap());
  }

  public RegistrationHandle register(final CommandName name,
      final Provider<Command> cmd) {
    final ConcurrentMap<String, Provider<Command>> m = getMap();
    if (m.putIfAbsent(name.value(), cmd) != null) {
      throw new IllegalArgumentException(name.value() + " exists");
    }
    return new RegistrationHandle() {
      @Override
      public void remove() {
        m.remove(name.value(), cmd);
      }
    };
  }

  private ConcurrentMap<String, Provider<Command>> getMap() {
    if (map == null) {
      synchronized (this) {
        if (map == null) {
          map = createMap();
        }
      }
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private ConcurrentMap<String, Provider<Command>> createMap() {
    ConcurrentMap<String, Provider<Command>> m = Maps.newConcurrentMap();
    for (final Binding<?> b : allCommands()) {
      final Annotation annotation = b.getKey().getAnnotation();
      if (annotation instanceof CommandName) {
        final CommandName n = (CommandName) annotation;
        if (!Commands.CMD_ROOT.equals(n) && Commands.isChild(parent, n)) {
          m.put(n.value(), (Provider<Command>) b.getProvider());
        }
      }
    }
    return m;
  }

  private static final TypeLiteral<Command> type =
      new TypeLiteral<Command>() {};

  private List<Binding<Command>> allCommands() {
    return injector.findBindingsByType(type);
  }
}
