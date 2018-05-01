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

package com.google.gerrit.server.cache.testing;

import com.google.protobuf.ByteString;

/** Static utilities for testing cache serializers. */
public class CacheSerializerTestUtil {

  // TODO(dborowitz): Move to subject
  public static void testExpectedAutoValueMethods(
      Class<?> clazz, Map<String, Type> expectedMethods) {
    // Would be nice if we could check clazz is an @AutoValue, but the retention is not RUNTIME.
    checkArgument(
        Modifier.isAbstract(clazz.getModifiers()), "not an abstract class: %s", clazz.getName());

    assertThat(
            Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .filter(m -> m.getParameters().length == 0)
                .collect(toImmutableMap(Method::getName, Method::getGenericReturnType)))
        .named("no-argument abstract methods on %s", clazz.getName())
        .isEqualTo(expectedMethods);
  }

  public static ByteString bytes(int... ints) {
    byte[] bytes = new byte[ints.length];
    for (int i = 0; i < ints.length; i++) {
      bytes[i] = (byte) ints[i];
    }
    return ByteString.copyFrom(bytes);
  }

  private CacheSerializerTestUtil() {}
}
