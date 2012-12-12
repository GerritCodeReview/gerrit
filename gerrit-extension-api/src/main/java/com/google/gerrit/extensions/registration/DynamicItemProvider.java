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

package com.google.gerrit.extensions.registration;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;

import java.util.List;

class DynamicItemProvider<T> implements Provider<DynamicItem<T>> {
  private final TypeLiteral<T> type;
  private final Key<DynamicItem<T>> key;

  @Inject
  private Injector injector;

  DynamicItemProvider(TypeLiteral<T> type, Key<DynamicItem<T>> key) {
    this.type = type;
    this.key = key;
  }

  public DynamicItem<T> get() {
    return new DynamicItem<T>(find(injector, type), null, key);
  }

  private static <T> Provider<T> find(
      Injector src,
      TypeLiteral<T> type) {
    Provider<T> provider = null;
    List<Binding<T>> bindings = src.findBindingsByType(type);
    if (bindings != null) {
      for (Binding<T> b : bindings) {
        if (b.getKey().getAnnotation() != null) {
          if (provider != null) {
            throw new ProvisionException("Multiple providers bound for "
                + "DynamicItem type: " + type
                + "\nThis is not allowed - perhaps the main "
                + "server is improperly configured?");
          }
          provider = b.getProvider();
        }
      }
    }
    return provider;
  }
}
