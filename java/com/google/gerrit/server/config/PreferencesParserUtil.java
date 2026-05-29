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
import static com.google.gerrit.server.config.ConfigUtil.mergeWithDefaults;
import static com.google.gerrit.server.config.ConfigUtil.skipField;
import static com.google.gerrit.server.config.UserPreferencesConverter.DiffPreferencesInfoConverter.DIFF_PREFERENCES_INFO_CONVERTER;
import static com.google.gerrit.server.config.UserPreferencesConverter.EditPreferencesInfoConverter.EDIT_PREFERENCES_INFO_CONVERTER;
import static com.google.gerrit.server.config.UserPreferencesConverter.GeneralPreferencesInfoConverter.GENERAL_PREFERENCES_INFO_CONVERTER;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE_COLUMN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.proto.Entities.UserPreferences;
import com.google.gerrit.server.git.UserConfigSections;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/** Helper to read default or user preferences from Git-style config files. */
public class PreferencesParserUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final ImmutableList<MenuItem> DEFAULT_MY_MENU_ITEMS =
      ImmutableList.of(
          new MenuItem(/* name= */ "Dashboard", /* url= */ "/dashboard/self", /* target= */ null),
          new MenuItem(/* name= */ "Draft Comments", /* url= */ "/q/has:draft", /* target= */ null),
          new MenuItem(/* name= */ "Edits", /* url= */ "/q/has:edit", /* target= */ null),
          new MenuItem(
              /* name= */ "Watched Changes",
              /* url= */ "/q/is:watched+is:open",
              /* target= */ null),
          new MenuItem(
              /* name= */ "Starred Changes", /* url= */ "/q/is:starred", /* target= */ null),
          new MenuItem(
              /* name= */ "All Visible Changes", /* url= */ "/q/is:visible", /* target= */ null),
          new MenuItem(/* name= */ "Groups", /* url= */ "/settings/#Groups", /* target= */ null));

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
      r.my = parseMyMenus(my(cfg), defaultCfg);
    }
    return r;
  }

  /**
   * Returns a {@link GeneralPreferencesInfo} that is the result of parsing {@code defaultCfg} for
   * the server's default configs and {@code cfg} for the user's config.
   */
  public static GeneralPreferencesInfo parseGeneralPreferences(
      GeneralPreferencesInfo cfg, @Nullable Config defaultCfg) throws ConfigInvalidException {
    GeneralPreferencesInfo r =
        mergeWithDefaults(
            cfg,
            new GeneralPreferencesInfo(),
            defaultCfg != null
                ? parseDefaultGeneralPreferences(defaultCfg, null)
                : GeneralPreferencesInfo.defaults());
    r.changeTable = cfg.changeTable != null ? cfg.changeTable : Lists.newArrayList();
    r.my = parseMyMenus(cfg.my, defaultCfg);
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
   * server's default configs and {@code cfg} for the user's config.
   */
  public static DiffPreferencesInfo parseDiffPreferences(
      DiffPreferencesInfo cfg, @Nullable Config defaultCfg) throws ConfigInvalidException {
    return mergeWithDefaults(
        cfg,
        new DiffPreferencesInfo(),
        defaultCfg != null
            ? parseDefaultDiffPreferences(defaultCfg, null)
            : DiffPreferencesInfo.defaults());
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
   * server's default configs and {@code cfg} for the user's config.
   */
  public static EditPreferencesInfo parseEditPreferences(
      EditPreferencesInfo cfg, @Nullable Config defaultCfg) throws ConfigInvalidException {
    return mergeWithDefaults(
        cfg,
        new EditPreferencesInfo(),
        defaultCfg != null
            ? parseDefaultEditPreferences(defaultCfg, null)
            : EditPreferencesInfo.defaults());
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

  private static List<MenuItem> parseMyMenus(
      @Nullable List<MenuItem> my, @Nullable Config defaultCfg) {
    if (defaultCfg != null && (my == null || my.isEmpty())) {
      my = my(defaultCfg);
    }
    if (my == null) {
      my = new ArrayList<>();
    }
    if (my.isEmpty()) {
      return DEFAULT_MY_MENU_ITEMS;
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
      String url = my(cfg, subsection, KEY_URL, "/");
      boolean isRelative = url.startsWith("#") || url.startsWith("/");
      String target = my(cfg, subsection, KEY_TARGET, isRelative ? null : "_blank");
      my.add(new MenuItem(subsection, url, target, my(cfg, subsection, KEY_ID, null)));
    }
    return my;
  }

  private static String my(Config cfg, String subsection, String key, String defaultValue) {
    String val = cfg.getString(UserConfigSections.MY, subsection, key);
    return !Strings.isNullOrEmpty(val) ? val : defaultValue;
  }

  /** Provides methods for parsing user configs */
  interface PreferencesParser<T> {
    T parse(Config cfg, @Nullable Config defaultConfig, @Nullable T input)
        throws ConfigInvalidException;

    T fromUserPreferences(UserPreferences userPreferences, @Nullable Config defaultCfg)
        throws ConfigInvalidException;

    T getJavaDefaults();
  }

  /** Provides methods for parsing GeneralPreferencesInfo configs */
  public static class GeneralPreferencesParser
      implements PreferencesParser<GeneralPreferencesInfo> {
    public static GeneralPreferencesParser Instance = new GeneralPreferencesParser();

    private GeneralPreferencesParser() {}

    @Override
    public GeneralPreferencesInfo parse(
        Config cfg, @Nullable Config defaultCfg, @Nullable GeneralPreferencesInfo input)
        throws ConfigInvalidException {
      return PreferencesParserUtil.parseGeneralPreferences(cfg, defaultCfg, input);
    }

    @Override
    public GeneralPreferencesInfo fromUserPreferences(
        UserPreferences p, @Nullable Config defaultCfg) throws ConfigInvalidException {
      return PreferencesParserUtil.parseGeneralPreferences(
          GENERAL_PREFERENCES_INFO_CONVERTER.fromProto(p.getGeneralPreferencesInfo()), defaultCfg);
    }

    @Override
    public GeneralPreferencesInfo getJavaDefaults() {
      return GeneralPreferencesInfo.defaults();
    }
  }

  /** Provides methods for parsing EditPreferencesInfo configs */
  public static class EditPreferencesParser implements PreferencesParser<EditPreferencesInfo> {
    public static EditPreferencesParser Instance = new EditPreferencesParser();

    private EditPreferencesParser() {}

    @Override
    public EditPreferencesInfo parse(
        Config cfg, @Nullable Config defaultCfg, @Nullable EditPreferencesInfo input)
        throws ConfigInvalidException {
      return PreferencesParserUtil.parseEditPreferences(cfg, defaultCfg, input);
    }

    @Override
    public EditPreferencesInfo fromUserPreferences(UserPreferences p, @Nullable Config defaultCfg)
        throws ConfigInvalidException {
      return PreferencesParserUtil.parseEditPreferences(
          EDIT_PREFERENCES_INFO_CONVERTER.fromProto(p.getEditPreferencesInfo()), defaultCfg);
    }

    @Override
    public EditPreferencesInfo getJavaDefaults() {
      return EditPreferencesInfo.defaults();
    }
  }

  /** Provides methods for parsing DiffPreferencesInfo configs */
  public static class DiffPreferencesParser implements PreferencesParser<DiffPreferencesInfo> {
    public static DiffPreferencesParser Instance = new DiffPreferencesParser();

    private DiffPreferencesParser() {}

    @Override
    public DiffPreferencesInfo parse(
        Config cfg, @Nullable Config defaultCfg, @Nullable DiffPreferencesInfo input)
        throws ConfigInvalidException {
      return PreferencesParserUtil.parseDiffPreferences(cfg, defaultCfg, input);
    }

    @Override
    public DiffPreferencesInfo fromUserPreferences(UserPreferences p, @Nullable Config defaultCfg)
        throws ConfigInvalidException {
      return PreferencesParserUtil.parseDiffPreferences(
          DIFF_PREFERENCES_INFO_CONVERTER.fromProto(p.getDiffPreferencesInfo()), defaultCfg);
    }

    @Override
    public DiffPreferencesInfo getJavaDefaults() {
      return DiffPreferencesInfo.defaults();
    }
  }
}
