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

package com.google.gerrit.gpg;

import com.google.common.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Maintain map from subkey id to master key id. This is needed for fast access to the key ring when
 * subkeys are used for signing.
 */
@Singleton
public class SubkeyToMasterKeyCache {
  public static final String SUBKEY_MASTER_KEY = "subkey_master_key";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(SUBKEY_MASTER_KEY, String.class, String.class);
      }
    };
  }

  private final Cache<String, String> cache;

  @Inject
  SubkeyToMasterKeyCache(@Named(SUBKEY_MASTER_KEY) Cache<String, String> cache) {
    this.cache = cache;
  }

  public String get(String subkey) {
    return cache.getIfPresent(subkey);
  }

  public void put(String subkey, String master) {
    cache.put(subkey, master);
  }

  public void remove(String subkey) {
    cache.invalidate(subkey);
  }
}
