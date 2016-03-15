// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

public class SchemaUtil {
  public static <V> ImmutableMap<Integer, Schema<V>> schemasFromClass(
      Class<?> schemasClass, Class<V> valueClass) {
    Map<Integer, Schema<V>> schemas = Maps.newTreeMap();
    for (Field f : schemasClass.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers())
          && Modifier.isFinal(f.getModifiers())
          && Schema.class.isAssignableFrom(f.getType())) {
        ParameterizedType t = (ParameterizedType) f.getGenericType();
        if (t.getActualTypeArguments()[0] == valueClass) {
          try {
            @SuppressWarnings("unchecked")
            Schema<V> schema = (Schema<V>) f.get(null);
            checkArgument(f.getName().startsWith("V"));
            schema.setVersion(Integer.parseInt(f.getName().substring(1)));
            schemas.put(schema.getVersion(), schema);
          } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
          }
        } else {
          throw new ExceptionInInitializerError(
              "non-ChangeData schema: " + f);
        }
      }
    }
    if (schemas.isEmpty()) {
      throw new ExceptionInInitializerError("no ChangeSchemas found");
    }
    return ImmutableMap.copyOf(schemas);
  }

  static {
  }

  private SchemaUtil() {
  }
}
