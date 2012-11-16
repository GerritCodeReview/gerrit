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

package com.google.gerrit.extensions.restapi;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;

/** Guice DSL for binding {@link RestView} implementations. */
public abstract class RestApiModule extends AbstractModule {
  protected static final String GET = "GET";
  protected static final String PUT = "PUT";
  protected static final String DELETE = "DELETE";
  protected static final String POST = "POST";

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> get(TypeLiteral<T> viewType) {
    return view(viewType, GET, "/");
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> put(TypeLiteral<T> viewType) {
    return view(viewType, PUT, "/");
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> post(TypeLiteral<T> viewType) {
    return view(viewType, POST, "/");
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> delete(TypeLiteral<T> viewType) {
    return view(viewType, DELETE, "/");
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> get(TypeLiteral<T> viewType, String name) {
    return view(viewType, GET, name);
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> put(TypeLiteral<T> viewType, String name) {
    return view(viewType, PUT, name);
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> post(TypeLiteral<T> viewType, String name) {
    return view(viewType, POST, name);
  }

  protected <R extends RestResource, T extends RestView<R>>
  LinkedBindingBuilder<T> delete(TypeLiteral<T> viewType, String name) {
    return view(viewType, DELETE, name);
  }

  protected <P extends RestResource, V extends RestView<P>>
  ChildCollectionBinder<P, V> child(
      TypeLiteral<V> viewType,
      String name) {
    return new ChildCollectionBinder<P, V>(view(viewType, GET, name));
  }

  protected <R extends RestResource, V extends RestView<R>>
  LinkedBindingBuilder<V> view(
      TypeLiteral<V> viewType,
      String method,
      String name) {
    if (name.length() > 1 && name.startsWith("/")) {
      // Views may be bound as "/" to mean the resource itself, or
      // as "status" as in "/type/{id}/status". Don't bind "/status"
      // if the caller asked for that, bind what the server expects.
      name = name.substring(1);
    }
    return bind(viewType).annotatedWith(Exports.named(method + "." + name));
  }

  public static class ChildCollectionBinder<
      P extends RestResource,
      V extends RestView<P>> {
    private final LinkedBindingBuilder<V> binder;

    private ChildCollectionBinder(LinkedBindingBuilder<V> binder) {
      this.binder = binder;
    }

    public <C extends RestResource, T extends ChildCollection<P, C>>
    void to(Class<T> impl) {
      @SuppressWarnings("unchecked")
      Class<V> p = (Class<V>) impl;
      binder.to(p);
    }

    public <C extends RestResource, T extends ChildCollection<P, C>>
    void toProvider(Class<? extends Provider<? extends T>> providerType) {
      @SuppressWarnings("unchecked")
      Class<Provider<V>> p = (Class<Provider<V>>) providerType;
      binder.toProvider(p);
    }
  }
}
