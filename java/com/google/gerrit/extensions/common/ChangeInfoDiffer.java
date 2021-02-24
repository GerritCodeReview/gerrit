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

import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Gets the differences between two {@link ChangeInfo}s.
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
   * oldChangeInfo} and {@code newChangeInfo}. When there's an unrepeated field that's not a String,
   * primitive, or enum, its fields are only returned when they differ.
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
    return ChangeInfoDifference.create(
        /* added= */ getAdded(oldChangeInfo, newChangeInfo),
        /* removed= */ getAdded(newChangeInfo, oldChangeInfo));
  }

  @SuppressWarnings("unchecked") // reflection is used to construct instances of T
  private static <T> T getAdded(T oldValue, T newValue) {
    T toPopulate = (T) construct(newValue.getClass());
    if (toPopulate == null) {
      return null;
    }

    for (Field field : newValue.getClass().getDeclaredFields()) {
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
      } else if (newFieldObj instanceof Collection) {
        set(
            field,
            toPopulate,
            getAddedForCollection((Collection<?>) oldFieldObj, (Collection<?>) newFieldObj));
      } else if (newFieldObj instanceof Map) {
        set(field, toPopulate, getAddedForMap((Map<?, ?>) oldFieldObj, (Map<?, ?>) newFieldObj));
      } else {
        // Recurse to set all fields in the non-primitive object.
        set(field, toPopulate, getAdded(oldFieldObj, newFieldObj));
      }
    }
    return toPopulate;
  }

  private static boolean isSimple(Class<?> c) {
    return c.isPrimitive()
        || c.isEnum()
        || c.isAssignableFrom(String.class)
        || c.isAssignableFrom(Integer.class)
        || c.isAssignableFrom(Boolean.class)
        || c.isAssignableFrom(Timestamp.class)
        || c.isAssignableFrom(Comparator.class);
  }

  /** @return null if nothing has been added to {@code oldCollection} */
  private static ImmutableList<?> getAddedForCollection(
      Collection<?> oldCollection, Collection<?> newCollection) {
    ImmutableList<?> notInOldCollection = getAdditions(oldCollection, newCollection);
    return notInOldCollection.isEmpty() ? null : notInOldCollection;
  }

  private static ImmutableList<?> getAdditions(
      Collection<?> oldCollection, Collection<?> newCollection) {
    List<?> newValuesToFilter = new ArrayList<>(newCollection);
    oldCollection.forEach(newValuesToFilter::remove);
    return ImmutableList.copyOf(newValuesToFilter);
  }

  /** @return null if nothing has been added to {@code oldMap} */
  private static ImmutableMap<Object, Object> getAddedForMap(Map<?, ?> oldMap, Map<?, ?> newMap) {
    ImmutableMap.Builder<Object, Object> additionsBuilder = ImmutableMap.builder();
    for (Map.Entry<?, ?> entry : newMap.entrySet()) {
      Object added = getAdded(oldMap.get(entry.getKey()), entry.getValue());
      if (added != null) {
        additionsBuilder.put(entry.getKey(), added);
      }
    }
    ImmutableMap<Object, Object> additions = additionsBuilder.build();
    return additions.isEmpty() ? null : additions;
  }

  private static Object construct(Class<?> c) {
    // Only use constructors without parameters because we can't determine what values to pass.
    return stream(c.getDeclaredConstructors())
        .filter(constructor -> constructor.getParameterCount() == 0)
        .findAny()
        .map(ChangeInfoDiffer::construct)
        .orElse(null);
  }

  private static Object construct(Constructor<?> constructor) {
    try {
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static Object get(Field field, Object obj) {
    try {
      return field.get(obj);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  private static void set(Field field, Object obj, Object value) {
    try {
      field.set(obj, value);
    } catch (IllegalAccessException e) {
      // Do nothing. The consequence is just that the field cannot be set and will not be returned.
    }
  }

  private ChangeInfoDiffer() {}
}
