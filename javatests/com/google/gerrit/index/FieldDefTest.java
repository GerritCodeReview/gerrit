// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.EqualsTester;
import com.google.gerrit.index.FieldDef.Getter;
import com.google.gerrit.index.FieldDef.Setter;
import com.google.gerrit.server.query.change.ChangeData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link FieldDef}. ö obsolete?! */
@RunWith(JUnit4.class)
// We're testing equals(), thank you.
@SuppressWarnings({"TruthIncompatibleType", "TruthSelfEquals", "UnstableApiUsage"})
public class FieldDefTest {

  private static final Getter<ChangeData, Integer> DUMMY_GETTER = x -> x.getId().get();
  private static final Getter<ChangeData, Integer> DUMMY_GETTER_2 = x -> x.getId().get();
  private static final Setter<ChangeData, Integer> DUMMY_SETTER = (x, y) -> {};
  private static final Setter<ChangeData, Integer> DUMMY_SETTER_2 = (x, y) -> {};

  // private static final Getter<ChangeData, Long> DUMMY_GETTER_LONG = x -> (long) x.getId().get();

  @Test
  public void equality_smokeTest() {
    FieldDef<ChangeData, Integer> f1 = FieldDef.integer("foo").build(DUMMY_GETTER);
    FieldDef<ChangeData, Integer> f2 = FieldDef.integer("foo").build(DUMMY_GETTER);
    new EqualsTester().addEqualityGroup(f1, f2).addEqualityGroup("huh?").testEquals();
  }

  @Test
  public void equality_ignoreGetterAndSetter() {
    FieldDef<ChangeData, Integer> f1 = FieldDef.integer("foo").build(DUMMY_GETTER, DUMMY_SETTER);
    FieldDef<ChangeData, Integer> f2 =
        FieldDef.integer("foo").build(DUMMY_GETTER_2, DUMMY_SETTER_2);
    assertThat(f1).isEqualTo(f1);
    assertThat(f1).isEqualTo(f2);
  }

  @Test
  public void equality_differentTypes() {
    FieldDef<ChangeData, Integer> f1 = FieldDef.integer("foo").build(DUMMY_GETTER);
    FieldDef<ChangeData, String> f2 = FieldDef.exact("foo").build(x -> "ignored");
    assertThat(f1).isNotEqualTo(f2);
  }

  @Test
  public void equality_differentNames() {
    FieldDef<ChangeData, Integer> f1 = FieldDef.integer("foo").build(DUMMY_GETTER);
    FieldDef<ChangeData, Integer> f2 = FieldDef.integer("bar").build(DUMMY_GETTER);
    assertThat(f1).isNotEqualTo(f2);
  }
}
