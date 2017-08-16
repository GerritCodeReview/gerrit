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

import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;

/** Guice DSL for binding {@link RestView} implementations. */
public abstract class RestApiModule extends FactoryModule {
  protected static final String GET = "GET";
  protected static final String PUT = "PUT";
  protected static final String DELETE = "DELETE";
  protected static final String POST = "POST";

  protected <R extends RestResource> ReadViewBinder<R> get(TypeLiteral<RestView<R>> viewType) {
    return new ReadViewBinder<>(view(viewType, GET, "/"));
  }

  protected <R extends RestResource> ModifyViewBinder<R> put(TypeLiteral<RestView<R>> viewType) {
    return new ModifyViewBinder<>(view(viewType, PUT, "/"));
  }

  protected <R extends RestResource> ModifyViewBinder<R> post(TypeLiteral<RestView<R>> viewType) {
    return new ModifyViewBinder<>(view(viewType, POST, "/"));
  }

  protected <R extends RestResource> ModifyViewBinder<R> delete(TypeLiteral<RestView<R>> viewType) {
    return new ModifyViewBinder<>(view(viewType, DELETE, "/"));
  }

  protected <R extends RestResource> ReadViewBinder<R> get(
      TypeLiteral<RestView<R>> viewType, String name) {
    return new ReadViewBinder<>(view(viewType, GET, name));
  }

  protected <R extends RestResource> ModifyViewBinder<R> put(
      TypeLiteral<RestView<R>> viewType, String name) {
    return new ModifyViewBinder<>(view(viewType, PUT, name));
  }

  protected <R extends RestResource> ModifyViewBinder<R> post(
      TypeLiteral<RestView<R>> viewType, String name) {
    return new ModifyViewBinder<>(view(viewType, POST, name));
  }

  protected <R extends RestResource> ModifyViewBinder<R> delete(
      TypeLiteral<RestView<R>> viewType, String name) {
    return new ModifyViewBinder<>(view(viewType, DELETE, name));
  }

  protected <P extends RestResource> ChildCollectionBinder<P> child(
      TypeLiteral<RestView<P>> type, String name) {
    return new ChildCollectionBinder<>(view(type, GET, name));
  }

  protected <R extends RestResource> LinkedBindingBuilder<RestView<R>> view(
      TypeLiteral<RestView<R>> viewType, String method, String name) {
    return bind(viewType).annotatedWith(export(method, name));
  }

  private static Export export(String method, String name) {
    if (name.length() > 1 && name.startsWith("/")) {
      // Views may be bound as "/" to mean the resource itself, or
      // as "status" as in "/type/{id}/status". Don't bind "/status"
      // if the caller asked for that, bind what the server expects.
      name = name.substring(1);
    }
    return Exports.named(method + "." + name);
  }

  public static class ReadViewBinder<P extends RestResource> {
    private final LinkedBindingBuilder<RestView<P>> binder;

    private ReadViewBinder(LinkedBindingBuilder<RestView<P>> binder) {
      this.binder = binder;
    }

    public <T extends RestReadView<P>> ScopedBindingBuilder to(Class<T> impl) {
      return binder.to(impl);
    }

    public <T extends RestReadView<P>> void toInstance(T impl) {
      binder.toInstance(impl);
    }

    public <T extends RestReadView<P>> ScopedBindingBuilder toProvider(
        Class<? extends Provider<? extends T>> providerType) {
      return binder.toProvider(providerType);
    }

    public <T extends RestReadView<P>> ScopedBindingBuilder toProvider(
        Provider<? extends T> provider) {
      return binder.toProvider(provider);
    }
  }

  public static class ModifyViewBinder<P extends RestResource> {
    private final LinkedBindingBuilder<RestView<P>> binder;

    private ModifyViewBinder(LinkedBindingBuilder<RestView<P>> binder) {
      this.binder = binder;
    }

    public <T extends RestModifyView<P, ?>> ScopedBindingBuilder to(Class<T> impl) {
      return binder.to(impl);
    }

    public <T extends RestModifyView<P, ?>> void toInstance(T impl) {
      binder.toInstance(impl);
    }

    public <T extends RestModifyView<P, ?>> ScopedBindingBuilder toProvider(
        Class<? extends Provider<? extends T>> providerType) {
      return binder.toProvider(providerType);
    }

    public <T extends RestModifyView<P, ?>> ScopedBindingBuilder toProvider(
        Provider<? extends T> provider) {
      return binder.toProvider(provider);
    }
  }

  public static class ChildCollectionBinder<P extends RestResource> {
    private final LinkedBindingBuilder<RestView<P>> binder;

    private ChildCollectionBinder(LinkedBindingBuilder<RestView<P>> binder) {
      this.binder = binder;
    }

    public <C extends RestResource, T extends ChildCollection<P, C>> ScopedBindingBuilder to(
        Class<T> impl) {
      return binder.to(impl);
    }

    public <C extends RestResource, T extends ChildCollection<P, C>> void toInstance(T impl) {
      binder.toInstance(impl);
    }

    public <C extends RestResource, T extends ChildCollection<P, C>>
        ScopedBindingBuilder toProvider(Class<? extends Provider<? extends T>> providerType) {
      return binder.toProvider(providerType);
    }

    public <C extends RestResource, T extends ChildCollection<P, C>>
        ScopedBindingBuilder toProvider(Provider<? extends T> provider) {
      return binder.toProvider(provider);
    }
  }
}
