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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;
import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE_COLUMN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.PreferencesParserUtil;
import com.google.gerrit.server.config.VersionedDefaultPreferences;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/**
 * Parses/writes preferences from/to a {@link Config} file.
 *
 * <p>This is a low-level API. Read/write of preferences in a user branch should be done through
 * {@link AccountsUpdate} or {@link AccountConfig}.
 *
 * <p>The config file has separate sections for general, diff and edit preferences:
 *
 * <pre>
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
public class StoredPreferences {
  public static final String PREFERENCES_CONFIG = "preferences.config";

  private final Account.Id accountId;
  private final Config cfg;
  private final Config defaultCfg;
  private final ValidationError.Sink validationErrorSink;

  private GeneralPreferencesInfo generalPreferences;
  private DiffPreferencesInfo diffPreferences;
  private EditPreferencesInfo editPreferences;

  StoredPreferences(
      Account.Id accountId,
      Config cfg,
      Config defaultCfg,
      ValidationError.Sink validationErrorSink) {
    this.accountId = requireNonNull(accountId, "accountId");
    this.cfg = requireNonNull(cfg, "cfg");
    this.defaultCfg = requireNonNull(defaultCfg, "defaultCfg");
    this.validationErrorSink = requireNonNull(validationErrorSink, "validationErrorSink");
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
          PreferencesParserUtil.parseDefaultGeneralPreferences(defaultCfg, null));
      setChangeTable(cfg, mergedGeneralPreferencesInput.changeTable);
      setMy(cfg, mergedGeneralPreferencesInput.my);

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
          PreferencesParserUtil.parseDefaultDiffPreferences(defaultCfg, null));

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
          PreferencesParserUtil.parseDefaultEditPreferences(defaultCfg, null));

      // evict the cached edit preferences
      this.editPreferences = null;
    }

    return cfg;
  }

  /** Returns the content of the {@code preferences.config} file as {@link Config}. */
  Config getRaw() {
    return cfg;
  }

  private GeneralPreferencesInfo parseGeneralPreferences(@Nullable GeneralPreferencesInfo input) {
    try {
      return PreferencesParserUtil.parseGeneralPreferences(cfg, defaultCfg, input);
    } catch (ConfigInvalidException e) {
      validationErrorSink.error(
          ValidationError.create(
              PREFERENCES_CONFIG,
              String.format(
                  "Invalid general preferences for account %d: %s",
                  accountId.get(), e.getMessage())));
      return new GeneralPreferencesInfo();
    }
  }

  private DiffPreferencesInfo parseDiffPreferences(@Nullable DiffPreferencesInfo input) {
    try {
      return PreferencesParserUtil.parseDiffPreferences(cfg, defaultCfg, input);
    } catch (ConfigInvalidException e) {
      validationErrorSink.error(
          ValidationError.create(
              PREFERENCES_CONFIG,
              String.format(
                  "Invalid diff preferences for account %d: %s", accountId.get(), e.getMessage())));
      return new DiffPreferencesInfo();
    }
  }

  private EditPreferencesInfo parseEditPreferences(@Nullable EditPreferencesInfo input) {
    try {
      return PreferencesParserUtil.parseEditPreferences(cfg, defaultCfg, input);
    } catch (ConfigInvalidException e) {
      validationErrorSink.error(
          ValidationError.create(
              PREFERENCES_CONFIG,
              String.format(
                  "Invalid edit preferences for account %d: %s", accountId.get(), e.getMessage())));
      return new EditPreferencesInfo();
    }
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
    defaultPrefs.commit(md);

    return PreferencesParserUtil.parseGeneralPreferences(defaultPrefs.getConfig(), null, null);
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

    return PreferencesParserUtil.parseDiffPreferences(defaultPrefs.getConfig(), null, null);
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

    return PreferencesParserUtil.parseEditPreferences(defaultPrefs.getConfig(), null, null);
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

  static Config readDefaultConfig(AllUsersName allUsersName, Repository allUsersRepo)
      throws IOException, ConfigInvalidException {
    VersionedDefaultPreferences defaultPrefs = new VersionedDefaultPreferences();
    defaultPrefs.load(allUsersName, allUsersRepo);
    return defaultPrefs.getConfig();
  }

  private static void setChangeTable(Config cfg, List<String> changeTable) {
    if (changeTable != null) {
      unsetSection(cfg, UserConfigSections.CHANGE_TABLE);
      cfg.setStringList(UserConfigSections.CHANGE_TABLE, null, CHANGE_TABLE_COLUMN, changeTable);
    }
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

  private static void unsetSection(Config cfg, String section) {
    cfg.unsetSection(section, null);
    for (String subsection : cfg.getSubsections(section)) {
      cfg.unsetSection(section, subsection);
    }
  }
}
