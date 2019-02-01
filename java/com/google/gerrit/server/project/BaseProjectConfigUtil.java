package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.project.ProjectConfig.LABEL;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/** Utilities to help handle base project configs. */
public class BaseProjectConfigUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Subtracts configs existing in the "baseConfig" from the "mergedConfig" and returns the configs
   * which are suitable to be stored in the local config file.
   *
   * <p>This method follows the following strategies:
   *
   * <ul>
   *   <li>if a config exists in "baseConfig" but not in "mergedConfig", a {@code
   *       ConfigInvalidException} is thrown because there is no way to create a new local config to
   *       unset the missing config.
   *   <li>if a config doesn't exist in "baseConfig" but in "mergedConfig", it will be set in the
   *       new local config.
   *   <li>if a config exists in both "baseConfig" and "mergedConfig", it will not be set in the new
   *       local config if it already presents in "localConfig".
   * </ul>
   *
   * @param mergedConfig a {@code config} to subtract base project configs.
   * @param localConfig a {@code config} containing local project configs.
   * @param baseConfig a {@code Config} containing base project configs.
   * @return a delta {@code Config} with the subtraction result.
   */
  static Config subtractBaseConfig(Config mergedConfig, Config localConfig, Config baseConfig)
      throws ConfigInvalidException {
    checkNotNull(mergedConfig);
    checkNotNull(baseConfig);
    Config newLocalConfig = new Config();
    Set<String> mergedConfigSections = mergedConfig.getSections();
    Set<String> baseConfigSections = baseConfig.getSections();

    // When "baseConfig" is empty, "mergedConfig" is the new local config.
    if (baseConfigSections.isEmpty()) {
      return mergedConfig;
    }

    // All sections of "baseConfig" must present.
    checkAllBasePresent(
        mergedConfigSections, baseConfigSections, "config misses base-config's section [%s]");

    for (String section : mergedConfigSections) {
      if (baseConfigSections.contains(section)) {
        subtractSection(mergedConfig, localConfig, baseConfig, section, newLocalConfig);
        continue;
      }

      // a new section in the merged config.
      setAllValuesInASection(mergedConfig, section, newLocalConfig);
    }

    return newLocalConfig;
  }

  private static void setAllValuesInASection(Config sourceConfig, String section, Config result) {
    setAllValuesInASubsection(sourceConfig, section, null, result);

    for (String subsection : sourceConfig.getSubsections(section)) {
      setAllValuesInASubsection(sourceConfig, section, subsection, result);
    }
  }

  private static void subtractSection(
      Config mergedConfig, Config localConfig, Config baseConfig, String section, Config result)
      throws ConfigInvalidException {
    Set<String> mergedSubsections = mergedConfig.getSubsections(section);
    Set<String> baseSubsections = baseConfig.getSubsections(section);

    // All subsections of "baseConfig" must present.
    checkAllBasePresent(
        mergedSubsections,
        baseSubsections,
        String.format("config misses base-config's subsection [%s \"%s\"]\n ...", section, "%s"));

    // Subtracts configs which have "subsection = null".
    subtractSubsection(mergedConfig, localConfig, baseConfig, section, null, result);

    for (String subsection : mergedSubsections) {
      if (baseSubsections.contains(subsection)) {
        // Don't allow to extend or overlay a label definition in the local config file since this
        // can result in partial label definition which is hard to read and debug.
        if (section.equals(LABEL)) {
          if (!checkLabelDefinitionEquivalent(mergedConfig, baseConfig, subsection)) {
            throw new ConfigInvalidException(
                String.format("config overlays base-config's label \"%s\"", subsection));
          }
          continue;
        }

        subtractSubsection(mergedConfig, localConfig, baseConfig, section, subsection, result);
        continue;
      }

      // A new subsection which only exists in the merged config.
      setAllValuesInASubsection(mergedConfig, section, subsection, result);
    }
  }

  private static void setAllValuesInASubsection(
      Config sourceConfig, String section, @Nullable String subsection, Config result) {
    sourceConfig
        .getNames(section, subsection)
        .forEach(name -> setAllValuesInAName(sourceConfig, section, subsection, name, result));
  }

  private static void subtractSubsection(
      Config mergedConfig,
      Config localConfig,
      Config baseConfig,
      String section,
      @Nullable String subsection,
      Config result)
      throws ConfigInvalidException {
    Set<String> names = mergedConfig.getNames(section, subsection);
    Set<String> baseNames = baseConfig.getNames(section, subsection);

    // All names of "baseConfig" must present.
    checkAllBasePresent(
        names,
        baseNames,
        String.format(
            "config misses base-config's name [%s \"%s\"]\n\t%s = ...",
            section, Strings.nullToEmpty(subsection), "%s"));

    for (String name : names) {
      if (baseNames.contains(name)) {
        subtractConfigInAName(
            mergedConfig, localConfig, baseConfig, section, subsection, name, result);
        continue;
      }

      // This is a new config in the local config.
      setAllValuesInAName(mergedConfig, section, subsection, name, result);
    }
  }

  private static void setAllValuesInAName(
      Config sourceConfig,
      String section,
      @Nullable String subsection,
      String name,
      Config result) {
    List<String> value = Arrays.asList(sourceConfig.getStringList(section, subsection, name));
    result.setStringList(section, subsection, name, value);
  }

  private static void subtractConfigInAName(
      Config mergedConfig,
      Config localConfig,
      Config baseConfig,
      String section,
      @Nullable String subsection,
      String name,
      Config result)
      throws ConfigInvalidException {
    List<String> mergedValues =
        Arrays.asList(mergedConfig.getStringList(section, subsection, name));
    List<String> localValues = Arrays.asList(localConfig.getStringList(section, subsection, name));
    List<String> baseValues = Arrays.asList(baseConfig.getStringList(section, subsection, name));
    LinkedHashSet<String> localValuesSet = new LinkedHashSet<>(localValues);
    LinkedHashSet<String> baseValuesSet = new LinkedHashSet<>(baseValues);
    LinkedHashSet<String> mergedValuesSet = new LinkedHashSet<>(mergedValues);

    // Log warnings since Gerrit doesn't have usages for duplicate config values. Note that
    // "mergedConfig" may contain duplicates because "baseConfig" and "localConfig" can contain the
    // same config.
    if (localValues.size() != localValuesSet.size()) {
      logger.atWarning().log(
          String.format(
              "local config has duplicate values for [%s \"%s\"]\n\t%s = ...",
              section, Strings.nullToEmpty(subsection), name));
    }

    if (baseValues.size() != baseValuesSet.size()) {
      logger.atWarning().log(
          String.format(
              "base config has duplicate values for [%s \"%s\"]\n\t%s = ...",
              section, Strings.nullToEmpty(subsection), name));
    }

    // All names of "baseConfig" must present.
    checkAllBasePresent(
        mergedValuesSet,
        baseValuesSet,
        String.format(
            "config misses base-config's value [%s \"%s\"]\n\t%s = %s",
            section, Strings.nullToEmpty(subsection), name, "%s"));

    LinkedHashSet<String> valuesToSet = new LinkedHashSet<>();
    for (String value : mergedValues) {
      if (!baseValues.contains(value) || localValues.contains(value)) {
        // Values need to be set at once because they share the same config name. Keeping them in
        // a "LinkedHashSet" to preserve order and avoid duplicates.
        valuesToSet.add(value);
      }
    }

    if (!valuesToSet.isEmpty()) {
      result.setStringList(section, subsection, name, new ArrayList<>(valuesToSet));
    }
  }

  /**
   * Checks if all values in {@code baseValues} presented in {@code currentValues}.
   *
   * @param currentValues the current value set, which supposes to contain all values in {@code
   *     baseValues}.
   * @param baseValues the base value set.
   * @param errorMessageFormat format for the error message.
   * @throws ConfigInvalidException if there is any value only presenting in {@code baseValues}.
   */
  private static void checkAllBasePresent(
      Set<String> currentValues, Set<String> baseValues, String errorMessageFormat)
      throws ConfigInvalidException {
    for (String section : baseValues) {
      if (!currentValues.contains(section)) {
        throw new ConfigInvalidException(String.format(errorMessageFormat, section));
      }
    }
  }

  private static boolean checkLabelDefinitionEquivalent(
      Config mergedConfig, Config baseConfig, String labelName) throws ConfigInvalidException {
    Set<String> configNames = mergedConfig.getNames(LABEL, labelName);
    Set<String> baseNames = baseConfig.getNames(LABEL, labelName);

    SetView<String> diff = Sets.symmetricDifference(configNames, baseNames);
    if (!diff.isEmpty()) {
      return false;
    }

    for (String name : configNames) {
      List<String> mergedConfigValues =
          Arrays.asList(mergedConfig.getStringList(LABEL, labelName, name));
      List<String> baseConfigValues =
          Arrays.asList(mergedConfig.getStringList(LABEL, labelName, name));

      if (mergedConfigValues.size() != baseConfigValues.size()) {
        return false;
      }

      for (int i = 0; i < mergedConfigValues.size(); ++i) {
        if (!mergedConfigValues.get(i).equals(baseConfigValues.get(i))) {
          return false;
        }
      }
    }

    return true;
  }
}
