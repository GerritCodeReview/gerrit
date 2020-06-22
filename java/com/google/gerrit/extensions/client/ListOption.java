// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.Set;

/** Enum that can be expressed as a bitset in query parameters. */
public interface ListOption {
  int getValue();

  static <T extends Enum<T> & ListOption> EnumSet<T> fromBits(Class<T> clazz, int v) {
    EnumSet<T> r = EnumSet.noneOf(clazz);
    T[] values;
    try {
      @SuppressWarnings("unchecked")
      T[] tmp = (T[]) clazz.getMethod("values").invoke(null);
      values = tmp;
    } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
    for (T o : values) {
      if ((v & (1 << o.getValue())) != 0) {
        r.add(o);
        v &= ~(1 << o.getValue());
      }
      if (v == 0) {
        return r;
      }
    }
    if (v != 0) {
      throw new IllegalArgumentException(
          "unknown " + clazz.getName() + ": " + Integer.toHexString(v));
    }
    return r;
  }

  static <T extends Enum<T> & ListOption> String toHex(Set<T> options) {
    int v = 0;
    for (T option : options) {
      v |= 1 << option.getValue();
    }

    return Integer.toHexString(v);
  }
}
