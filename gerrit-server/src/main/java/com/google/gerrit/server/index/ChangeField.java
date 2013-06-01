// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.server.OrmException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Fields indexed on change documents.
 * <p>
 * Each field corresponds to both a field name supported by
 * {@link ChangeQueryBuilder} for querying that field, and a method on
 * {@link ChangeData} used for populating the corresponding document fields in
 * the secondary index.
 * <p>
 * Used to generate a schema for index implementations that require one.
 */
public class ChangeField {
  /** Increment whenever making schema changes. */
  public static final int SCHEMA_VERSION = 1;

  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> CHANGE_ID =
      new FieldDef.Single<ChangeData, Integer>(ChangeQueryBuilder.FIELD_CHANGE,
          FieldType.INTEGER, true) {
        @Override
        public Integer get(ChangeData input, FillArgs args) {
          return input.getId().get();
        }
      };

  /** Change status string, in the same format as {@code status:}. */
  public static final FieldDef<ChangeData, String> STATUS =
      new FieldDef.Single<ChangeData, String>(ChangeQueryBuilder.FIELD_STATUS,
          FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return ChangeStatusPredicate.VALUES.get(
              input.change(args.db).getStatus());
        }
      };

  /** List of filenames modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> FILE =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_FILE, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.currentFilePaths(args.db, args.patchListCache);
        }
      };

  /** List of base filenames modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> BASENAME =
      new FieldDef.Repeatable<ChangeData, String>(
          ChangeQueryBuilder.FIELD_BASENAME, FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(ChangeData input, FillArgs args)
            throws OrmException {
          return Iterables.transform(
              input.currentFilePaths(args.db, args.patchListCache),
              new Function<String, String>() {
                @Override
                public String apply(String input) {
                  return Files.getNameWithoutExtension(input);
                }
              });
        }
      };

  public static final ImmutableMap<String, FieldDef<ChangeData, ?>> ALL;

  static {
    Map<String, FieldDef<ChangeData, ?>> fields = Maps.newHashMap();
    for (Field f : ChangeField.class.getFields()) {
      if (Modifier.isPublic(f.getModifiers())
          && Modifier.isStatic(f.getModifiers())
          && Modifier.isFinal(f.getModifiers())
          && FieldDef.class.isAssignableFrom(f.getType())) {
        ParameterizedType t = (ParameterizedType) f.getGenericType();
        if (t.getActualTypeArguments()[0] == ChangeData.class) {
          try {
            @SuppressWarnings("unchecked")
            FieldDef<ChangeData, ?> fd = (FieldDef<ChangeData, ?>) f.get(null);
            fields.put(fd.getName(), fd);
          } catch (IllegalArgumentException e) {
            throw new ExceptionInInitializerError(e);
          } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
          }
        } else {
          throw new ExceptionInInitializerError(
              "non-ChangeData ChangeField: " + f);
        }
      }
    }
    if (fields.isEmpty()) {
      throw new ExceptionInInitializerError("no ChangeFields found");
    }
    ALL = ImmutableMap.copyOf(fields);
  }
}
