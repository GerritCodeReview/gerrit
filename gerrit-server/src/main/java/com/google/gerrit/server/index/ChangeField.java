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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.server.OrmException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.sql.Timestamp;
import java.util.Map;

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
  public static final int SCHEMA_VERSION = 3;

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

  /** Project containing the change. */
  public static final FieldDef<ChangeData, String> PROJECT =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_PROJECT, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getProject().get();
        }
      };

  /** Reference (aka branch) the change will submit onto. */
  public static final FieldDef<ChangeData, String> REF =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_REF, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getDest().get();
        }
      };

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> TOPIC =
      new FieldDef.Single<ChangeData, String>(
          ChangeQueryBuilder.FIELD_TOPIC, FieldType.EXACT, false) {
        @Override
        public String get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getTopic();
        }
      };

  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> UPDATED =
      new FieldDef.Single<ChangeData, Timestamp>(
          "updated", FieldType.TIMESTAMP, true) {
        @Override
        public Timestamp get(ChangeData input, FillArgs args)
            throws OrmException {
          return input.change(args.db).getLastUpdatedOn();
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
