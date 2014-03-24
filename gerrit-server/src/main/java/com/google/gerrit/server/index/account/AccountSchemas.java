// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.index.account;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.Schema;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/** Secondary index schemas for accounts. */
public class AccountSchemas {
  static final Schema<AccountInfo> V1 = release(
      AccountField.ID,
      AccountField.NAME,
      AccountField.EMAIL);

  private static Schema<AccountInfo> release(Collection<FieldDef<AccountInfo, ?>> fields) {
    return new Schema<AccountInfo>(true, fields);
  }

  @SafeVarargs
  private static Schema<AccountInfo> release(FieldDef<AccountInfo, ?>... fields) {
    return release(Arrays.asList(fields));
  }

  @SafeVarargs
  @SuppressWarnings("unused")
  private static Schema<AccountInfo> developer(FieldDef<AccountInfo, ?>... fields) {
    return new Schema<AccountInfo>(false, Arrays.asList(fields));
  }

  public static final ImmutableMap<Integer, Schema<AccountInfo>> ALL;

  public static Schema<AccountInfo> get(int version) {
    Schema<AccountInfo> schema = ALL.get(version);
    checkArgument(schema != null, "Unrecognized schema version: %s", version);
    return schema;
  }

  public static Schema<AccountInfo> getLatest() {
    return Iterables.getLast(ALL.values());
  }

  static {
    Map<Integer, Schema<AccountInfo>> all = Maps.newTreeMap();
    for (Field f : AccountSchemas.class.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers())
          && Modifier.isFinal(f.getModifiers())
          && Schema.class.isAssignableFrom(f.getType())) {
        ParameterizedType t = (ParameterizedType) f.getGenericType();
        if (t.getActualTypeArguments()[0] == AccountInfo.class) {
          try {
            @SuppressWarnings("unchecked")
            Schema<AccountInfo> schema = (Schema<AccountInfo>) f.get(null);
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
              "non-AccountInfo schema: " + f);
        }
      }
    }
    if (all.isEmpty()) {
      throw new ExceptionInInitializerError("no AccountSchemas found");
    }
    ALL = ImmutableMap.copyOf(all);
  }
}