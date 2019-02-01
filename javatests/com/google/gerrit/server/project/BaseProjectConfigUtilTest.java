package com.google.gerrit.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.BaseProjectConfigUtil.subtractBaseConfig;
import static com.google.gerrit.server.project.testing.ConfigTestUtils.assertTwoConfigsEquivalent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/** Tests to verify basic behaviors of {@code BaseProjectConfigUtil}. */
public class BaseProjectConfigUtilTest extends GerritBaseTests {
  private static final ImmutableList<String> TEST_RECEIVE_SECTION =
      ImmutableList.of(
          "[receive]",
          "  requireContributorAgreement = false",
          "  requireSignedOffBy = false",
          "  requireChangeId = true",
          "  enableSignedPush = false");

  private static final ImmutableList<String> TEST_ACCESS_SECTION =
      ImmutableList.of(
          "[access \"refs/*\"]",
          "  create = group Administrators",
          "  create = group Project Owners",
          "  forgeAuthor = group Registered Users");

  private static final ImmutableList<String> TEST_LABEL_SECTION =
      ImmutableList.of(
          "[label \"Code-Review\"]",
          "  function = MaxWithBlock",
          "  value = -2 This shall not be merged",
          "  value = -1 I would prefer this is not merged as is",
          "  value = 0 No score",
          "  value = +1 Looks good to me, but someone else must approve",
          "  value = +2 Looks good to me, approved");

  @Test
  public void emptyBaseConfig() throws Exception {
    Config base = new Config();
    Config local = new Config();
    Config merged = new Config();
    merged.fromText(convertToConfigText(TEST_ACCESS_SECTION, TEST_LABEL_SECTION));

    Config delta = subtractBaseConfig(merged, local, base);

    assertThat(delta.toText()).isEqualTo(merged.toText());
  }

  // Tests to verify all base configs must present in the current merged config.

  @Test
  public void missingBaseConfigSection() throws Exception {
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_ACCESS_SECTION));
    Config local = new Config();
    Config merged = new Config();

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("config misses base-config's section [access]");
    subtractBaseConfig(merged, local, base);
  }

  @Test
  public void missingBaseConfigSubsection() throws Exception {
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_ACCESS_SECTION));
    Config local = new Config();
    Config merged = new Config();
    ImmutableList<String> section =
        ImmutableList.of("[access \"refs/heads/*\"]", "  create = group Administrators");
    merged.fromText(convertToConfigText(section));

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("config misses base-config's subsection [access \"refs/*\"]");
    subtractBaseConfig(merged, local, base);
  }

  @Test
  public void missingBaseConfigName() throws Exception {
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_ACCESS_SECTION));
    Config local = new Config();
    Config merged = new Config();
    ImmutableList<String> section =
        ImmutableList.of(
            "[access \"refs/*\"]",
            "  create = group Administrators",
            "  create = group Project Owners");
    merged.fromText(convertToConfigText(section));

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage(
        "config misses base-config's name [access \"refs/*\"]\n\tforgeAuthor = ...");
    subtractBaseConfig(merged, local, base);
  }

  @Test
  public void missingBaseConfigValue() throws Exception {
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_ACCESS_SECTION));
    Config local = new Config();
    Config merged = new Config();
    ImmutableList<String> section =
        ImmutableList.of(
            "[access \"refs/*\"]",
            "  create = group Administrators",
            "  forgeAuthor = group Registered Users");
    merged.fromText(convertToConfigText(section));

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage(
        "config misses base-config's value [access \"refs/*\"]\n\tcreate = group Project Owners");
    subtractBaseConfig(merged, local, base);
  }

  // Tests to verify behaviors for config calculations.

  @Test
  public void newConfigsIncluded() throws Exception {
    // A config in the merged config will be included in the new local config if it doesn't present
    // in the base config.
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION));
    Config local = new Config();
    Config merged = new Config();
    ImmutableList<String> newConfigs =
        ImmutableList.of(
            "[new-section-1]", // new section with null subsection.
            "  foo = bar1", // multiple values.
            "  foo = bar2",
            "  foo1 = bar1",
            "[new-section-1 \"subsection\"]", // new section with non-null subsection.
            "  foo = bar1", // multiple values.
            "  foo = bar2",
            "  foo1 = bar1",
            "[receive]", // new config in null subsection of an existing section.
            "  foo = bar1", // new name with multiple values.
            "  foo = bar2",
            "  foo1 = bar1",
            "[access \"refs/*\"]", // new config in non-null subsection of an existing section.
            "  foo = bar1", // new name with multiple values.
            "  foo = bar2",
            "  foo1 = bar1");
    merged.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION, newConfigs));

    Config newLocalConfig = subtractBaseConfig(merged, local, base);

    Config expectedLocalConfig = new Config();
    expectedLocalConfig.fromText(convertToConfigText(newConfigs));
    assertTwoConfigsEquivalent(newLocalConfig, expectedLocalConfig);
  }

  @Test
  public void differentValuesIncluded() throws Exception {
    // A config has different value with the base config will be included.
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION));
    Config local = new Config();
    Config merged = new Config();
    ImmutableList<String> newConfigs =
        ImmutableList.of(
            "[receive]", // new config in null subsection of an existing section.
            "  requireContributorAgreement = true",
            "  requireSignedOffBy = true",
            "  requireChangeId = false",
            "[access \"refs/*\"]", // new config in non-null subsection of an existing section.
            "  create = group Group-1", // new value for an existing name.
            "  create = group Group-2");
    merged.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION, newConfigs));

    Config newLocalConfig = subtractBaseConfig(merged, local, base);

    Config expectedLocalConfig = new Config();
    expectedLocalConfig.fromText(convertToConfigText(newConfigs));
    assertTwoConfigsEquivalent(newLocalConfig, expectedLocalConfig);
  }

  @Test
  public void sameValueNotIncludedIfNotInLocal() throws Exception {
    // A config has the same value with the base config will not be included.
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION));
    Config local = new Config();
    ImmutableList<String> newConfigs =
        ImmutableList.of(
            "[receive]",
            "  requireContributorAgreement = false",
            "  requireSignedOffBy = false",
            "  requireChangeId = true",
            "[access \"refs/*\"]",
            "  create = group Administrators",
            "  create = group Project Owners");
    Config merged = new Config();
    merged.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION, newConfigs));

    Config newLocalConfig = subtractBaseConfig(merged, local, base);

    assertThat(newLocalConfig.toText()).isEmpty();
  }

  @Test
  public void sameValueIncludedIfInLocal() throws Exception {
    // A config has the same value with the base config will not be included.
    Config base = new Config();
    base.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION));
    ImmutableList<String> newConfigs =
        ImmutableList.of(
            "[receive]",
            "  requireContributorAgreement = false",
            "  requireChangeId = true",
            "[access \"refs/*\"]",
            "  create = group Administrators",
            "  create = group Project Owners");
    Config local = new Config();
    local.fromText(convertToConfigText(newConfigs));
    Config merged = new Config();
    merged.fromText(convertToConfigText(TEST_RECEIVE_SECTION, TEST_ACCESS_SECTION, newConfigs));

    Config newLocalConfig = subtractBaseConfig(merged, local, base);

    assertTwoConfigsEquivalent(newLocalConfig, local);
  }

  // Tests to verify behaviors for restricted configs, e.g. label definitions.

  @Test
  public void extendLabelProhibited() throws Exception {
    Config merged = new Config();
    merged.fromText(convertToConfigText(TEST_LABEL_SECTION));
    Config base = new Config();
    Config local = new Config();
    ImmutableList<String> extendedLabelConfigs =
        ImmutableList.of("[label \"Code-Review\"]", "  copyMinScore = true");
    base.fromText(convertToConfigText(extendedLabelConfigs));

    exception.expect(ConfigInvalidException.class);
    exception.expectMessage("config overlays base-config's label \"Code-Review\"");
    subtractBaseConfig(merged, local, base);
  }

  @SafeVarargs
  private static String convertToConfigText(ImmutableList<String>... configs) {
    return Streams.stream(Iterables.concat(configs)).collect(Collectors.joining("\n"));
  }
}
