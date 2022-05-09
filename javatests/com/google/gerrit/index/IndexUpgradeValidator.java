package com.google.gerrit.index;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * Validates index upgrades to enforce the following constraints: Upgrades may only add or remove
 * fields. They may not do both, and may not change field types.
 */
public class IndexUpgradeValidator {
  public static void assertValid(Schema<?> previousSchema, Schema<?> schema) {
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

  private IndexUpgradeValidator() {}
}
