// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.proto.testing;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;

/**
 * Subject about classes that are serialized into persistent caches or indices.
 *
 * <p>Hand-written {@link com.google.gerrit.server.cache.serialize.CacheSerializer CacheSerializer}
 * and {@link com.google.gerrit.reviewdb.converter.ProtoConverter ProtoConverter} implementations
 * depend on the exact representation of the data stored in a class, so it is important to verify
 * any assumptions about the structure of the serialized classes. This class contains assertions
 * about serialized classes, and should be used for every class that has a custom serializer
 * implementation.
 *
 * <p>Changing fields of a serialized class (or abstract methods, in the case of {@code @AutoValue}
 * classes) will likely require changes to the serializer implementation, and may require bumping
 * the {@link com.google.gerrit.server.cache.PersistentCacheBinding#version(int) version} in the
 * cache binding, in case the representation has changed in such a way that old serialized data
 * becomes unreadable.
 *
 * <p>Changes to a serialized class such as adding or removing fields generally requires a change to
 * the hand-written serializer. Usually, serializer implementations should be written in such a way
 * that new fields are considered optional, and won't require bumping the version.
 */
public class SerializedClassSubject extends Subject {
  public static SerializedClassSubject assertThatSerializedClass(Class<?> actual) {
    // This formulation fails in Eclipse 4.7.3a with "The type
    // SerializedClassSubject does not define SerializedClassSubject() that is
    // applicable here", due to
    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=534694 or a similar bug:
    // return assertAbout(SerializedClassSubject::new).that(actual);
    Subject.Factory<SerializedClassSubject, Class<?>> factory =
        (m, a) -> new SerializedClassSubject(m, a);
    return assertAbout(factory).that(actual);
  }

  private final Class<?> clazz;

  private SerializedClassSubject(FailureMetadata metadata, Class<?> clazz) {
    super(metadata, clazz);
    this.clazz = clazz;
  }

  public void isAbstract() {
    isNotNull();
    if (!Modifier.isAbstract(clazz.getModifiers())) {
      failWithActual(simpleFact("expected class to be abstract"));
    }
  }

  public void isConcrete() {
    isNotNull();
    if (Modifier.isAbstract(clazz.getModifiers())) {
      failWithActual(simpleFact("expected class to be concrete"));
    }
  }

  public void hasFields(Map<String, Type> expectedFields) {
    isConcrete();
    check("fields()")
        .that(
            FieldUtils.getAllFieldsList(clazz)
                .stream()
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .collect(toImmutableMap(Field::getName, Field::getGenericType)))
        .containsExactlyEntriesIn(expectedFields);
  }

  public void hasAutoValueMethods(Map<String, Type> expectedMethods) {
    // Would be nice if we could check clazz is an @AutoValue, but the retention is not RUNTIME.
    isAbstract();
    check("noArgumentAbstractMethods()")
        .that(
            Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .filter(m -> m.getParameters().length == 0)
                .collect(toImmutableMap(Method::getName, Method::getGenericReturnType)))
        .isEqualTo(expectedMethods);
  }

  public void extendsClass(Type superclassType) {
    isNotNull();
    check("getGenericSuperclass()").that(clazz.getGenericSuperclass()).isEqualTo(superclassType);
  }
}
