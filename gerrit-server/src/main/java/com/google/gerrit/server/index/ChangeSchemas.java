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
// limitations under the License.

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.server.query.change.ChangeData;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/** Secondary index schemas for changes. */
public class ChangeSchemas {
  @SuppressWarnings({"unchecked", "deprecation"})
  static final Schema<ChangeData> V1 = release(
        ChangeField.LEGACY_ID,
        ChangeField.ID,
        ChangeField.STATUS,
        ChangeField.PROJECT,
        ChangeField.REF,
        ChangeField.TOPIC,
        ChangeField.UPDATED,
        ChangeField.LEGACY_SORTKEY,
        ChangeField.FILE,
        ChangeField.OWNER,
        ChangeField.REVIEWER,
        ChangeField.COMMIT,
        ChangeField.TR,
        ChangeField.LABEL,
        ChangeField.REVIEWED,
        ChangeField.COMMIT_MESSAGE,
        ChangeField.COMMENT);

  @SuppressWarnings({"unchecked", "deprecation"})
  static final Schema<ChangeData> V2 = release(
        ChangeField.LEGACY_ID,
        ChangeField.ID,
        ChangeField.STATUS,
        ChangeField.PROJECT,
        ChangeField.REF,
        ChangeField.TOPIC,
        ChangeField.UPDATED,
        ChangeField.LEGACY_SORTKEY,
        ChangeField.FILE,
        ChangeField.OWNER,
        ChangeField.REVIEWER,
        ChangeField.COMMIT,
        ChangeField.TR,
        ChangeField.LABEL,
        ChangeField.REVIEWED,
        ChangeField.COMMIT_MESSAGE,
        ChangeField.COMMENT,
        ChangeField.CHANGE,
        ChangeField.APPROVAL);

  @SuppressWarnings("unchecked")
  static final Schema<ChangeData> V3 = release(
        ChangeField.LEGACY_ID,
        ChangeField.ID,
        ChangeField.STATUS,
        ChangeField.PROJECT,
        ChangeField.REF,
        ChangeField.TOPIC,
        ChangeField.UPDATED,
        ChangeField.SORTKEY,
        ChangeField.FILE,
        ChangeField.OWNER,
        ChangeField.REVIEWER,
        ChangeField.COMMIT,
        ChangeField.TR,
        ChangeField.LABEL,
        ChangeField.REVIEWED,
        ChangeField.COMMIT_MESSAGE,
        ChangeField.COMMENT,
        ChangeField.CHANGE,
        ChangeField.APPROVAL);

  // For upgrade to Lucene 4.4.0 index format only.
  static final Schema<ChangeData> V4 = release(V3.getFields().values());

  private static Schema<ChangeData> release(Collection<FieldDef<ChangeData, ?>> fields) {
    return new Schema<ChangeData>(true, fields);
  }

  private static Schema<ChangeData> release(FieldDef<ChangeData, ?>... fields) {
    return release(Arrays.asList(fields));
  }

  @SuppressWarnings("unused")
  private static Schema<ChangeData> developer(FieldDef<ChangeData, ?>... fields) {
    return new Schema<ChangeData>(false, Arrays.asList(fields));
  }

  public static final ImmutableMap<Integer, Schema<ChangeData>> ALL;

  public static Schema<ChangeData> get(int version) {
    Schema<ChangeData> schema = ALL.get(version);
    checkArgument(schema != null, "Unrecognized schema version: %s", version);
    return schema;
  }

  public static Schema<ChangeData> getLatest() {
    return Iterables.getLast(ALL.values());
  }

  static {
    Map<Integer, Schema<ChangeData>> all = Maps.newTreeMap();
    for (Field f : ChangeSchemas.class.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers())
          && Modifier.isFinal(f.getModifiers())
          && Schema.class.isAssignableFrom(f.getType())) {
        ParameterizedType t = (ParameterizedType) f.getGenericType();
        if (t.getActualTypeArguments()[0] == ChangeData.class) {
          try {
            @SuppressWarnings("unchecked")
            Schema<ChangeData> schema = (Schema<ChangeData>) f.get(null);
            checkArgument(f.getName().startsWith("V"));
            schema.setVersion(Integer.parseInt(f.getName().substring(1)));
            all.put(schema.getVersion(), schema);
          } catch (IllegalArgumentException e) {
            throw new ExceptionInInitializerError(e);
          } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
          }
        } else {
          throw new ExceptionInInitializerError(
              "non-ChangeData schema: " + f);
        }
      }
    }
    if (all.isEmpty()) {
      throw new ExceptionInInitializerError("no ChangeSchemas found");
    }
    ALL = ImmutableMap.copyOf(all);
  }
}
