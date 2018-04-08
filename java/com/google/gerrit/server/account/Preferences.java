// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.config.ConfigUtil.loadSection;
import static com.google.gerrit.config.ConfigUtil.skipField;
import static com.google.gerrit.config.ConfigUtil.storeSection;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE_COLUMN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_MATCH;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TOKEN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.URL_ALIAS;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses/writes preferences from/to a {@link Config} file.
 *
 * <p>This is a low-level API. Read/write of preferences in a user branch should be done through
 * {@link AccountsUpdate} or {@link AccountConfig}.
 *
 * <p>The config file has separate sections for general, diff and edit preferences:
 *
 * <pre>
 *   [general]
 *     showSiteHeader = false
 *   [diff]
 *     hideTopMenu = true
 *   [edit]
 *     lineLength = 80
 * </pre>
 *
 * <p>The parameter names match the names that are used in the preferences REST API.
 *
 * <p>If the preference is omitted in the config file, then the default value for the preference is
 * used.
 *
 * <p>Defaults for preferences that apply for all accounts can be configured in the {@code
 * refs/users/default} branch in the {@code All-Users} repository. The config for the default
 * preferences must be provided to this class so that it can read default values from it.
 *
 * <p>The preferences are lazily parsed.
 */
public class Preferences {
  private static final Logger log = LoggerFactory.getLogger(Preferences.class);

  public static final String PREFERENCES_CONFIG = "preferences.config";

  private final Account.Id accountId;
  private final Config cfg;
  private final Config defaultCfg;
  private final ValidationError.Sink validationErrorSink;

  private GeneralPreferencesInfo generalPreferences;
  private DiffPreferencesInfo diffPreferences;
  private EditPreferencesInfo editPreferences;

  Preferences(
      Account.Id accountId,
      Config cfg,
      Config defaultCfg,
      ValidationError.Sink validationErrorSink) {
    this.accountId = checkNotNull(accountId, "accountId");
    this.cfg = checkNotNull(cfg, "cfg");
    this.defaultCfg = checkNotNull(defaultCfg, "defaultCfg");
    this.validationErrorSink = checkNotNull(validationErrorSink, "validationErrorSink");
  }

  public GeneralPreferencesInfo getGeneralPreferences() {
    if (generalPreferences == null) {
      parse();
    }
    return generalPreferences;
  }

  public DiffPreferencesInfo getDiffPreferences() {
    if (diffPreferences == null) {
      parse();
    }
    return diffPreferences;
  }

  public EditPreferencesInfo getEditPreferences() {
    if (editPreferences == null) {
      parse();
    }
    return editPreferences;
  }

  public void parse() {
    generalPreferences = parseGeneralPreferences(null);
    diffPreferences = parseDiffPreferences(null);
    editPreferences = parseEditPreferences(null);
  }

  public Config saveGeneralPreferences(
      Optional<GeneralPreferencesInfo> generalPreferencesInput,
      Optional<DiffPreferencesInfo> diffPreferencesInput,
      Optional<EditPreferencesInfo> editPreferencesInput)
      throws ConfigInvalidException {
    if (generalPreferencesInput.isPresent()) {
      GeneralPreferencesInfo mergedGeneralPreferencesInput =
          parseGeneralPreferences(generalPreferencesInput.get());

      storeSection(
          cfg,
          UserConfigSections.GENERAL,
          null,
          mergedGeneralPreferencesInput,
          parseDefaultGeneralPreferences(defaultCfg, null));
      setChangeTable(cfg, mergedGeneralPreferencesInput.changeTable);
      setMy(cfg, mergedGeneralPreferencesInput.my);
      setUrlAliases(cfg, mergedGeneralPreferencesInput.urlAliases);

      // evict the cached general preferences
      this.generalPreferences = null;
    }

    if (diffPreferencesInput.isPresent()) {
      DiffPreferencesInfo mergedDiffPreferencesInput =
          parseDiffPreferences(diffPreferencesInput.get());

      storeSection(
          cfg,
          UserConfigSections.DIFF,
          null,
          mergedDiffPreferencesInput,
          parseDefaultDiffPreferences(defaultCfg, null));

      // evict the cached diff preferences
      this.diffPreferences = null;
    }

    if (editPreferencesInput.isPresent()) {
      EditPreferencesInfo mergedEditPreferencesInput =
          parseEditPreferences(editPreferencesInput.get());

      storeSection(
          cfg,
          UserConfigSections.EDIT,
          null,
          mergedEditPreferencesInput,
          parseDefaultEditPreferences(defaultCfg, null));

      // evict the cached edit preferences
      this.editPreferences = null;
    }

    return cfg;
  }

  private GeneralPreferencesInfo parseGeneralPreferences(@Nullable GeneralPreferencesInfo input) {
    try {
      return parseGeneralPreferences(cfg, defaultCfg, input);
    } catch (ConfigInvalidException e) {
      validationErrorSink.error(
          new ValidationError(
              PREFERENCES_CONFIG,
              String.format(
                  "Invalid general preferences for account %d: %s",
                  accountId.get(), e.getMessage())));
      return new GeneralPreferencesInfo();
    }
  }

  private DiffPreferencesInfo parseDiffPreferences(@Nullable DiffPreferencesInfo input) {
    try {
      return parseDiffPreferences(cfg, defaultCfg, input);
    } catch (ConfigInvalidException e) {
      validationErrorSink.error(
          new ValidationError(
              PREFERENCES_CONFIG,
              String.format(
                  "Invalid diff preferences for account %d: %s", accountId.get(), e.getMessage())));
      return new DiffPreferencesInfo();
    }
  }

  private EditPreferencesInfo parseEditPreferences(@Nullable EditPreferencesInfo input) {
    try {
      return parseEditPreferences(cfg, defaultCfg, input);
    } catch (ConfigInvalidException e) {
      validationErrorSink.error(
          new ValidationError(
              PREFERENCES_CONFIG,
              String.format(
                  "Invalid edit preferences for account %d: %s", accountId.get(), e.getMessage())));
      return new EditPreferencesInfo();
    }
  }

  private static GeneralPreferencesInfo parseGeneralPreferences(
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
      r.urlAliases = input.urlAliases;
    } else {
      r.changeTable = parseChangeTableColumns(cfg, defaultCfg);
      r.my = parseMyMenus(cfg, defaultCfg);
      r.urlAliases = parseUrlAliases(cfg, defaultCfg);
    }
    return r;
  }

  private static DiffPreferencesInfo parseDiffPreferences(
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

  private static EditPreferencesInfo parseEditPreferences(
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

  private static GeneralPreferencesInfo parseDefaultGeneralPreferences(
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

  private static DiffPreferencesInfo parseDefaultDiffPreferences(
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

  private static EditPreferencesInfo parseDefaultEditPreferences(
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
      log.error("Failed to apply default general preferences", e);
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
      log.error("Failed to apply default diff preferences", e);
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
      log.error("Failed to apply default edit preferences", e);
      return EditPreferencesInfo.defaults();
    }
    return result;
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
      my.add(new MenuItem("Changes", "#/dashboard/self", null));
      my.add(new MenuItem("Draft Comments", "#/q/has:draft", null));
      my.add(new MenuItem("Edits", "#/q/has:edit", null));
      my.add(new MenuItem("Watched Changes", "#/q/is:watched+is:open", null));
      my.add(new MenuItem("Starred Changes", "#/q/is:starred", null));
      my.add(new MenuItem("Groups", "#/groups/self", null));
    }
    return my;
  }

  private static Map<String, String> parseUrlAliases(Config cfg, @Nullable Config defaultCfg) {
    Map<String, String> urlAliases = urlAliases(cfg);
    if (urlAliases == null && defaultCfg != null) {
      urlAliases = urlAliases(defaultCfg);
    }
    return urlAliases;
  }

  public static GeneralPreferencesInfo readDefaultGeneralPreferences(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    return parseGeneralPreferences(readDefaultConfig(allUsersRepo), null, null);
  }

  public static DiffPreferencesInfo readDefaultDiffPreferences(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    return parseDiffPreferences(readDefaultConfig(allUsersRepo), null, null);
  }

  public static EditPreferencesInfo readDefaultEditPreferences(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    return parseEditPreferences(readDefaultConfig(allUsersRepo), null, null);
  }

  static Config readDefaultConfig(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    VersionedDefaultPreferences defaultPrefs = new VersionedDefaultPreferences();
    defaultPrefs.load(allUsersRepo);
    return defaultPrefs.getConfig();
  }

  public static GeneralPreferencesInfo updateDefaultGeneralPreferences(
      MetaDataUpdate md, GeneralPreferencesInfo input) throws IOException, ConfigInvalidException {
    VersionedDefaultPreferences defaultPrefs = new VersionedDefaultPreferences();
    defaultPrefs.load(md);
    storeSection(
        defaultPrefs.getConfig(),
        UserConfigSections.GENERAL,
        null,
        input,
        GeneralPreferencesInfo.defaults());
    setMy(defaultPrefs.getConfig(), input.my);
    setChangeTable(defaultPrefs.getConfig(), input.changeTable);
    setUrlAliases(defaultPrefs.getConfig(), input.urlAliases);
    defaultPrefs.commit(md);

    return parseGeneralPreferences(defaultPrefs.getConfig(), null, null);
  }

  public static DiffPreferencesInfo updateDefaultDiffPreferences(
      MetaDataUpdate md, DiffPreferencesInfo input) throws IOException, ConfigInvalidException {
    VersionedDefaultPreferences defaultPrefs = new VersionedDefaultPreferences();
    defaultPrefs.load(md);
    storeSection(
        defaultPrefs.getConfig(),
        UserConfigSections.DIFF,
        null,
        input,
        DiffPreferencesInfo.defaults());
    defaultPrefs.commit(md);

    return parseDiffPreferences(defaultPrefs.getConfig(), null, null);
  }

  public static EditPreferencesInfo updateDefaultEditPreferences(
      MetaDataUpdate md, EditPreferencesInfo input) throws IOException, ConfigInvalidException {
    VersionedDefaultPreferences defaultPrefs = new VersionedDefaultPreferences();
    defaultPrefs.load(md);
    storeSection(
        defaultPrefs.getConfig(),
        UserConfigSections.EDIT,
        null,
        input,
        EditPreferencesInfo.defaults());
    defaultPrefs.commit(md);

    return parseEditPreferences(defaultPrefs.getConfig(), null, null);
  }

  private static List<String> changeTable(Config cfg) {
    return Lists.newArrayList(cfg.getStringList(CHANGE_TABLE, null, CHANGE_TABLE_COLUMN));
  }

  private static void setChangeTable(Config cfg, List<String> changeTable) {
    if (changeTable != null) {
      unsetSection(cfg, UserConfigSections.CHANGE_TABLE);
      cfg.setStringList(UserConfigSections.CHANGE_TABLE, null, CHANGE_TABLE_COLUMN, changeTable);
    }
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

  private static void setMy(Config cfg, List<MenuItem> my) {
    if (my != null) {
      unsetSection(cfg, UserConfigSections.MY);
      for (MenuItem item : my) {
        checkState(!isNullOrEmpty(item.name), "MenuItem.name must not be null or empty");
        checkState(!isNullOrEmpty(item.url), "MenuItem.url must not be null or empty");

        setMy(cfg, item.name, KEY_URL, item.url);
        setMy(cfg, item.name, KEY_TARGET, item.target);
        setMy(cfg, item.name, KEY_ID, item.id);
      }
    }
  }

  public static void validateMy(List<MenuItem> my) throws BadRequestException {
    if (my == null) {
      return;
    }
    for (MenuItem item : my) {
      checkRequiredMenuItemField(item.name, "name");
      checkRequiredMenuItemField(item.url, "URL");
    }
  }

  private static void checkRequiredMenuItemField(String value, String name)
      throws BadRequestException {
    if (isNullOrEmpty(value)) {
      throw new BadRequestException(name + " for menu item is required");
    }
  }

  private static boolean isNullOrEmpty(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static void setMy(Config cfg, String section, String key, @Nullable String val) {
    if (val == null || val.trim().isEmpty()) {
      cfg.unset(UserConfigSections.MY, section.trim(), key);
    } else {
      cfg.setString(UserConfigSections.MY, section.trim(), key, val.trim());
    }
  }

  private static Map<String, String> urlAliases(Config cfg) {
    HashMap<String, String> urlAliases = new HashMap<>();
    for (String subsection : cfg.getSubsections(URL_ALIAS)) {
      urlAliases.put(
          cfg.getString(URL_ALIAS, subsection, KEY_MATCH),
          cfg.getString(URL_ALIAS, subsection, KEY_TOKEN));
    }
    return !urlAliases.isEmpty() ? urlAliases : null;
  }

  private static void setUrlAliases(Config cfg, Map<String, String> urlAliases) {
    if (urlAliases != null) {
      for (String subsection : cfg.getSubsections(URL_ALIAS)) {
        cfg.unsetSection(URL_ALIAS, subsection);
      }

      int i = 1;
      for (Entry<String, String> e : urlAliases.entrySet()) {
        cfg.setString(URL_ALIAS, URL_ALIAS + i, KEY_MATCH, e.getKey());
        cfg.setString(URL_ALIAS, URL_ALIAS + i, KEY_TOKEN, e.getValue());
        i++;
      }
    }
  }

  private static void unsetSection(Config cfg, String section) {
    cfg.unsetSection(section, null);
    for (String subsection : cfg.getSubsections(section)) {
      cfg.unsetSection(section, subsection);
    }
  }

  private static class VersionedDefaultPreferences extends VersionedMetaData {
    private Config cfg;

    @Override
    protected String getRefName() {
      return RefNames.REFS_USERS_DEFAULT;
    }

    private Config getConfig() {
      checkState(cfg != null, "Default preferences not loaded yet.");
      return cfg;
    }

    @Override
    protected void onLoad() throws IOException, ConfigInvalidException {
      cfg = readConfig(PREFERENCES_CONFIG);
    }

    @Override
    protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
      if (Strings.isNullOrEmpty(commit.getMessage())) {
        commit.setMessage("Update default preferences\n");
      }
      saveConfig(PREFERENCES_CONFIG, cfg);
      return true;
    }
  }
}
