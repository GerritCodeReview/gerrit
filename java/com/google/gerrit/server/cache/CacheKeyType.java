// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GeneralProjectName;
import java.util.HashMap;
import java.util.Map;

/** Class for registering cache key types */
public class CacheKeyType {
  private static final Map<String, Class<?>> keyTypesByString = new HashMap<>();

  static {
    registerKeyType("account_id", Account.Id.class);
    registerKeyType("account_group_id", AccountGroup.Id.class);
    registerKeyType("account_group_uuid", AccountGroup.UUID.class);
    registerKeyType("project_name", GeneralProjectName.class);
  }

  /**
   * Register a key type and associated class.
   *
   * @param keyType The key type to register.
   * @param keyClass The key class to register.
   */
  public static void registerKeyType(String keyType, Class<?> keyClass) {
    keyTypesByString.put(keyType, keyClass);
  }

  /**
   * Get the class for a key type.
   *
   * @param type The type.
   * @return The key class, or null if no class is registered with the given type
   */
  public static Class<?> getKeyClass(String type) {
    return keyTypesByString.get(type);
  }

  /**
   * Get a copy of all currently registered keys.
   *
   * <p>The key is the one given to the keyType parameter of the {@link #registerKeyType} method.
   *
   * @return ImmutableMap of key types, key classes.
   */
  public static ImmutableMap<String, Class<?>> getRegisteredKeys() {
    return ImmutableMap.copyOf(keyTypesByString);
  }
}
