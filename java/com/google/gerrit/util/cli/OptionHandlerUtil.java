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

package com.google.gerrit.util.cli;

import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Types;
import java.lang.reflect.Type;
import org.kohsuke.args4j.spi.OptionHandler;

/** Utilities to support creating OptionHandler instances. */
public class OptionHandlerUtil {
  /** Generate a key for an {@link OptionHandlerFactory} in Guice. */
  @SuppressWarnings("unchecked")
  public static <T> Key<OptionHandlerFactory<T>> keyFor(Class<T> valueType) {
    final Type factoryType = Types.newParameterizedType(OptionHandlerFactory.class, valueType);
    return (Key<OptionHandlerFactory<T>>) Key.get(factoryType);
  }

  @SuppressWarnings("unchecked")
  private static <T> Key<OptionHandler<T>> handlerOf(Class<T> type) {
    final Type handlerType = Types.newParameterizedTypeWithOwner(null, OptionHandler.class, type);
    return (Key<OptionHandler<T>>) Key.get(handlerType);
  }

  public static <T> Module moduleFor(Class<T> type, Class<? extends OptionHandler<T>> impl) {
    return new FactoryModuleBuilder().implement(handlerOf(type), impl).build(keyFor(type));
  }

  private OptionHandlerUtil() {}
}
