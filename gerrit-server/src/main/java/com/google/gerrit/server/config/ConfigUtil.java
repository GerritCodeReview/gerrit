// Copyright (C) 2009 The Android Open Source Project
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

import static org.eclipse.jgit.util.StringUtils.equalsIgnoreCase;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigUtil {
  /**
   * Parse a Java enumeration from the configuration.
   *
   * @param <T> type of the enumeration object.
   * @param config the configuration file to read.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param defaultValue default value to return if the setting was not set.
   *        Must not be null as the enumeration values are derived from this.
   * @return the selected enumeration value, or {@code defaultValue}.
   */
  public static <T extends Enum<?>> T getEnum(final Config config,
      final String section, final String subsection, final String setting,
      final T defaultValue) {
    final T[] all = allValuesOf(defaultValue);
    return getEnum(config, section, subsection, setting, all, defaultValue);
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] allValuesOf(final T defaultValue) {
    try {
      return (T[]) defaultValue.getClass().getMethod("values").invoke(null);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (SecurityException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    }
  }

  /**
   * Parse a Java enumeration from the configuration.
   *
   * @param <T> type of the enumeration object.
   * @param config the configuration file to read.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param all all possible values in the enumeration which should be
   *        recognized. This should be {@code EnumType.values()}.
   * @param defaultValue default value to return if the setting was not set.
   *        This value may be null.
   * @return the selected enumeration value, or {@code defaultValue}.
   */
  public static <T extends Enum<?>> T getEnum(final Config config,
      final String section, final String subsection, final String setting,
      final T[] all, final T defaultValue) {
    final String valueString = config.getString(section, subsection, setting);
    if (valueString == null) {
      return defaultValue;
    }

    return getEnum(section, subsection, setting, valueString, all);
  }

  /**
   * Parse a Java enumeration from the configuration.
   *
   * @param <T> type of the enumeration object.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param valueString string value from git Config
   * @param all all possible values in the enumeration which should be
   *        recognized. This should be {@code EnumType.values()}.
   * @return the selected enumeration value, or {@code defaultValue}.
   */
  private static <T extends Enum<?>> T getEnum(final String section,
      final String subsection, final String setting, String valueString,
      final T[] all) {

    String n = valueString.replace(' ', '_');
    for (final T e : all) {
      if (equalsIgnoreCase(e.name(), n)) {
        return e;
      }
    }

    final StringBuilder r = new StringBuilder();
    r.append("Value \"");
    r.append(valueString);
    r.append("\" not recognized in ");
    r.append(section);
    if (subsection != null) {
      r.append(".");
      r.append(subsection);
    }
    r.append(".");
    r.append(setting);
    r.append("; supported values are: ");
    for (final T e : all) {
      r.append(e.name());
      r.append(" ");
    }

    throw new IllegalArgumentException(r.toString().trim());
  }

  /**
   * Parse a Java enumeration list from the configuration.
   *
   * @param <T> type of the enumeration object.
   * @param config the configuration file to read.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param defaultValue default value to return if the setting was not set.
   *        Must not be null as the enumeration values are derived from this.
   * @return the selected enumeration values list, or {@code defaultValue}.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Enum<?>> List<T> getEnumList(final Config config,
      final String section, final String subsection, final String setting,
      final T defaultValue) {
    final T[] all = allValuesOf(defaultValue);
    return getEnumList(config, section, subsection, setting, all, defaultValue);
  }

  /**
   * Parse a Java enumeration list from the configuration.
   *
   * @param <T> type of the enumeration object.
   * @param config the configuration file to read.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param all all possible values in the enumeration which should be
   *        recognized. This should be {@code EnumType.values()}.
   * @param defaultValue default value to return if the setting was not set.
   *        This value may be null.
   * @return the selected enumeration values list, or {@code defaultValue}.
   */
  public static <T extends Enum<?>> List<T> getEnumList(final Config config,
      final String section, final String subsection, final String setting,
      final T[] all, final T defaultValue) {
    final List<T> list = new ArrayList<T>();
    final String[] values = config.getStringList(section, subsection, setting);
    if (values.length == 0) {
      list.add(defaultValue);
    } else {
      for (String string : values) {
        if (string != null) {
          list.add(getEnum(section, subsection, setting, string, all));
        }
      }
    }
    return list;
  }

  /**
   * Parse a numerical time unit, such as "1 minute", from the configuration.
   *
   * @param config the configuration file to read.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param defaultValue default value to return if no value was set in the
   *        configuration file.
   * @param wantUnit the units of {@code defaultValue} and the return value, as
   *        well as the units to assume if the value does not contain an
   *        indication of the units.
   * @return the setting, or {@code defaultValue} if not set, expressed in
   *         {@code units}.
   */
  public static long getTimeUnit(final Config config, final String section,
      final String subsection, final String setting, final long defaultValue,
      final TimeUnit wantUnit) {
    final String valueString = config.getString(section, subsection, setting);
    if (valueString == null) {
      return defaultValue;
    }

    String s = valueString.trim();
    if (s.length() == 0) {
      return defaultValue;
    }

    try {
      return getTimeUnit(s, defaultValue, wantUnit);
    } catch (IllegalArgumentException notTime) {
      throw notTimeUnit(section, subsection, setting, valueString);
    }
  }

  /**
   * Parse a numerical time unit, such as "1 minute", from a string.
   *
   * @param valueString the string to parse.
   * @param defaultValue default value to return if no value was set in the
   *        configuration file.
   * @param wantUnit the units of {@code defaultValue} and the return value, as
   *        well as the units to assume if the value does not contain an
   *        indication of the units.
   * @return the setting, or {@code defaultValue} if not set, expressed in
   *         {@code units}.
   */
  public static long getTimeUnit(final String valueString, long defaultValue,
      TimeUnit wantUnit) {
    Matcher m = Pattern.compile("^([1-9][0-9]*)\\s*(.*)$").matcher(valueString);
    if (!m.matches()) {
      return defaultValue;
    }

    String digits = m.group(1);
    String unitName = m.group(2).trim();

    TimeUnit inputUnit;
    int inputMul;

    if ("".equals(unitName)) {
      inputUnit = wantUnit;
      inputMul = 1;

    } else if (match(unitName, "ms", "milliseconds")) {
      inputUnit = TimeUnit.MILLISECONDS;
      inputMul = 1;

    } else if (match(unitName, "s", "sec", "second", "seconds")) {
      inputUnit = TimeUnit.SECONDS;
      inputMul = 1;

    } else if (match(unitName, "m", "min", "minute", "minutes")) {
      inputUnit = TimeUnit.MINUTES;
      inputMul = 1;

    } else if (match(unitName, "h", "hr", "hour", "hours")) {
      inputUnit = TimeUnit.HOURS;
      inputMul = 1;

    } else if (match(unitName, "d", "day", "days")) {
      inputUnit = TimeUnit.DAYS;
      inputMul = 1;

    } else if (match(unitName, "w", "week", "weeks")) {
      inputUnit = TimeUnit.DAYS;
      inputMul = 7;

    } else if (match(unitName, "mon", "month", "months")) {
      inputUnit = TimeUnit.DAYS;
      inputMul = 30;

    } else if (match(unitName, "y", "year", "years")) {
      inputUnit = TimeUnit.DAYS;
      inputMul = 365;

    } else {
      throw notTimeUnit(valueString);
    }

    try {
      return wantUnit.convert(Long.parseLong(digits) * inputMul, inputUnit);
    } catch (NumberFormatException nfe) {
      throw notTimeUnit(valueString);
    }
  }

  /**
   * Resolve groups from group names, via the database. Group names not found in
   * the database will be skipped.
   *
   * @param dbfactory database to resolve from.
   * @param groupNames group names to resolve.
   * @param log log for any warnings and errors.
   * @param groupNotFoundWarning formatted message to output to the log for each
   *        group name which is not found in the database. <code>{0}</code> will
   *        be replaced with the group name.
   * @return the actual groups resolved from the database. If no groups are
   *         found, returns an empty {@code Set}, never {@code null}.
   */
  public static Set<AccountGroup.UUID> groupsFor(
      SchemaFactory<ReviewDb> dbfactory, String[] groupNames, Logger log,
      String groupNotFoundWarning) {
    final Set<AccountGroup.UUID> result = new HashSet<AccountGroup.UUID>();
    try {
      final ReviewDb db = dbfactory.open();
      try {
        for (String name : groupNames) {
          AccountGroupName group =
              db.accountGroupNames().get(new AccountGroup.NameKey(name));
          if (group == null) {
            log.warn(MessageFormat.format(groupNotFoundWarning, name));
            continue;
          }

          AccountGroup ag = db.accountGroups().get(group.getId());
          if (ag == null) {
            log.warn(MessageFormat.format(groupNotFoundWarning, name));
            continue;
          }

          result.add(ag.getGroupUUID());
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      log.error("Database error, cannot load groups", e);
    }
    return result;
  }

  /**
   * Resolve groups from group names, via the database. Group names not found in
   * the database will be skipped.
   *
   * @param dbfactory database to resolve from.
   * @param groupNames group names to resolve.
   * @param log log for any warnings and errors.
   * @return the actual groups resolved from the database. If no groups are
   *         found, returns an empty {@code Set}, never {@code null}.
   */
  public static Set<AccountGroup.UUID> groupsFor(
      SchemaFactory<ReviewDb> dbfactory, String[] groupNames, Logger log) {
    return groupsFor(dbfactory, groupNames, log,
        "Group \"{0}\" not in database, skipping.");
  }

  private static boolean match(final String a, final String... cases) {
    for (final String b : cases) {
      if (equalsIgnoreCase(a, b)) {
        return true;
      }
    }
    return false;
  }

  private static IllegalArgumentException notTimeUnit(final String section,
      final String subsection, final String setting, final String valueString) {
    return new IllegalArgumentException("Invalid time unit value: " + section
        + (subsection != null ? "." + subsection : "") + "." + setting + " = "
        + valueString);
  }

  private static IllegalArgumentException notTimeUnit(final String val) {
    return new IllegalArgumentException("Invalid time unit value: " + val);
  }

  private ConfigUtil() {
  }
}
