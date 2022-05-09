package com.google.gerrit.index;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import java.util.Collection;
import java.util.Map.Entry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Validates index upgrades; see {@link IndexUpgradeValidator} for details. */
@RunWith(Parameterized.class)
public class IndexUpgradeTest {
  /** This is the first version to which {@link IndexUpgradeValidator} is applied. */
  private static final ImmutableMap<Class<? extends SchemaDefinitions<?>>, Integer>
      ENFORCE_UPDATE_RESTRICTIONS_FROM_VERSION =
          ImmutableMap.of(
              AccountSchemaDefinitions.class, 12,
              ChangeSchemaDefinitions.class, 78,
              GroupSchemaDefinitions.class, 8,
              ProjectSchemaDefinitions.class, 4);

  @Parameter public SchemaDefinitions<?> schemaDefinitions;

  @Parameters
  public static Collection<SchemaDefinitions<?>> indexes() {
    return ImmutableList.of(
        AccountSchemaDefinitions.INSTANCE,
        ChangeSchemaDefinitions.INSTANCE,
        GroupSchemaDefinitions.INSTANCE,
        ProjectSchemaDefinitions.INSTANCE);
  }

  @Test
  public void upgradesValid() {
    Schema<?> previousSchema = null;
    for (Entry<Integer, ? extends Schema<?>> entry : schemaDefinitions.getSchemas().entrySet()) {
      Schema<?> schema = entry.getValue();
      if (previousSchema != null
          && schema.getVersion()
              >= ENFORCE_UPDATE_RESTRICTIONS_FROM_VERSION.get(schemaDefinitions.getClass())) {
        IndexUpgradeValidator.assertValid(previousSchema, schema);
      }
      previousSchema = schema;
    }
  }
}
