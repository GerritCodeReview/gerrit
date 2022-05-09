// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.IndexVersions.ALL;
import static com.google.gerrit.testing.IndexVersions.CURRENT;
import static com.google.gerrit.testing.IndexVersions.PREVIOUS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.FieldDef.Getter;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.junit.Test;

/** Tests for {@link ChangeSchemaDefinitions}. */
public class IndexVersionsTest {
  /** This is the first version to which {@link #assertValidUpgrade(Schema, Schema)} is applied. */
  private static final int ENFORCE_UPDATE_RESTRICTIONS_FROM_VERSION = 78;

  private static final ChangeSchemaDefinitions SCHEMA_DEF = ChangeSchemaDefinitions.INSTANCE;

  @Test
  public void noValue() {
    List<Integer> expected = new ArrayList<>();
    if (SCHEMA_DEF.getPrevious() != null) {
      expected.add(SCHEMA_DEF.getPrevious().getVersion());
    }
    expected.add(SCHEMA_DEF.getLatest().getVersion());

    assertThat(get(null)).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void emptyValue() {
    List<Integer> expected = new ArrayList<>();
    if (SCHEMA_DEF.getPrevious() != null) {
      expected.add(SCHEMA_DEF.getPrevious().getVersion());
    }
    expected.add(SCHEMA_DEF.getLatest().getVersion());

    assertThat(get("")).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void all() {
    assertThat(get(ALL)).containsExactlyElementsIn(SCHEMA_DEF.getSchemas().keySet()).inOrder();
  }

  @Test
  public void current() {
    assertThat(get(CURRENT)).containsExactly(SCHEMA_DEF.getLatest().getVersion());
  }

  @Test
  public void previous() {
    assume().that(SCHEMA_DEF.getPrevious()).isNotNull();

    assertThat(get(PREVIOUS)).containsExactly(SCHEMA_DEF.getPrevious().getVersion());
  }

  @Test
  public void versionNumber() {
    assume().that(SCHEMA_DEF.getPrevious()).isNotNull();

    assertThat(get(Integer.toString(SCHEMA_DEF.getPrevious().getVersion())))
        .containsExactly(SCHEMA_DEF.getPrevious().getVersion());
  }

  @Test
  public void invalid() {
    assertIllegalArgument("foo", "Invalid value for test: foo");
  }

  @Test
  public void currentAndPrevious() {
    if (SCHEMA_DEF.getPrevious() == null) {
      assertIllegalArgument(CURRENT + "," + PREVIOUS, "previous version does not exist");
      return;
    }

    assertThat(get(CURRENT + "," + PREVIOUS))
        .containsExactly(SCHEMA_DEF.getLatest().getVersion(), SCHEMA_DEF.getPrevious().getVersion())
        .inOrder();
    assertThat(get(PREVIOUS + "," + CURRENT))
        .containsExactly(SCHEMA_DEF.getPrevious().getVersion(), SCHEMA_DEF.getLatest().getVersion())
        .inOrder();
  }

  @Test
  public void currentAndVersionNumber() {
    assume().that(SCHEMA_DEF.getPrevious()).isNotNull();

    assertThat(get(CURRENT + "," + SCHEMA_DEF.getPrevious().getVersion()))
        .containsExactly(SCHEMA_DEF.getLatest().getVersion(), SCHEMA_DEF.getPrevious().getVersion())
        .inOrder();
    assertThat(get(SCHEMA_DEF.getPrevious().getVersion() + "," + CURRENT))
        .containsExactly(SCHEMA_DEF.getPrevious().getVersion(), SCHEMA_DEF.getLatest().getVersion())
        .inOrder();
  }

  @Test
  public void currentAndAll() {
    assertIllegalArgument(CURRENT + "," + ALL, "Invalid value for test: " + ALL);
  }

  @Test
  public void currentAndInvalid() {
    assertIllegalArgument(CURRENT + ",foo", "Invalid value for test: foo");
  }

  @Test
  public void nonExistingVersion() {
    int nonExistingVersion = SCHEMA_DEF.getLatest().getVersion() + 1;
    assertIllegalArgument(
        Integer.toString(nonExistingVersion),
        "Index version "
            + nonExistingVersion
            + " that was specified by test not found. Possible versions are: "
            + SCHEMA_DEF.getSchemas().keySet());
  }

  @Test
  public void singleNonDeprecatedVersion() {
    Integer lastNonDeprecatedVersion = null;
    for (Entry<Integer, Schema<ChangeData>> entry : SCHEMA_DEF.getSchemas().entrySet()) {
      for (Field field : entry.getValue().getClass().getDeclaredFields()) {
        boolean deprecated = field.isAnnotationPresent(Deprecated.class);
        assertWithMessage(
                "Versions "
                    + lastNonDeprecatedVersion
                    + " and "
                    + entry.getKey()
                    + " are both active (non-deprecated)")
            .that(lastNonDeprecatedVersion != null && deprecated)
            .isFalse();
        if (deprecated) {
          lastNonDeprecatedVersion = entry.getKey();
        }
      }
    }
  }

  @Test
  public void upgradesValid() {
    Schema<ChangeData> previousSchema = null;
    for (Entry<Integer, Schema<ChangeData>> entry : SCHEMA_DEF.getSchemas().entrySet()) {
      Schema<ChangeData> schema = entry.getValue();
      if (previousSchema != null
          && schema.getVersion() >= ENFORCE_UPDATE_RESTRICTIONS_FROM_VERSION) {
        assertValidUpgrade(previousSchema, schema);
      }
      previousSchema = schema;
    }
  }

  @Test
  public void assertValidUpgrade_valid() {
    assertValidUpgrade(schema(1, ChangeField.ID), schema(2, ChangeField.ID));
    assertValidUpgrade(schema(1, ChangeField.ID), schema(2, ChangeField.ID, ChangeField.OWNER));
    assertValidUpgrade(
        schema(1, ChangeField.ID),
        schema(2, ChangeField.ID, ChangeField.OWNER, ChangeField.COMMITTER));
  }

  @Test
  public void assertValidUpgrade_addAndRemove() {
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () -> assertValidUpgrade(schema(1, ChangeField.ID), schema(2, ChangeField.OWNER)));
    assertThat(e)
        .hasMessageThat()
        .contains("Schema upgrade to version 2 may either add or remove fields, but not both");
  }

  @Test
  public void assertValidUpgrade_modify() {
    // Change value type from String to Integer.
    FieldDef<ChangeData, Integer> ID_MODIFIED =
        new FieldDef.Builder<>(FieldType.INTEGER, ChangeQueryBuilder.FIELD_CHANGE_ID)
            .build(cd -> 42);
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () -> assertValidUpgrade(schema(1, ChangeField.ID), schema(2, ID_MODIFIED)));
    assertThat(e).hasMessageThat().contains("Fields may not be modified");
    assertThat(e).hasMessageThat().contains(ChangeQueryBuilder.FIELD_CHANGE_ID);
  }

  @Test
  public void assertValidUpgrade_modify_referenceEquality() {
    // Comparison uses Object.equals(), i.e. reference equality.
    Getter<ChangeData, String> getter = cd -> cd.change().getKey().get();
    FieldDef<ChangeData, String> ID_1 =
        new FieldDef.Builder<>(FieldType.PREFIX, ChangeQueryBuilder.FIELD_CHANGE_ID).build(getter);
    FieldDef<ChangeData, String> ID_2 =
        new FieldDef.Builder<>(FieldType.PREFIX, ChangeQueryBuilder.FIELD_CHANGE_ID).build(getter);
    AssertionError e =
        assertThrows(
            AssertionError.class, () -> assertValidUpgrade(schema(1, ID_1), schema(2, ID_2)));
    assertThat(e).hasMessageThat().contains("Fields may not be modified");
    assertThat(e).hasMessageThat().contains(ChangeQueryBuilder.FIELD_CHANGE_ID);
  }

  @SafeVarargs
  private static Schema<ChangeData> schema(int version, FieldDef<ChangeData, ?>... fields) {
    Schema<ChangeData> schema = SchemaUtil.schema(fields);
    schema.setVersion(version);
    return schema;
  }

  /**
   * Upgrades may only add or remove fields. They may not do both, and may not change field types.
   */
  private static void assertValidUpgrade(
      Schema<ChangeData> previousSchema, Schema<ChangeData> schema) {
    SetView<String> addedFields =
        Sets.difference(schema.getFields().keySet(), previousSchema.getFields().keySet());
    SetView<String> removedFields =
        Sets.difference(previousSchema.getFields().keySet(), schema.getFields().keySet());
    SetView<String> keptFields =
        Sets.intersection(previousSchema.getFields().keySet(), schema.getFields().keySet());
    assertWithMessage(
            "Schema upgrade to version "
                + schema.getVersion()
                + " may either add or remove fields, but not both")
        .that(addedFields.isEmpty() || removedFields.isEmpty())
        .isTrue();
    ImmutableList<String> modifiedFields =
        keptFields.stream()
            .filter(
                fieldName ->
                    previousSchema.getFields().get(fieldName) != schema.getFields().get(fieldName))
            .collect(toImmutableList());
    assertWithMessage("Fields may not be modified (create a new field instead)")
        .that(modifiedFields)
        .isEmpty();
  }

  private static List<Integer> get(String value) {
    return IndexVersions.get(ChangeSchemaDefinitions.INSTANCE, "test", value);
  }

  private void assertIllegalArgument(String value, String expectedMessage) {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> get(value));
    assertThat(thrown).hasMessageThat().contains(expectedMessage);
  }
}
