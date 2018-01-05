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
import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.skipField;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;
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
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.VersionedMetaData;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferencesConfig {
  private static final Logger log = LoggerFactory.getLogger(PreferencesConfig.class);

  public static final String PREFERENCES_CONFIG = "preferences.config";

  private final Account.Id accountId;
  private final Config cfg;
  private final Config defaultCfg;
  private final ValidationError.Sink validationErrorSink;

  private GeneralPreferencesInfo generalPreferences;

  public PreferencesConfig(
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

  public void parse() {
    generalPreferences = parse(null);
  }

  public Config saveGeneralPreferences(GeneralPreferencesInfo input) throws ConfigInvalidException {
    // merge configs
    input = parse(input);

    storeSection(
        cfg, UserConfigSections.GENERAL, null, input, parseDefaultPreferences(defaultCfg, null));
    setChangeTable(cfg, input.changeTable);
    setMy(cfg, input.my);
    setUrlAliases(cfg, input.urlAliases);

    // evict the cached general preferences
    this.generalPreferences = null;

    return cfg;
  }

  private GeneralPreferencesInfo parse(@Nullable GeneralPreferencesInfo input) {
    try {
      return parse(cfg, defaultCfg, input);
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

  private static GeneralPreferencesInfo parse(
      Config cfg, @Nullable Config defaultCfg, @Nullable GeneralPreferencesInfo input)
      throws ConfigInvalidException {
    GeneralPreferencesInfo r =
        loadSection(
            cfg,
            UserConfigSections.GENERAL,
            null,
            new GeneralPreferencesInfo(),
            defaultCfg != null
                ? parseDefaultPreferences(defaultCfg, input)
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

  private static GeneralPreferencesInfo parseDefaultPreferences(
      Config defaultCfg, GeneralPreferencesInfo input) throws ConfigInvalidException {
    GeneralPreferencesInfo allUserPrefs = new GeneralPreferencesInfo();
    loadSection(
        defaultCfg,
        UserConfigSections.GENERAL,
        null,
        allUserPrefs,
        GeneralPreferencesInfo.defaults(),
        input);
    return updateDefaults(allUserPrefs);
  }

  private static GeneralPreferencesInfo updateDefaults(GeneralPreferencesInfo input) {
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

  public static GeneralPreferencesInfo readDefaultPreferences(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    return parse(readDefaultConfig(allUsersRepo), null, null);
  }

  static Config readDefaultConfig(Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    VersionedDefaultPreferences defaultPrefs = new VersionedDefaultPreferences();
    defaultPrefs.load(allUsersRepo);
    return defaultPrefs.getConfig();
  }

  public static GeneralPreferencesInfo updateDefaultPreferences(
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

    return parse(defaultPrefs.getConfig(), null, null);
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
