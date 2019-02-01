package com.google.gerrit.server.project.testing;

import static com.google.common.truth.Truth.assertThat;

import java.util.Set;
import org.eclipse.jgit.lib.Config;

/** Utilities for {@code Config}. */
public class ConfigTestUtils {

  public static void assertTwoConfigsEquivalent(Config config1, Config config2) {
    Set<String> sections1 = config1.getSections();
    Set<String> sections2 = config2.getSections();
    assertThat(sections1).containsExactlyElementsIn(sections2);

    sections1.forEach(s -> assertSectionEquivalent(config1, config2, s));
  }

  public static void assertSectionEquivalent(Config config1, Config config2, String section) {
    assertSubsectionEquivalent(config1, config2, section, null);

    Set<String> subsections1 = config1.getSubsections(section);
    Set<String> subsections2 = config2.getSubsections(section);
    assertThat(subsections1)
        .named("section \"%s\"", section)
        .containsExactlyElementsIn(subsections2);

    subsections1.forEach(s -> assertSubsectionEquivalent(config1, config2, section, s));
  }

  private static void assertSubsectionEquivalent(
      Config config1, Config config2, String section, String subsection) {
    Set<String> subsectionNames1 = config1.getNames(section, subsection);
    Set<String> subsectionNames2 = config2.getNames(section, subsection);
    String name = String.format("subsection \"%s\" of section \"%s\"", subsection, section);
    assertThat(subsectionNames1).named(name).containsExactlyElementsIn(subsectionNames2);

    // The order of "name, value" pairs matters since it may change the return value when read a
    // single config value, e.g. config#getString returns the last value given a config name.
    subsectionNames1.forEach(
        n ->
            assertThat(config1.getStringList(section, subsection, n))
                .named(name)
                .asList()
                .containsExactlyElementsIn(config2.getStringList(section, subsection, n))
                .inOrder());
  }

  private ConfigTestUtils() {}
}
