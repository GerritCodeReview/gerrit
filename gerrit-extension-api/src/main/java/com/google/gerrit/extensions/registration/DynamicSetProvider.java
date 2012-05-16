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
import com.google.inject.internal.UniqueAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class DynamicSetProvider<T> implements Provider<DynamicSet<T>> {
  private static final Class<?> UNIQUE_ANNOTATION =
      UniqueAnnotations.create().getClass();
  private final TypeLiteral<T> type;

  @Inject
  private Injector injector;

  DynamicSetProvider(TypeLiteral<T> type) {
    this.type = type;
  }

  public DynamicSet<T> get() {
    return new DynamicSet<T>(find(injector, type));
  }

  private static <T> List<AtomicReference<T>> find(
      Injector src,
      TypeLiteral<T> type) {
    List<Binding<T>> bindings = src.findBindingsByType(type);
    int cnt = bindings != null ? bindings.size() : 0;
    if (cnt == 0) {
      return Collections.emptyList();
    }
    List<AtomicReference<T>> r = new ArrayList<AtomicReference<T>>(cnt);
    for (Binding<T> b : bindings) {
      if (UNIQUE_ANNOTATION.isInstance(b.getKey().getAnnotation())) {
        r.add(new AtomicReference<T>(b.getProvider().get()));
      }
    }
    return r;
  }
}
