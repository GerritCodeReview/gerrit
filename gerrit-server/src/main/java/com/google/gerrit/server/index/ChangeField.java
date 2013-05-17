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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_CHANGE;
import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_FILE;

import com.google.gerrit.server.query.change.ChangeData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

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
public enum ChangeField {
  /** Legacy change ID. */
  CHANGE_ID(FIELD_CHANGE, FieldType.INTEGER, "getIdNum", true),

  /** Modified filenames in the current patch set. */
  FILES(FIELD_FILE, FieldType.EXACT_REPEATABLE, "getCurrentFilePaths", false);

  private static Method getMethod(FieldType type, String methodName) {
    Method method;
    try {
      method = ChangeData.class.getMethod(methodName);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(e);
    } catch (SecurityException e) {
      throw new IllegalArgumentException(e);
    }
    switch (type) {
      case EXACT_REPEATABLE:
        checkRepeatable(method, String.class);
        break;
      case INTEGER:
        check(method, Integer.class);
        break;
      default:
        throw new IllegalArgumentException("invalid field type: " + type);
    }
    return method;
  }

  private static void check(Method method, Class<?> clazz) {
    checkArgument(clazz.isAssignableFrom(method.getReturnType()),
        "expected to return %s: %s", clazz, method);
  }

  private static void checkRepeatable(Method method, Class<?> clazz) {
    java.lang.reflect.Type t = method.getGenericReturnType();
    checkArgument(
        t instanceof ParameterizedType
            && Iterable.class.isAssignableFrom(method.getReturnType()),
        "expected to return Iterable<%s>: %s", clazz, method);
    ParameterizedType pt = (ParameterizedType) method.getGenericReturnType();
    java.lang.reflect.Type[] params = pt.getActualTypeArguments();
    checkArgument(
        params.length == 1 && clazz.isAssignableFrom((Class<?>) params[0]),
        "expected to return Iterable<%s>: %s", clazz, method);
  }

  private final String name;
  private final FieldType type;
  private final Method method;
  private final boolean stored;

  private ChangeField(String name, FieldType type, String methodName,
      boolean stored) {
    this.name = name;
    this.type = type;
    this.method = getMethod(type, methodName);
    this.stored = stored;
  }

  public String getName() {
    return name;
  }

  public FieldType getType() {
    return type;
  }

  public boolean isStored() {
    return stored;
  }

  /**
   * Look up this field on a {@link ChangeData} object.
   *
   * @param cd change data
   * @return method lookup result; the type matches that expected by the
   *     corresponding {@link FieldType} for this field.
   */
  public Object get(ChangeData cd) {
    try {
      return method.invoke(cd);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
