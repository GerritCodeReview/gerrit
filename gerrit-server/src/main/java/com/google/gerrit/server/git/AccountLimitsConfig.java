// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.ConfigEnum;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;

public class AccountLimitsConfig {
  private static final Logger log = LoggerFactory.getLogger(AccountLimitsConfig.class);
  static final String GROUP_SECTION = "group";
  static final SectionParser<AccountLimitsConfig> KEY =
      new SectionParser<AccountLimitsConfig>() {
        @Override
        public AccountLimitsConfig parse(final Config cfg) {
          return new AccountLimitsConfig(cfg);
        }
      };

  public static class RateLimit {
    public Type getType() {
      return type;
    }

    public double getRatePerSecond() {
      return ratePerSecond;
    }

    public int getMaxBurstSeconds() {
      return maxBurstSeconds;
    }

    private Type type;
    private double ratePerSecond;
    private int maxBurstSeconds;

    public RateLimit(Type type, double ratePerSecond, int maxBurstSeconds) {
      this.type = type;
      this.ratePerSecond = ratePerSecond;
      this.maxBurstSeconds = maxBurstSeconds;
    }
  }

  public static enum Type implements ConfigEnum {
    UPLOADPACK;

    @Override
    public String toConfigValue() {
      return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matchConfigValue(String in) {
      return name().equalsIgnoreCase(in);
    }
  }

  private Table<Type, String, RateLimit> rateLimits;

  private AccountLimitsConfig(final Config c) {
    Set<String> groups = c.getSubsections(GROUP_SECTION);
    if (groups.size() == 0) {
      return;
    }
    rateLimits = ArrayTable.create(Arrays.asList(Type.values()), groups);
    for (String groupName : groups) {
      Type type = Type.UPLOADPACK;
      rateLimits.put(type, groupName, parseRateLimit(c, groupName, type, 60, 30));
    }
  }

  RateLimit parseRateLimit(
      Config c, String groupName, Type type, int defaultIntervalSeconds, int defaultStoreCount) {
    String name = type.toConfigValue();
    String value = c.getString(GROUP_SECTION, groupName, name).trim();
    if (value == null) {
      return defaultRateLimit(type, defaultIntervalSeconds, defaultStoreCount);
    }

    Matcher m = Pattern.compile("^interval\\s*(\\d+)\\s*(.*)\\s*store\\s*(\\d+)$").matcher(value);
    if (!m.matches()) {
      log.warn(
          "Invalid ''{}'' ratelimit configuration ''{}'', use default ratelimit",
          type.toConfigValue(),
          value);
      return defaultRateLimit(type, defaultIntervalSeconds, defaultStoreCount);
    }

    String digits = m.group(1);
    String unitName = m.group(2).trim();
    String storeCountString = m.group(3).trim();
    long storeCount = defaultStoreCount;
    try {
      storeCount = Long.parseLong(storeCountString);
    } catch (NumberFormatException e) {
      log.warn(
          "Invalid ''{}'' ratelimit store configuration ''{}'', use default store count ''{}''",
          type.toConfigValue(),
          storeCountString,
          storeCount);
    }

    TimeUnit inputUnit = TimeUnit.SECONDS;
    long intervalMillis = defaultIntervalSeconds * 1000;
    if (unitName.isEmpty()) {
      inputUnit = TimeUnit.SECONDS;
    } else if (match(unitName, "ms", "milliseconds")) {
      inputUnit = TimeUnit.MILLISECONDS;
    } else if (match(unitName, "s", "sec", "second", "seconds")) {
      inputUnit = TimeUnit.SECONDS;
    } else if (match(unitName, "m", "min", "minute", "minutes")) {
      inputUnit = TimeUnit.MINUTES;
    } else if (match(unitName, "h", "hr", "hour", "hours")) {
      inputUnit = TimeUnit.HOURS;
    } else {
      logNotTimeUnit(GROUP_SECTION, groupName, name, value);
    }
    try {
      intervalMillis = TimeUnit.MILLISECONDS.convert(Long.parseLong(digits), inputUnit);
    } catch (NumberFormatException nfe) {
      logNotTimeUnit(GROUP_SECTION, groupName, unitName, value);
    }

    double ratePerSecond = 1000.0D / intervalMillis;
    int maxBurstSeconds = (int) ((storeCount * intervalMillis) / 1000);
    return new RateLimit(type, ratePerSecond, maxBurstSeconds);
  }

  private static boolean match(final String a, final String... cases) {
    for (final String b : cases) {
      if (b != null && b.equalsIgnoreCase(a)) {
        return true;
      }
    }
    return false;
  }

  private void logNotTimeUnit(String section, String subsection, String name, String valueString) {
    if (subsection != null) {
      log.error(
          MessageFormat.format(
              JGitText.get().invalidTimeUnitValue3, section, subsection, name, valueString));
    }
    log.error(
        MessageFormat.format(JGitText.get().invalidTimeUnitValue2, section, name, valueString));
  }

  private RateLimit defaultRateLimit(Type type, int defaultIntervalSeconds, int defaultStoreCount) {
    return new RateLimit(
        type, 1.0D / defaultIntervalSeconds, defaultIntervalSeconds * defaultStoreCount);
  }

  /**
   * @param type type of rate limit
   * @return map of rate limits per group name
   */
  Optional<Map<String, RateLimit>> getRatelimits(Type type) {
    if (rateLimits != null) {
      return Optional.ofNullable(rateLimits.row(type));
    }
    return Optional.empty();
  }
}
