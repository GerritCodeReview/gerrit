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

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Project;
import java.io.Serializable;
import org.junit.Test;

public class JavaCacheSerializerTest {
  @Test
  public void builtInTypes() throws Exception {
    assertRoundTrip("foo");
    assertRoundTrip(Integer.valueOf(1234));
    assertRoundTrip(Boolean.TRUE);
  }

  @Test
  public void customType() throws Exception {
    assertRoundTrip(new AutoValue_JavaCacheSerializerTest_MyType(123, "four five six"));
  }

  @Test
  public void gerritEntities() throws Exception {
    assertRoundTrip(Project.nameKey("foo"));
  }

  @AutoValue
  abstract static class MyType implements Serializable {
    private static final long serialVersionUID = 1L;

    abstract Integer anInt();

    abstract String aString();
  }

  private static <T extends Serializable> void assertRoundTrip(T input) throws Exception {
    JavaCacheSerializer<T> s = new JavaCacheSerializer<>();
    assertThat(s.deserialize(s.serialize(input))).isEqualTo(input);
  }
}
