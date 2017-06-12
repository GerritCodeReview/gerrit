// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.index.SchemaUtil.getPersonParts;
import static com.google.gerrit.server.index.SchemaUtil.schema;

import com.google.gerrit.testutil.GerritBaseTests;
import java.util.Map;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class SchemaUtilTest extends GerritBaseTests {
  static class TestSchemas {
    static final Schema<String> V1 = schema();
    static final Schema<String> V2 = schema();
    static Schema<String> V3 = schema(); // Not final, ignored.
    private static final Schema<String> V4 = schema();

    // Ignored.
    static Schema<String> V10 = schema();
    final Schema<String> V11 = schema();
  }

  @Test
  public void schemasFromClassBuildsMap() {
    Map<Integer, Schema<String>> all = SchemaUtil.schemasFromClass(TestSchemas.class, String.class);
    assertThat(all.keySet()).containsExactly(1, 2, 4);
    assertThat(all.get(1)).isEqualTo(TestSchemas.V1);
    assertThat(all.get(2)).isEqualTo(TestSchemas.V2);
    assertThat(all.get(4)).isEqualTo(TestSchemas.V4);

    exception.expect(IllegalArgumentException.class);
    SchemaUtil.schemasFromClass(TestSchemas.class, Object.class);
  }

  @Test
  public void getPersonPartsExtractsParts() {
    // PersonIdent allows empty email, which should be extracted as the empty
    // string. However, it converts empty names to null internally.
    assertThat(getPersonParts(new PersonIdent("", ""))).containsExactly("");
    assertThat(getPersonParts(new PersonIdent("foo bar", ""))).containsExactly("foo", "bar", "");

    assertThat(getPersonParts(new PersonIdent("", "foo@example.com")))
        .containsExactly("foo@example.com", "foo", "example.com", "example", "com");
    assertThat(getPersonParts(new PersonIdent("foO J. bAr", "bA-z@exAmple.cOm")))
        .containsExactly(
            "foo",
            "j",
            "bar",
            "ba-z@example.com",
            "ba-z",
            "ba",
            "z",
            "example.com",
            "example",
            "com");
  }
}
