// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.skipField;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE_COLUMN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.server.git.UserConfigSections;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/** Helper to read default or user preferences from Git-style config files. */
public class PreferencesParserUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private PreferencesParserUtil() {}

  /**
   * Returns a {@link GeneralPreferencesInfo} that is the result of parsing {@code defaultCfg} for
   * the server's default configs and {@code cfg} for the user's config. These configs are then
   * overlaid to inherit values (default -> user -> input (if provided).
   */
  public static GeneralPreferencesInfo parseGeneralPreferences(
      Config cfg, @Nullable Config defaultCfg, @Nullable GeneralPreferencesInfo input)
      throws ConfigInvalidException {
    GeneralPreferencesInfo r =
        loadSection(
            cfg,
            UserConfigSections.GENERAL,
            null,
            new GeneralPreferencesInfo(),
            defaultCfg != null
                ? parseDefaultGeneralPreferences(defaultCfg, input)
                : GeneralPreferencesInfo.defaults(),
            input);
    if (input != null) {
      r.changeTable = input.changeTable;
      r.my = input.my;
    } else {
      r.changeTable = parseChangeTableColumns(cfg, defaultCfg);
      r.my = parseMyMenus(cfg, defaultCfg);
    }
    return r;
  }

  /**
   * Returns a {@link GeneralPreferencesInfo} that is the result of parsing {@code defaultCfg} for
   * the server's default configs. These configs are then overlaid to inherit values (default ->
   * input (if provided).
   */
  public static GeneralPreferencesInfo parseDefaultGeneralPreferences(
      Config defaultCfg, GeneralPreferencesInfo input) throws ConfigInvalidException {
    GeneralPreferencesInfo allUserPrefs = new GeneralPreferencesInfo();
    loadSection(
        defaultCfg,
        UserConfigSections.GENERAL,
        null,
        allUserPrefs,
        GeneralPreferencesInfo.defaults(),
        input);
    return updateGeneralPreferencesDefaults(allUserPrefs);
  }

  /**
   * Returns a {@link DiffPreferencesInfo} that is the result of parsing {@code defaultCfg} for the
   * server's default configs and {@code cfg} for the user's config. These configs are then overlaid
   * to inherit values (default -> user -> input (if provided).
   */
  public static DiffPreferencesInfo parseDiffPreferences(
      Config cfg, @Nullable Config defaultCfg, @Nullable DiffPreferencesInfo input)
      throws ConfigInvalidException {
    return loadSection(
        cfg,
        UserConfigSections.DIFF,
        null,
        new DiffPreferencesInfo(),
        defaultCfg != null
            ? parseDefaultDiffPreferences(defaultCfg, input)
            : DiffPreferencesInfo.defaults(),
        input);
  }

  /**
   * Returns a {@link DiffPreferencesInfo} that is the result of parsing {@code defaultCfg} for the
   * server's default configs. These configs are then overlaid to inherit values (default -> input
   * (if provided).
   */
  public static DiffPreferencesInfo parseDefaultDiffPreferences(
      Config defaultCfg, DiffPreferencesInfo input) throws ConfigInvalidException {
    DiffPreferencesInfo allUserPrefs = new DiffPreferencesInfo();
    loadSection(
        defaultCfg,
        UserConfigSections.DIFF,
        null,
        allUserPrefs,
        DiffPreferencesInfo.defaults(),
        input);
    return updateDiffPreferencesDefaults(allUserPrefs);
  }

  /**
   * Returns a {@link EditPreferencesInfo} that is the result of parsing {@code defaultCfg} for the
   * server's default configs and {@code cfg} for the user's config. These configs are then overlaid
   * to inherit values (default -> user -> input (if provided).
   */
  public static EditPreferencesInfo parseEditPreferences(
      Config cfg, @Nullable Config defaultCfg, @Nullable EditPreferencesInfo input)
      throws ConfigInvalidException {
    return loadSection(
        cfg,
        UserConfigSections.EDIT,
        null,
        new EditPreferencesInfo(),
        defaultCfg != null
            ? parseDefaultEditPreferences(defaultCfg, input)
            : EditPreferencesInfo.defaults(),
        input);
  }

  /**
   * Returns a {@link EditPreferencesInfo} that is the result of parsing {@code defaultCfg} for the
   * server's default configs. These configs are then overlaid to inherit values (default -> input
   * (if provided).
   */
  public static EditPreferencesInfo parseDefaultEditPreferences(
      Config defaultCfg, EditPreferencesInfo input) throws ConfigInvalidException {
    EditPreferencesInfo allUserPrefs = new EditPreferencesInfo();
    loadSection(
        defaultCfg,
        UserConfigSections.EDIT,
        null,
        allUserPrefs,
        EditPreferencesInfo.defaults(),
        input);
    return updateEditPreferencesDefaults(allUserPrefs);
  }

  private static List<String> parseChangeTableColumns(Config cfg, @Nullable Config defaultCfg) {
    List<String> changeTable = changeTable(cfg);
    if (changeTable == null && defaultCfg != null) {
      changeTable = changeTable(defaultCfg);
    }
    return changeTable;
  }

  private static List<MenuItem> parseMyMenus(Config cfg, @Nullable Config defaultCfg) {
    List<MenuItem> my = my(cfg);
    if (my.isEmpty() && defaultCfg != null) {
      my = my(defaultCfg);
    }
    if (my.isEmpty()) {
      my.add(new MenuItem("Dashboard", "#/dashboard/self", null));
      my.add(new MenuItem("Draft Comments", "#/q/has:draft", null));
      my.add(new MenuItem("Edits", "#/q/has:edit", null));
      my.add(new MenuItem("Watched Changes", "#/q/is:watched+is:open", null));
      my.add(new MenuItem("Starred Changes", "#/q/is:starred", null));
      my.add(new MenuItem("All Visible Changes", "#/q/is:visible", null));
      my.add(new MenuItem("Groups", "#/settings/#Groups", null));
    }
    return my;
  }

  private static GeneralPreferencesInfo updateGeneralPreferencesDefaults(
      GeneralPreferencesInfo input) {
    GeneralPreferencesInfo result = GeneralPreferencesInfo.defaults();
    try {
      for (Field field : input.getClass().getDeclaredFields()) {
        if (skipField(field)) {
          continue;
        }
        Object newVal = field.get(input);
        if (newVal != null) {
          field.set(result, newVal);
        }
      }
    } catch (IllegalAccessException e) {
      logger.atSevere().withCause(e).log("Failed to apply default general preferences");
      return GeneralPreferencesInfo.defaults();
    }
    return result;
  }

  private static DiffPreferencesInfo updateDiffPreferencesDefaults(DiffPreferencesInfo input) {
    DiffPreferencesInfo result = DiffPreferencesInfo.defaults();
    try {
      for (Field field : input.getClass().getDeclaredFields()) {
        if (skipField(field)) {
          continue;
        }
        Object newVal = field.get(input);
        if (newVal != null) {
          field.set(result, newVal);
        }
      }
    } catch (IllegalAccessException e) {
      logger.atSevere().withCause(e).log("Failed to apply default diff preferences");
      return DiffPreferencesInfo.defaults();
    }
    return result;
  }

  private static EditPreferencesInfo updateEditPreferencesDefaults(EditPreferencesInfo input) {
    EditPreferencesInfo result = EditPreferencesInfo.defaults();
    try {
      for (Field field : input.getClass().getDeclaredFields()) {
        if (skipField(field)) {
          continue;
        }
        Object newVal = field.get(input);
        if (newVal != null) {
          field.set(result, newVal);
        }
      }
    } catch (IllegalAccessException e) {
      logger.atSevere().withCause(e).log("Failed to apply default edit preferences");
      return EditPreferencesInfo.defaults();
    }
    return result;
  }

  private static List<String> changeTable(Config cfg) {
    return Lists.newArrayList(cfg.getStringList(CHANGE_TABLE, null, CHANGE_TABLE_COLUMN));
  }

  private static List<MenuItem> my(Config cfg) {
    List<MenuItem> my = new ArrayList<>();
    for (String subsection : cfg.getSubsections(UserConfigSections.MY)) {
      String url = my(cfg, subsection, KEY_URL, "#/");
      String target = my(cfg, subsection, KEY_TARGET, url.startsWith("#") ? null : "_blank");
      my.add(new MenuItem(subsection, url, target, my(cfg, subsection, KEY_ID, null)));
    }
    return my;
  }

  private static String my(Config cfg, String subsection, String key, String defaultValue) {
    String val = cfg.getString(UserConfigSections.MY, subsection, key);
    return !Strings.isNullOrEmpty(val) ? val : defaultValue;
  }
}
