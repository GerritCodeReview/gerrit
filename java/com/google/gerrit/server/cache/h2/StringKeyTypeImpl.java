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

package com.google.gerrit.server.cache.h2;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.gerrit.server.cache.CacheSerializer;

class StringKeyTypeImpl<V> implements EntryType<String, V> {
  private final CacheSerializer<String> keySerializer;
  private final CacheSerializer<V> valueSerializer;

  StringKeyTypeImpl(CacheSerializer<String> keySerializer, CacheSerializer<V> valueSerializer) {
    this.keySerializer = keySerializer;
    this.valueSerializer = valueSerializer;
  }

  @Override
  public String keyColumnType() {
    return "VARCHAR(4096)";
  }

  @SuppressWarnings("unchecked")
  @Override
  public Funnel<String> keyFunnel() {
    Funnel<?> s = Funnels.unencodedCharsFunnel();
    return (Funnel<String>) s;
  }

  @Override
  public CacheSerializer<String> keySerializer() {
    return keySerializer;
  }

  @Override
  public CacheSerializer<V> valueSerializer() {
    return valueSerializer;
  }
}
