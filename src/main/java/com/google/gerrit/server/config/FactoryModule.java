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

package com.google.gerrit.server.config;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.assistedinject.FactoryProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public abstract class FactoryModule extends AbstractModule {
  /**
   * Register an assisted injection factory.
   * <p>
   * This function provides an automatic way to define a factory that creates a
   * concrete type through assited injection. For example to configure the
   * following assisted injection case:
   *
   * <pre>
   * public class Foo {
   *   public interface Factory {
   *     Foo create(int a);
   *   }
   *   &#064;Inject
   *   Foo(Logger log, @Assisted int a) {...}
   * }
   * </pre>
   *
   * Just pass {@code Foo.Factory.class} to this method. The factory will be
   * generated to return its one return type as declared in the creation method.
   *
   * @param <F>
   * @param factory interface which specifies the bean factory method.
   */
  protected <F> void factory(final Class<F> factory) {
    factory(Key.get(factory), factory);
  }

  /**
   * Register an assisted injection factory.
   * <p>
   * This function provides an automatic way to define a factory that creates a
   * concrete type through assited injection. For example to configure the
   * following assisted injection case:
   *
   * <pre>
   * public class Foo {
   *   public interface Factory {
   *     Foo create(int a);
   *   }
   *   &#064;Inject
   *   Foo(Logger log, @Assisted int a) {...}
   * }
   * </pre>
   *
   * Just pass {@code Foo.Factory.class} to this method. The factory will be
   * generated to return its one return type as declared in the creation method.
   *
   * @param <F>
   * @param key key to bind with in Guice bindings.
   * @param factory interface which specifies the bean factory method.
   */
  protected <F> void factory(final Key<F> key, final Class<F> factory) {
    final Method[] methods = factory.getDeclaredMethods();
    switch (methods.length) {
      case 1: {
        final Class<?> result = methods[0].getReturnType();
        if (isAbstract(result)) {
          addError("Factory " + factory.getName() + " returns abstract result.");
        } else {
          bind(key).toProvider(FactoryProvider.newFactory(factory, result));
        }
        break;
      }

      case 0:
        addError("Factory " + factory.getName() + " has no create method.");
        break;

      default:
        addError("Factory " + factory.getName()
            + " has more than one create method.");
        break;
    }
  }

  private static boolean isAbstract(final Class<?> result) {
    return result.isInterface()
        || (result.getModifiers() & Modifier.ABSTRACT) == Modifier.ABSTRACT;
  }
}
