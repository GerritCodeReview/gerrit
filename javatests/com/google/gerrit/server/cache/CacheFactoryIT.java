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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.Test;

public class CacheFactoryIT extends AbstractDaemonTest {
  private static final String CACHE_NAME = "dummy-cache";

  @Inject private DynamicMap<CacheDef<?, ?>> cacheDefs;

  @Override
  public com.google.inject.Module createModule() {
    return new TestModule();
  }

  @Test
  public void newCacheDefIsAvailableInDynamicMap() {
    assertThat(cacheDefs).isNotNull();

    Optional<? extends CacheDef<?, ?>> def =
        StreamSupport.stream(cacheDefs.spliterator(), false)
            .filter(e -> e.getExportName().equals(CACHE_NAME))
            .map(Extension::get)
            .findFirst();
    assertThat(def).isPresent();
    assertThat(def.get().keyType().getRawType()).isEqualTo(String.class);
    assertThat(def.get().valueType().getRawType()).isEqualTo(String.class);
  }

  public static class TestModule extends CacheModule {

    @Override
    protected void configure() {
      cache(CACHE_NAME, String.class, String.class);
    }
  }
}
