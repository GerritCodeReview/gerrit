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
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.util.List;

class DynamicMapProvider<T> implements Provider<DynamicMap<T>> {
  private final TypeLiteral<T> type;

  @Inject private Injector injector;

  DynamicMapProvider(TypeLiteral<T> type) {
    this.type = type;
  }

  @Override
  public DynamicMap<T> get() {
    PrivateInternals_DynamicMapImpl<T> m = new PrivateInternals_DynamicMapImpl<>();
    List<Binding<T>> bindings = injector.findBindingsByType(type);
    if (bindings != null) {
      for (Binding<T> b : bindings) {
        if (b.getKey().getAnnotation() != null) {
          m.put("gerrit", b.getKey(), b.getProvider());
        }
      }
    }
    return m;
  }
}
