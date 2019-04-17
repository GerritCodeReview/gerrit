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

package com.google.gerrit.server.cache.serialize;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.auto.value.AutoValue;
import com.google.common.base.Converter;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class CacheSerializerTest extends GerritBaseTests {
  @AutoValue
  abstract static class MyAutoValue {
    static MyAutoValue create(int val) {
      return new AutoValue_CacheSerializerTest_MyAutoValue(val);
    }

    abstract int val();
  }

  private static final CacheSerializer<MyAutoValue> SERIALIZER =
      CacheSerializer.convert(
          IntegerCacheSerializer.INSTANCE, Converter.from(MyAutoValue::val, MyAutoValue::create));

  @Test
  public void serialize() throws Exception {
    MyAutoValue v = MyAutoValue.create(1234);
    byte[] serialized = SERIALIZER.serialize(v);
    assertThat(serialized).isEqualTo(new byte[] {-46, 9});
    assertThat(SERIALIZER.deserialize(serialized).val()).isEqualTo(1234);
  }

  @Test
  public void deserializeNullFails() throws Exception {
    try {
      SERIALIZER.deserialize(null);
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
