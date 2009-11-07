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
import com.google.inject.TypeLiteral;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.lang.reflect.Type;

/** Utilities to support creating OptionHandler instances. */
public class OptionHandlerUtil {
  /** Generate a key for an {@link OptionHandlerFactory} in Guice. */
  @SuppressWarnings("unchecked")
  public static <T> Key<OptionHandlerFactory<T>> keyFor(final Class<T> valueType) {
    final Type factoryType =
        new ParameterizedTypeImpl(null, OptionHandlerFactory.class, valueType);

    return (Key<OptionHandlerFactory<T>>) Key.get(TypeLiteral.get(factoryType));
  }

  private OptionHandlerUtil() {
  }
}
