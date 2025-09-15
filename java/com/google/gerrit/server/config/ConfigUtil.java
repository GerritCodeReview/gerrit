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

import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class ConfigUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @SuppressWarnings("unchecked")
  private static <T> T[] allValuesOf(T defaultValue) {
    try {
      return (T[]) defaultValue.getClass().getMethod("values").invoke(null);
    } catch (IllegalArgumentException
        | NoSuchMethodException
        | InvocationTargetException
        | IllegalAccessException
        | SecurityException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    }
  }

  /**
   * Parse a Java enumeration from the configuration.
   *
   * @param <T> type of the enumeration object.
   * @param section section the key is in.
   * @param subsection subsection the key is in, or null if not in a subsection.
   * @param setting name of the setting to read.
   * @param valueString string value from git Config
   * @param all all possible values in the enumeration which should be recognized. This should be
   *     {@code EnumType.values()}.
   * @return the selected enumeration value, or {@code defaultValue}.
   */
  private static <T extends Enum<?>> T getEnum(
      final String section,
      final String subsection,
      final String setting,
      String valueString,
      final T[] all) {

    String n = valueString.replace(' ', '_').replace('-', '_');
    for (T e : all) {
      if (e.name().equalsIgnoreCase(n)) {
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
    for (T e : all) {
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
   * @param defaultValue default value to return if the setting was not set. Must not be null as the
   *     enumeration values are derived from this.
   * @return the selected enumeration values list, or {@code defaultValue}.
   */
  public static <T extends Enum<?>> List<T> getEnumList(
      final Config config,
      final String section,
      final String subsection,
      final String setting,
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
   * @param all all possible values in the enumeration which should be recognized. This should be
   *     {@code EnumType.values()}.
   * @param defaultValue default value to return if the setting was not set. This value may be null.
   * @return the selected enumeration values list, or {@code defaultValue}.
   */
  public static <T extends Enum<?>> List<T> getEnumList(
      final Config config,
      final String section,
      final String subsection,
      final String setting,
      final T[] all,
      final T defaultValue) {
    final List<T> list = new ArrayList<>();
    final String[] values = config.getStringList(section, subsection, setting);
    if (values.length == 0) {
      list.add(defaultValue);
    } else {
      for (String string : values) {
        if (string != null) {
          try {
            list.add(getEnum(section, subsection, setting, string, all));
          } catch (IllegalArgumentException ex) {
            // It's better to ignore a wrongly configured enum, rather than fail to load Gerrit.
            logger.atWarning().log("%s", ex.getMessage());
          }
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
   * @param defaultValue default value to return if no value was set in the configuration file.
   * @param wantUnit the units of {@code defaultValue} and the return value, as well as the units to
   *     assume if the value does not contain an indication of the units.
   * @return the setting, or {@code defaultValue} if not set, expressed in {@code units}.
   */
  public static long getTimeUnit(
      final Config config,
      final String section,
      final String subsection,
      final String setting,
      final long defaultValue,
      final TimeUnit wantUnit) {
    final String valueString = config.getString(section, subsection, setting);
    try {
      return getTimeUnit(valueString, defaultValue, wantUnit);
    } catch (IllegalArgumentException notTime) {
      throw notTimeUnit(section, subsection, setting, valueString, notTime);
    }
  }

  /**
   * Parse a numerical time unit, such as "1 minute", from a string.
   *
   * @param valueString the string to parse.
   * @param defaultValue default value to return if no value was set in the configuration file.
   * @param wantUnit the units of {@code defaultValue} and the return value, as well as the units to
   *     assume if the value does not contain an indication of the units.
   * @return the setting, or {@code defaultValue} if not set, expressed in {@code units}.
   */
  public static long getTimeUnit(String valueString, long defaultValue, TimeUnit wantUnit) {
    if (valueString == null) {
      return defaultValue;
    }

    String s = valueString.trim();
    if (s.isEmpty()) {
      return defaultValue;
    }

    if (s.startsWith("-") /* negative */) {
      throw notTimeUnit(valueString);
    }

    Matcher m = Pattern.compile("^(0|[1-9][0-9]*)\\s*(.*)$").matcher(s);
    if (!m.matches()) {
      throw notTimeUnit(valueString);
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
      throw notTimeUnit(valueString, nfe);
    }
  }

  public static String getRequired(Config cfg, String section, String name) {
    final String v = cfg.getString(section, null, name);
    if (v == null || "".equals(v)) {
      throw new IllegalArgumentException("No " + section + "." + name + " configured");
    }
    return v;
  }

  /**
   * Store section by inspecting Java class attributes.
   *
   * <p>Optimize the storage by unsetting a variable if it is being set to default value by the
   * server.
   *
   * <p>Fields marked with final or transient modifiers are skipped.
   *
   * @param cfg config in which the values should be stored
   * @param section section
   * @param sub subsection
   * @param s instance of class with config values
   * @param defaults instance of class with default values
   */
  public static <T> void storeSection(Config cfg, String section, String sub, T s, T defaults)
      throws ConfigInvalidException {
    try {
      for (Field f : s.getClass().getDeclaredFields()) {
        if (skipField(f)) {
          continue;
        }
        Class<?> t = f.getType();
        String n = f.getName();
        f.setAccessible(true);
        Object c = f.get(s);
        Object d = f.get(defaults);
        if (!isString(t) && !isCollectionOrMap(t)) {
          requireNonNull(d, "Default cannot be null for: " + n);
        }
        if (c == null || c.equals(d)) {
          cfg.unset(section, sub, n);
        } else {
          if (isString(t)) {
            cfg.setString(section, sub, n, (String) c);
          } else if (isInteger(t)) {
            cfg.setInt(section, sub, n, (Integer) c);
          } else if (isLong(t)) {
            cfg.setLong(section, sub, n, (Long) c);
          } else if (isBoolean(t)) {
            cfg.setBoolean(section, sub, n, (Boolean) c);
          } else if (t.isEnum()) {
            cfg.setEnum(section, sub, n, (Enum<?>) c);
          } else if (isCollectionOrMap(t)) {
            // TODO(davido): accept closure passed in from caller
            continue;
          } else {
            throw new ConfigInvalidException("type is unknown: " + t.getName());
          }
        }
      }
    } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
      throw new ConfigInvalidException("cannot save values", e);
    }
  }

  /**
   * Load section by inspecting Java class attributes.
   *
   * <p>Config values are stored optimized: no default values are stored. The loading is performed
   * eagerly: all values are set.
   *
   * <p>Fields marked with final or transient modifiers are skipped.
   *
   * @param cfg config from which the values are loaded
   * @param section section
   * @param sub subsection
   * @param s instance of class in which the values are set
   * @param defaults instance of class with default values
   * @param i instance to merge during the load. When present, the boolean fields are not nullified
   *     when their values are false
   * @return loaded instance
   */
  @CanIgnoreReturnValue
  public static <T> T loadSection(Config cfg, String section, String sub, T s, T defaults, T i)
      throws ConfigInvalidException {
    try {
      for (Field f : s.getClass().getDeclaredFields()) {
        if (skipField(f)) {
          continue;
        }
        Class<?> t = f.getType();
        String n = f.getName();
        f.setAccessible(true);
        Object d = f.get(defaults);
        if (!isString(t) && !isCollectionOrMap(t)) {
          requireNonNull(d, "Default cannot be null for: " + n);
        }
        if (isString(t)) {
          String v = cfg.getString(section, sub, n);
          if (v == null) {
            v = (String) d;
          }
          f.set(s, v);
        } else if (isInteger(t)) {
          f.set(s, cfg.getInt(section, sub, n, (Integer) d));
        } else if (isLong(t)) {
          f.set(s, cfg.getLong(section, sub, n, (Long) d));
        } else if (isBoolean(t)) {
          // Sets the field if:
          // - 'cfg' value is 'true'.
          // - the default value is 'true'.
          // - i is set.
          boolean b = cfg.getBoolean(section, sub, n, (Boolean) d);
          if (b || (Boolean) d || i != null) {
            f.set(s, b);
          }
        } else if (t.isEnum()) {
          f.set(s, cfg.getEnum(section, sub, n, (Enum<?>) d));
        } else if (isCollectionOrMap(t)) {
          // TODO(davido): accept closure passed in from caller
          continue;
        } else {
          throw new ConfigInvalidException("type is unknown: " + t.getName());
        }
        if (i != null) {
          Object o = f.get(i);
          if (o != null) {
            f.set(s, o);
          }
        }
      }
    } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
      throw new ConfigInvalidException("cannot load values", e);
    }
    return s;
  }

  /**
   * Merges config by inspecting Java class attributes, similar to {@link #loadSection}.
   *
   * <p>Config values are stored optimized: no default values are stored. The loading is performed
   * eagerly: all values are set, except default boolean values.
   *
   * <p>Fields marked with final or transient modifiers are skipped.
   *
   * @param cfg config from which the values are loaded
   * @param s instance of class in which the values are set
   * @param defaults instance of class with default values
   * @return loaded instance
   */
  @CanIgnoreReturnValue
  public static <T> T mergeWithDefaults(T cfg, T s, T defaults) throws ConfigInvalidException {
    try {
      for (Field f : s.getClass().getDeclaredFields()) {
        if (skipField(f)) {
          continue;
        }
        Class<?> t = f.getType();
        String n = f.getName();
        f.setAccessible(true);

        Object val = f.get(cfg);
        if (val == null) {
          val = f.get(defaults);
          if (!isString(t) && !isCollectionOrMap(t)) {
            requireNonNull(val, "Default cannot be null for: " + n);
          }
        }
        if (!isBoolean(t) || (boolean) val || (Boolean) f.get(defaults)) {
          // To reproduce the same behavior as in the loadSection method above, values are
          // explicitly set for all types, except the boolean type. For the boolean type, the value
          // is set only in the following cases:
          // - 'cfg' value is 'true'.
          // - the default value is 'true'.
          // Otherwise, false values are omitted in the result object.
          f.set(s, val);
        }
      }
    } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
      throw new ConfigInvalidException("cannot load values", e);
    }
    return s;
  }

  /**
   * Update user config by applying the specified delta
   *
   * <p>As opposed to {@link com.google.gerrit.server.config.ConfigUtil#storeSection}, this method
   * does not unset a variable that are set to default, because it is expected that the input {@code
   * original} is the raw user config value (does not include the defaults)
   *
   * <p>To use this method with the proto config (see {@link
   * CachedPreferences#asUserPreferencesProto()}), the caller can first convert the proto to a java
   * class usign one of the {@link UserPreferencesConverter} classes.
   *
   * <p>Fields marked with final or transient modifiers are skipped.
   *
   * @param original the original current user config
   * @param updateDelta instance of class with config values that need to be uplied to the original
   *     config
   */
  @UsedAt(Project.GOOGLE)
  public static <T> void updatePreferences(T original, T updateDelta) throws IOException {
    try {
      for (Field f : updateDelta.getClass().getDeclaredFields()) {
        if (skipField(f)) {
          continue;
        }
        f.setAccessible(true);
        Object c = f.get(updateDelta);
        if (c != null) {
          f.set(original, c);
        }
      }
    } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
      throw new IOException("cannot apply delta the original config", e);
    }
  }

  public static boolean skipField(Field field) {
    int modifiers = field.getModifiers();
    return Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers);
  }

  private static boolean isCollectionOrMap(Class<?> t) {
    return Collection.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t);
  }

  private static boolean isString(Class<?> t) {
    return String.class == t;
  }

  private static boolean isBoolean(Class<?> t) {
    return Boolean.class == t || boolean.class == t;
  }

  private static boolean isLong(Class<?> t) {
    return Long.class == t || long.class == t;
  }

  private static boolean isInteger(Class<?> t) {
    return Integer.class == t || int.class == t;
  }

  private static boolean match(String a, String... cases) {
    for (String b : cases) {
      if (b != null && b.equalsIgnoreCase(a)) {
        return true;
      }
    }
    return false;
  }

  private static IllegalArgumentException notTimeUnit(
      String section, String subsection, String setting, String valueString, Throwable why) {
    return notTimeUnit(
        section
            + (subsection != null ? "." + subsection : "")
            + "."
            + setting
            + " = "
            + valueString,
        why);
  }

  private static IllegalArgumentException notTimeUnit(String val) {
    return new IllegalArgumentException("Invalid time unit value: " + val);
  }

  private static IllegalArgumentException notTimeUnit(String val, Throwable why) {
    return new IllegalArgumentException("Invalid time unit value: " + val, why);
  }

  private ConfigUtil() {}
}
