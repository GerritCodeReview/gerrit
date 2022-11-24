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

package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;

public class SchemaUtil {
  public static <V> ImmutableSortedMap<Integer, Schema<V>> schemasFromClass(
      Class<?> schemasClass, Class<V> valueClass) {
    Map<Integer, Schema<V>> schemas = new HashMap<>();
    for (Field f : schemasClass.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers())
          && Modifier.isFinal(f.getModifiers())
          && Schema.class.isAssignableFrom(f.getType())) {
        ParameterizedType t = (ParameterizedType) f.getGenericType();
        if (t.getActualTypeArguments()[0] == valueClass) {
          try {
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Schema<V> schema = (Schema<V>) f.get(null);
            checkArgument(f.getName().startsWith("V"));
            int versionName = Integer.parseInt(f.getName().substring(1));
            checkArgument(
                versionName == schema.getVersion(),
                "Schema version %s does not match its name %s",
                schema.getVersion(),
                f.getName());
            schemas.put(schema.getVersion(), schema);
          } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
          }
        } else {
          throw new IllegalArgumentException(
              "non-" + schemasClass.getSimpleName() + " schema: " + f);
        }
      }
    }
    if (schemas.isEmpty()) {
      throw new ExceptionInInitializerError("no ChangeSchemas found");
    }
    return ImmutableSortedMap.copyOf(schemas);
  }

  @SafeVarargs
  public static <V> Schema<V> schema(FieldDef<V, ?>... fields) {
    return new Schema.Builder<V>().version(0).add(fields).build();
  }

  @SafeVarargs
  public static <V> Schema<V> schema(int version, FieldDef<V, ?>... fields) {
    return new Schema.Builder<V>().version(version).add(fields).build();
  }

  public static <V> Schema<V> schema(int version, ImmutableList<FieldDef<V, ?>> fields) {
    return new Schema.Builder<V>().version(version).add(fields).build();
  }

  @SafeVarargs
  public static <V> Schema<V> schema(Schema<V> schema, FieldDef<V, ?>... moreFields) {
    return new Schema.Builder<V>().add(schema).add(moreFields).build();
  }

  public static <V> Schema<V> schema(
      int version,
      ImmutableList<FieldDef<V, ?>> fieldDefs,
      ImmutableList<IndexedField<V, ?>> indexedFields,
      ImmutableList<IndexedField<V, ?>.SearchSpec> searchSpecs) {
    return new Schema.Builder<V>()
        .version(version)
        .add(fieldDefs)
        .addIndexedFields(indexedFields)
        .addSearchSpecs(searchSpecs)
        .build();
  }

  public static <V> Schema<V> schema(
      int version,
      ImmutableList<IndexedField<V, ?>> indexedFields,
      ImmutableList<IndexedField<V, ?>.SearchSpec> searchSpecs) {
    return new Schema.Builder<V>()
        .version(version)
        .addIndexedFields(indexedFields)
        .addSearchSpecs(searchSpecs)
        .build();
  }

  public static <V> Schema<V> schema(
      Schema<V> schema,
      ImmutableList<FieldDef<V, ?>> fieldDefs,
      ImmutableList<IndexedField<V, ?>> indexedFields,
      ImmutableList<IndexedField<V, ?>.SearchSpec> searchSpecs) {
    return new Schema.Builder<V>()
        .add(schema)
        .add(fieldDefs)
        .addIndexedFields(indexedFields)
        .addSearchSpecs(searchSpecs)
        .build();
  }

  public static <V> Schema<V> schema(
      Schema<V> schema,
      ImmutableList<IndexedField<V, ?>> indexedFields,
      ImmutableList<IndexedField<V, ?>.SearchSpec> searchSpecs) {
    return new Schema.Builder<V>()
        .add(schema)
        .addIndexedFields(indexedFields)
        .addSearchSpecs(searchSpecs)
        .build();
  }

  public static <V> Schema<V> schema(
      ImmutableList<FieldDef<V, ?>> fieldDefs,
      ImmutableList<IndexedField<V, ?>> indexFields,
      ImmutableList<IndexedField<V, ?>.SearchSpec> searchSpecs) {
    return schema(/* version= */ 0, fieldDefs, indexFields, searchSpecs);
  }

  public static Set<String> getPersonParts(PersonIdent person) {
    if (person == null) {
      return ImmutableSet.of();
    }
    return getNameParts(person.getName(), Collections.singleton(person.getEmailAddress()));
  }

  public static Set<String> getNameParts(String name) {
    return getNameParts(name, Collections.emptySet());
  }

  public static Set<String> getNameParts(String name, Iterable<String> emails) {
    Splitter at = Splitter.on('@');
    Splitter s = Splitter.on(CharMatcher.anyOf("@.- /_")).omitEmptyStrings();
    HashSet<String> parts = new HashSet<>();
    for (String email : emails) {
      if (email == null) {
        continue;
      }
      String lowerEmail = email.toLowerCase(Locale.US);
      parts.add(lowerEmail);
      Iterables.addAll(parts, at.split(lowerEmail));
      Iterables.addAll(parts, s.split(lowerEmail));
    }
    if (name != null) {
      Iterables.addAll(parts, s.split(name.toLowerCase(Locale.US)));
    }
    return parts;
  }

  private SchemaUtil() {}
}
