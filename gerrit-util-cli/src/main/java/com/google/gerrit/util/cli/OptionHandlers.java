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

package com.google.gerrit.util.cli;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.lang.reflect.ParameterizedType;
import java.util.Map.Entry;

@Singleton
public class OptionHandlers {
  public static OptionHandlers empty() {
    ImmutableMap<Class<?>, Provider<OptionHandlerFactory<?>>> m = ImmutableMap.of();
    return new OptionHandlers(m);
  }

  private final ImmutableMap<Class<?>, Provider<OptionHandlerFactory<?>>> map;

  @Inject
  OptionHandlers(Injector parent) {
    this(build(parent));
  }

  OptionHandlers(ImmutableMap<Class<?>, Provider<OptionHandlerFactory<?>>> m) {
    this.map = m;
  }

  @Nullable
  OptionHandlerFactory<?> get(Class<?> type) {
    Provider<OptionHandlerFactory<?>> b = map.get(type);
    return b != null ? b.get() : null;
  }

  private static ImmutableMap<Class<?>, Provider<OptionHandlerFactory<?>>> build(Injector i) {
    ImmutableMap.Builder<Class<?>, Provider<OptionHandlerFactory<?>>> map = ImmutableMap.builder();
    for (; i != null; i = i.getParent()) {
      for (Entry<Key<?>, Binding<?>> e : i.getBindings().entrySet()) {
        TypeLiteral<?> type = e.getKey().getTypeLiteral();
        if (type.getRawType() == OptionHandlerFactory.class
            && e.getKey().getAnnotation() == null
            && type.getType() instanceof ParameterizedType) {
          map.put(getType(type), cast(e.getValue()).getProvider());
        }
      }
    }
    return map.build();
  }

  private static Class<?> getType(TypeLiteral<?> t) {
    ParameterizedType p = (ParameterizedType) t.getType();
    return (Class<?>) p.getActualTypeArguments()[0];
  }

  @SuppressWarnings("unchecked")
  private static Binding<OptionHandlerFactory<?>> cast(Binding<?> e) {
    return (Binding<OptionHandlerFactory<?>>) e;
  }
}
