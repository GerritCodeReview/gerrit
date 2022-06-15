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
import static com.google.gerrit.index.SchemaUtil.schema;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.index.SchemaFieldDefs.Getter;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link IndexUpgradeValidator}. */
@RunWith(JUnit4.class)
public class IndexUpgradeValidatorTest {

  // TODO(mariasavtchouk): adopt this test to verity IndexedFields follow the same constraints as
  // SchemaFields.
  @Test
  public void valid() {
    IndexUpgradeValidator.assertValid(schema(1, ChangeField.ID), schema(2, ChangeField.ID));
    IndexUpgradeValidator.assertValid(
        schema(1, ChangeField.ID), schema(2, ChangeField.ID, ChangeField.OWNER));
    IndexUpgradeValidator.assertValid(
        schema(1, ChangeField.ID),
        schema(2, ChangeField.ID, ChangeField.OWNER, ChangeField.COMMITTER));
  }

  @Test
  public void invalid_addAndRemove() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                IndexUpgradeValidator.assertValid(
                    schema(1, ChangeField.ID), schema(2, ChangeField.OWNER)));
    assertThat(e)
        .hasMessageThat()
        .contains("Schema upgrade to version 2 may either add or remove fields, but not both");
  }

  @Test
  public void invalid_modify() {
    // Change value type from String to Integer.
    FieldDef<ChangeData, Integer> ID_MODIFIED =
        new FieldDef.Builder<>(FieldType.INTEGER, ChangeQueryBuilder.FIELD_CHANGE_ID)
            .build(cd -> 42);
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                IndexUpgradeValidator.assertValid(
                    schema(1, ChangeField.ID), schema(2, ID_MODIFIED)));
    assertThat(e).hasMessageThat().contains("Fields may not be modified");
    assertThat(e).hasMessageThat().contains(ChangeQueryBuilder.FIELD_CHANGE_ID);
  }

  @Test
  public void invalid_modify_referenceEquality() {
    // Comparison uses Object.equals(), i.e. reference equality.
    Getter<ChangeData, String> getter = cd -> cd.change().getKey().get();
    FieldDef<ChangeData, String> ID_1 =
        new FieldDef.Builder<>(FieldType.PREFIX, ChangeQueryBuilder.FIELD_CHANGE_ID).build(getter);
    FieldDef<ChangeData, String> ID_2 =
        new FieldDef.Builder<>(FieldType.PREFIX, ChangeQueryBuilder.FIELD_CHANGE_ID).build(getter);
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () -> IndexUpgradeValidator.assertValid(schema(1, ID_1), schema(2, ID_2)));
    assertThat(e).hasMessageThat().contains("Fields may not be modified");
    assertThat(e).hasMessageThat().contains(ChangeQueryBuilder.FIELD_CHANGE_ID);
  }
}
