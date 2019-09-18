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
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.apache.sshd.server.Command;

/** Creates DispatchCommand using commands registered by {@link CommandModule}. */
public class DispatchCommandProvider implements Provider<DispatchCommand> {
  @Inject private Injector injector;

  @Inject private DispatchCommand.Factory factory;

  private final CommandName parent;
  private volatile ConcurrentMap<String, CommandProvider> map;

  public DispatchCommandProvider(CommandName cn) {
    this.parent = cn;
  }

  @Override
  public DispatchCommand get() {
    return factory.create(getMap());
  }

  public RegistrationHandle register(CommandName name, Provider<Command> cmd) {
    final ConcurrentMap<String, CommandProvider> m = getMap();
    final CommandProvider commandProvider = new CommandProvider(cmd, null);
    if (m.putIfAbsent(name.value(), commandProvider) != null) {
      throw new IllegalArgumentException(name.value() + " exists");
    }
    return new RegistrationHandle() {
      @Override
      public void remove() {
        m.remove(name.value(), commandProvider);
      }
    };
  }

  public RegistrationHandle replace(CommandName name, Provider<Command> cmd) {
    final ConcurrentMap<String, CommandProvider> m = getMap();
    final CommandProvider commandProvider = new CommandProvider(cmd, null);
    m.put(name.value(), commandProvider);
    return new RegistrationHandle() {
      @Override
      public void remove() {
        m.remove(name.value(), commandProvider);
      }
    };
  }

  ConcurrentMap<String, CommandProvider> getMap() {
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
  private ConcurrentMap<String, CommandProvider> createMap() {
    ConcurrentMap<String, CommandProvider> m = Maps.newConcurrentMap();
    for (Binding<?> b : allCommands()) {
      final Annotation annotation = b.getKey().getAnnotation();
      if (annotation instanceof CommandName) {
        final CommandName n = (CommandName) annotation;
        if (!Commands.CMD_ROOT.equals(n) && Commands.isChild(parent, n)) {
          String descr = null;
          if (annotation instanceof Commands.NestedCommandNameImpl) {
            Commands.NestedCommandNameImpl impl = ((Commands.NestedCommandNameImpl) annotation);
            descr = impl.descr();
          }
          m.put(n.value(), new CommandProvider((Provider<Command>) b.getProvider(), descr));
        }
      }
    }
    return m;
  }

  private static final TypeLiteral<Command> type = new TypeLiteral<Command>() {};

  private List<Binding<Command>> allCommands() {
    return injector.findBindingsByType(type);
  }
}
