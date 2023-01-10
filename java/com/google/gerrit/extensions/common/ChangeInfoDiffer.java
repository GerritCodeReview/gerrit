// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Gets the differences between two {@link ChangeInfo}s.
 *
 * <p>This must be in package {@code com.google.gerrit.extensions.common} for access to protected
 * constructors.
 *
 * <p>This assumes that every class reachable from {@link ChangeInfo} has a non-private constructor
 * with zero parameters and overrides the equals method.
 */
public final class ChangeInfoDiffer {

  /**
   * Returns the difference between two instances of {@link ChangeInfo}.
   *
   * <p>The {@link ChangeInfoDifference} returned has the following properties:
   *
   * <p>Unrepeated fields are present in the difference returned when they differ between {@code
   * oldChangeInfo} and {@code newChangeInfo}. When there's an unrepeated field that's not a {@link
   * String}, primitive, or enum, its fields are only returned when they differ.
   *
   * <p>Entries in {@link Map} fields are returned when a key is present in {@code newChangeInfo}
   * and not {@code oldChangeInfo}. If a key is present in both, the diff of the value is returned.
   *
   * <p>{@link Collection} fields in {@link ChangeInfoDifference#added()} contain only items found
   * in {@code newChangeInfo} and not {@code oldChangeInfo}.
   *
   * <p>{@link Collection} fields in {@link ChangeInfoDifference#removed()} contain only items found
   * in {@code oldChangeInfo} and not {@code newChangeInfo}.
   *
   * @param oldChangeInfo the previous {@link ChangeInfo} to diff against {@code newChangeInfo}
   * @param newChangeInfo the {@link ChangeInfo} to diff against {@code oldChangeInfo}
   * @return the difference between the given {@link ChangeInfo}s
   */
  public static ChangeInfoDifference getDifference(
      ChangeInfo oldChangeInfo, ChangeInfo newChangeInfo) {
    return ChangeInfoDifference.builder()
        .setOldChangeInfo(oldChangeInfo)
        .setNewChangeInfo(newChangeInfo)
        .setAdded(getAdded(oldChangeInfo, newChangeInfo))
        .setRemoved(getAdded(newChangeInfo, oldChangeInfo))
        .build();
  }

  @SuppressWarnings("unchecked") // reflection is used to construct instances of T
  private static <T> T getAdded(T oldValue, T newValue) {
    if (newValue instanceof Collection) {
      List<?> result = getAddedForCollection((Collection<?>) oldValue, (Collection<?>) newValue);
      return (T) result;
    }

    if (newValue instanceof Map) {
      Map<?, ?> result = getAddedForMap((Map<?, ?>) oldValue, (Map<?, ?>) newValue);
      return (T) result;
    }

    T toPopulate = (T) construct(newValue.getClass());
    if (toPopulate == null) {
      return null;
    }

    for (Field field : newValue.getClass().getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
        continue;
      }

      Object newFieldObj = get(field, newValue);
      if (oldValue == null || newFieldObj == null) {
        set(field, toPopulate, newFieldObj);
        continue;
      }

      Object oldFieldObj = get(field, oldValue);
      if (newFieldObj.equals(oldFieldObj)) {
        continue;
      }

      if (isSimple(field.getType()) || oldFieldObj == null) {
        set(field, toPopulate, newFieldObj);
      } else if (newFieldObj instanceof Collection || newFieldObj instanceof Map) {
        set(field, toPopulate, getAdded(oldFieldObj, newFieldObj));
      } else {
        // Recurse to set all fields in the non-primitive object.
        set(field, toPopulate, getAdded(oldFieldObj, newFieldObj));
      }
    }
    return toPopulate;
  }

  @VisibleForTesting
  static boolean isSimple(Class<?> c) {
    return c.isPrimitive()
        || c.isEnum()
        || String.class.isAssignableFrom(c)
        || Number.class.isAssignableFrom(c)
        || Boolean.class.isAssignableFrom(c)
        || Timestamp.class.isAssignableFrom(c);
  }

  @VisibleForTesting
  static Object construct(Class<?> c) {
    // Only use constructors without parameters because we can't determine what values to pass.
    return stream(c.getDeclaredConstructors())
        .filter(constructor -> constructor.getParameterCount() == 0)
        .findAny()
        .map(ChangeInfoDiffer::construct)
        .orElseThrow(
            () ->
                new IllegalStateException("Class " + c + " must have a zero argument constructor"));
  }

  private static Object construct(Constructor<?> constructor) {
    try {
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to construct class " + constructor.getName(), e);
    }
  }

  /** Returns {@code null} if nothing has been added to {@code oldCollection} */
  @Nullable
  private static ImmutableList<?> getAddedForCollection(
      @Nullable Collection<?> oldCollection, Collection<?> newCollection) {
    ImmutableList<?> notInOldCollection = getAdditionsForCollection(oldCollection, newCollection);
    return notInOldCollection.isEmpty() ? null : notInOldCollection;
  }

  @Nullable
  private static ImmutableList<Object> getAdditionsForCollection(
      @Nullable Collection<?> oldCollection, Collection<?> newCollection) {
    if (oldCollection == null) return ImmutableList.copyOf(newCollection);

    Map<Object, List<Object>> duplicatesMap = newCollection.stream().collect(groupingBy(v -> v));
    oldCollection.forEach(
        v -> {
          if (duplicatesMap.containsKey(v)) {
            duplicatesMap.get(v).remove(v);
          }
        });
    return duplicatesMap.values().stream().flatMap(Collection::stream).collect(toImmutableList());
  }

  /** Returns {@code null} if nothing has been added to {@code oldMap} */
  @Nullable
  private static ImmutableMap<Object, Object> getAddedForMap(
      @Nullable Map<?, ?> oldMap, Map<?, ?> newMap) {
    ImmutableMap<Object, Object> notInOldMap = getAdditionsForMap(oldMap, newMap);
    return notInOldMap.isEmpty() ? null : notInOldMap;
  }

  @Nullable
  private static ImmutableMap<Object, Object> getAdditionsForMap(
      @Nullable Map<?, ?> oldMap, Map<?, ?> newMap) {
    if (oldMap == null) {
      return ImmutableMap.copyOf(newMap);
    }
    ImmutableMap.Builder<Object, Object> additionsBuilder = ImmutableMap.builder();
    for (Map.Entry<?, ?> entry : newMap.entrySet()) {
      Object added = getAdded(oldMap.get(entry.getKey()), entry.getValue());
      if (added != null) {
        additionsBuilder.put(entry.getKey(), added);
      }
    }
    return additionsBuilder.build();
  }

  private static Object get(Field field, Object obj) {
    try {
      return field.get(obj);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          String.format("Access denied getting field %s in %s", field.getName(), obj.getClass()),
          e);
    }
  }

  private static void set(Field field, Object obj, Object value) {
    try {
      field.set(obj, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          String.format(
              "Access denied setting field %s in %s", field.getName(), obj.getClass().getName()),
          e);
    }
  }

  private ChangeInfoDiffer() {}
}
