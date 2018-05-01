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

package com.google.gerrit.server.cache;

import static com.google.common.truth.Truth.assertThat;

import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.Key;
import org.junit.Test;

public class CacheSerializersTest {
  @Test
  public void intKeySerializer() throws Exception {
    class MyIntKey extends IntKey<Key<?>> {
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

    MyIntKey k = new MyIntKey(1234);
    CacheSerializer<MyIntKey> s = CacheSerializers.newIntKeySerializer(MyIntKey::new);
    byte[] serialized = s.serialize(k);
    assertThat(serialized).isEqualTo(new byte[] {-46, 9});
    assertThat(s.deserialize(serialized).get()).isEqualTo(1234);
  }
}
