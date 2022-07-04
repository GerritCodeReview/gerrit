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

package com.google.gerrit.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.index.FieldDef.exact;
import static com.google.gerrit.index.SchemaUtil.getNameParts;
import static com.google.gerrit.index.SchemaUtil.getPersonParts;
import static com.google.gerrit.index.SchemaUtil.schema;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.util.Map;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class SchemaUtilTest {

  private static final FieldDef<String, String> TEST_DEF =
      exact("test_id").stored().build(id -> id);

  private static final FieldDef<String, String> OTHER_TEST_DEF =
      exact("other_test_id").stored().build(id -> id);

  private static final IndexedField<String, String> TEST_FIELD =
      IndexedField.<String>stringBuilder("TestId").build(a -> a);

  private static final IndexedField<String, String> TEST_FIELD_DUPLICATE_NAME =
      IndexedField.<String>stringBuilder(TEST_DEF.getName()).build(a -> a);

  private static final IndexedField.SearchSpec TEST_FIELD_SPEC =
      TEST_FIELD.exact(TEST_DEF.getName());

  static class TestSchemas {

    static final Schema<String> V1 = schema(/* version= */ 1);
    static final Schema<String> V2 = schema(/* version= */ 2);
    static Schema<String> V3 = schema(V2); // Not final, ignored.
    private static final Schema<String> V4 = schema(V3);

    // Ignored.
    static Schema<String> V10 = schema(/* version= */ 10);
    final Schema<String> V11 = schema(V10);
  }

  @Test
  public void schemasFromClassBuildsMap() {
    Map<Integer, Schema<String>> all = SchemaUtil.schemasFromClass(TestSchemas.class, String.class);
    assertThat(all.keySet()).containsExactly(1, 2, 4);
    assertThat(all.get(1)).isEqualTo(TestSchemas.V1);
    assertThat(all.get(2)).isEqualTo(TestSchemas.V2);
    assertThat(all.get(4)).isEqualTo(TestSchemas.V4);
    assertThrows(
        IllegalArgumentException.class,
        () -> SchemaUtil.schemasFromClass(TestSchemas.class, Object.class));
  }

  @Test
  public void schemaVersion_incrementedOnVersionUpgrades() {
    Schema<String> initialSchemaVersion = schema(/* version= */ 1);
    Schema<String> schemaVersionUpgrade = schema(initialSchemaVersion);
    assertThat(initialSchemaVersion.getVersion()).isEqualTo(1);
    assertThat(schemaVersionUpgrade.getVersion()).isEqualTo(2);
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

  @Test
  public void getNamePartsExtractsParts() {
    assertThat(getNameParts("")).isEmpty();
    assertThat(getNameParts("foO-bAr_Baz a.b@c/d"))
        .containsExactly("foo", "bar", "baz", "a", "b", "c", "d");
  }

  @Test
  public void canAddFieldSpecAndFieldDef() {
    Schema<String> schema0 =
        new Schema.Builder<String>()
            .version(0)
            .addIndexedFields(TEST_FIELD)
            .addSearchSpecs(TEST_FIELD_SPEC)
            .add(OTHER_TEST_DEF)
            .build();

    assertThat(schema0.hasField(TEST_FIELD_SPEC)).isTrue();
    assertThat(schema0.hasField(OTHER_TEST_DEF)).isTrue();
    assertThat(schema0.getIndexFields().values()).contains(TEST_FIELD);
  }

  @Test
  public void canRemoveIndexedField() {
    Schema<String> schema0 =
        new Schema.Builder<String>()
            .version(0)
            .addIndexedFields(TEST_FIELD)
            .addSearchSpecs(TEST_FIELD_SPEC)
            .build();

    Schema<String> schema1 =
        new Schema.Builder<String>()
            .add(schema0)
            .remove(TEST_FIELD_SPEC)
            .remove(TEST_FIELD)
            .build();
    assertThat(schema1.hasField(TEST_FIELD_SPEC)).isFalse();
    assertThat(schema1.getIndexFields().values()).doesNotContain(TEST_FIELD);
  }

  @Test
  public void canRemoveSearchSpec() {
    Schema<String> schema0 =
        new Schema.Builder<String>()
            .version(0)
            .addIndexedFields(TEST_FIELD)
            .addSearchSpecs(TEST_FIELD_SPEC)
            .build();

    Schema<String> schema1 =
        new Schema.Builder<String>().add(schema0).remove(TEST_FIELD_SPEC).build();
    assertThat(schema1.hasField(TEST_FIELD_SPEC)).isFalse();
    assertThat(schema1.getIndexFields().values()).contains(TEST_FIELD);
  }

  @Test
  public void canRemoveFieldDef() {
    Schema<String> schema0 =
        new Schema.Builder<String>()
            .version(0)
            .addIndexedFields(TEST_FIELD)
            .addSearchSpecs(TEST_FIELD_SPEC)
            .add(OTHER_TEST_DEF)
            .build();

    Schema<String> schema1 =
        new Schema.Builder<String>().add(schema0).remove(OTHER_TEST_DEF).build();
    assertThat(schema1.hasField(TEST_FIELD_SPEC)).isTrue();
    assertThat(schema1.hasField(OTHER_TEST_DEF)).isFalse();
    assertThat(schema1.getIndexFields().values()).contains(TEST_FIELD);
  }

  @Test
  public void addSearchWithoutStoredField_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Schema.Builder<String>().version(0).addSearchSpecs(TEST_FIELD_SPEC).build());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("test_id spec can only be added to the schema that contains TestId field");
  }

  @Test
  public void addDuplicateIndexField_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Schema.Builder<String>()
                    .version(0)
                    .addIndexedFields(TEST_FIELD)
                    .addIndexedFields(TEST_FIELD)
                    .build());
    assertThat(thrown).hasMessageThat().contains("Multiple entries with same key: TestId");
  }

  @Test
  public void addDuplicateSearchSpec_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Schema.Builder<String>()
                    .version(0)
                    .addIndexedFields(TEST_FIELD)
                    .addSearchSpecs(TEST_FIELD_SPEC)
                    .addSearchSpecs(TEST_FIELD_SPEC)
                    .build());
    assertThat(thrown).hasMessageThat().contains("Multiple entries with same key: test_id");
  }

  @Test
  public void addFieldDefWithDuplicateSearchName_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Schema.Builder<String>()
                    .version(0)
                    .addIndexedFields(TEST_FIELD)
                    .addSearchSpecs(TEST_FIELD_SPEC)
                    .add(TEST_DEF)
                    .build());
    assertThat(thrown).hasMessageThat().contains("Multiple entries with same key: test_id");
  }

  @Test
  public void addFieldDefWithDuplicateFieldName_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Schema.Builder<String>()
                    .version(0)
                    .addIndexedFields(TEST_FIELD_DUPLICATE_NAME)
                    .add(TEST_DEF)
                    .build());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("DuplicateKeys found [test_id], indexFields:[test_id], schemaFields: [test_id]");
  }

  @Test
  public void removeFieldWithExistingSearchSpec_disallowed() {
    Schema<String> schema0 =
        new Schema.Builder<String>()
            .version(0)
            .addIndexedFields(TEST_FIELD)
            .addSearchSpecs(TEST_FIELD_SPEC)
            .build();

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Schema.Builder<String>().add(schema0).remove(TEST_FIELD).build());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "Field TestId can be only removed from schema after all of its searches are removed.");

    Schema<String> schema1 =
        new Schema.Builder<String>()
            .add(schema0)
            .remove(TEST_FIELD_SPEC)
            .remove(TEST_FIELD)
            .build();
    assertThat(schema1.hasField(TEST_FIELD_SPEC)).isFalse();
    assertThat(schema1.getIndexFields().values()).doesNotContain(TEST_FIELD);
  }
}
