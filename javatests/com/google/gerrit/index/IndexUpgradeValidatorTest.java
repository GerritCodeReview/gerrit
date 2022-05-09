package com.google.gerrit.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.index.FieldDef.Getter;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link IndexUpgradeValidator}. */
@RunWith(JUnit4.class)
public class IndexUpgradeValidatorTest {
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

  @SafeVarargs
  private static Schema<ChangeData> schema(int version, FieldDef<ChangeData, ?>... fields) {
    Schema<ChangeData> schema = SchemaUtil.schema(fields);
    schema.setVersion(version);
    return schema;
  }
}
