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

import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.Key;
import org.junit.Test;

public class IntKeyCacheSerializerTest {

  private static class MyIntKey extends IntKey<Key<?>> {
    private static final long serialVersionUID = 1L;

    private int val;

    MyIntKey(int val) {
      this.val = val;
    }

    @Override
    public int get() {
      return val;
    }

    @Override
    protected void set(int newValue) {
      this.val = newValue;
    }
  }

  private static final IntKeyCacheSerializer<MyIntKey> SERIALIZER =
      new IntKeyCacheSerializer<>(MyIntKey::new);

  @Test
  public void serialize() throws Exception {
    MyIntKey k = new MyIntKey(1234);
    byte[] serialized = SERIALIZER.serialize(k);
    assertThat(serialized).isEqualTo(new byte[] {-46, 9});
    assertThat(SERIALIZER.deserialize(serialized).get()).isEqualTo(1234);
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
