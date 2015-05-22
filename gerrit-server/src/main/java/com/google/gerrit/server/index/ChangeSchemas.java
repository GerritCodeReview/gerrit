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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.server.query.change.ChangeData;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Map;

/** Secondary index schemas for changes. */
public class ChangeSchemas {
  @SuppressWarnings("deprecation")
  static final Schema<ChangeData> V11 = schema(
        ChangeField.LEGACY_ID,
        ChangeField.ID,
        ChangeField.STATUS,
        ChangeField.PROJECT,
        ChangeField.PROJECTS,
        ChangeField.REF,
        ChangeField.LEGACY_TOPIC,
        ChangeField.UPDATED,
        ChangeField.FILE_PART,
        ChangeField.PATH,
        ChangeField.OWNER,
        ChangeField.REVIEWER,
        ChangeField.COMMIT,
        ChangeField.TR,
        ChangeField.LABEL,
        ChangeField.REVIEWED,
        ChangeField.COMMIT_MESSAGE,
        ChangeField.COMMENT,
        ChangeField.CHANGE,
        ChangeField.APPROVAL,
        ChangeField.LEGACY_MERGEABLE,
        ChangeField.ADDED,
        ChangeField.DELETED,
        ChangeField.DELTA);

  // For upgrade to Lucene 4.10.0 index format only.
  static final Schema<ChangeData> V12 = schema(V11.getFields().values());

  @SuppressWarnings("deprecation")
  static final Schema<ChangeData> V13 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.LEGACY_TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.REVIEWED,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.LEGACY_MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG);

  @SuppressWarnings("deprecation")
  static final Schema<ChangeData> V14 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.LEGACY_TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.REVIEWED,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG);

  @SuppressWarnings("deprecation")
  static final Schema<ChangeData> V15 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.LEGACY_TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.REVIEWED,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG,
      ChangeField.COMMENTBY);

  static final Schema<ChangeData> V16 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.REVIEWED,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG,
      ChangeField.COMMENTBY);

  static final Schema<ChangeData> V17 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.REVIEWED,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG,
      ChangeField.COMMENTBY,
      ChangeField.PATCH_SET);

  static final Schema<ChangeData> V18 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.REVIEWED,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG,
      ChangeField.COMMENTBY,
      ChangeField.PATCH_SET,
      ChangeField.GROUP);

  private static Schema<ChangeData> schema(Collection<FieldDef<ChangeData, ?>> fields) {
    return new Schema<>(ImmutableList.copyOf(fields));
  }

  @SafeVarargs
  private static Schema<ChangeData> schema(FieldDef<ChangeData, ?>... fields) {
    return schema(ImmutableList.copyOf(fields));
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
