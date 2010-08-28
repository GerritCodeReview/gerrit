// Copyright (C) 2010 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.*;

import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

public class ConfigUtilTest extends TestCase {
  public void testTimeUnit() {
    assertEquals(ms(2, MILLISECONDS), parse("2ms"));
    assertEquals(ms(200, MILLISECONDS), parse("200 milliseconds"));

    assertEquals(ms(2, SECONDS), parse("2s"));
    assertEquals(ms(231, SECONDS), parse("231sec"));
    assertEquals(ms(1, SECONDS), parse("1second"));
    assertEquals(ms(300, SECONDS), parse("300 seconds"));

    assertEquals(ms(2, MINUTES), parse("2m"));
    assertEquals(ms(2, MINUTES), parse("2min"));
    assertEquals(ms(1, MINUTES), parse("1 minute"));
    assertEquals(ms(10, MINUTES), parse("10 minutes"));

    assertEquals(ms(5, HOURS), parse("5h"));
    assertEquals(ms(5, HOURS), parse("5hr"));
    assertEquals(ms(1, HOURS), parse("1hour"));
    assertEquals(ms(48, HOURS), parse("48hours"));

    assertEquals(ms(5, HOURS), parse("5 h"));
    assertEquals(ms(5, HOURS), parse("5 hr"));
    assertEquals(ms(1, HOURS), parse("1 hour"));
    assertEquals(ms(48, HOURS), parse("48 hours"));
    assertEquals(ms(48, HOURS), parse("48 \t \r hours"));

    assertEquals(ms(4, DAYS), parse("4d"));
    assertEquals(ms(1, DAYS), parse("1day"));
    assertEquals(ms(14, DAYS), parse("14days"));

    assertEquals(ms(7, DAYS), parse("1w"));
    assertEquals(ms(7, DAYS), parse("1week"));
    assertEquals(ms(14, DAYS), parse("2w"));
    assertEquals(ms(14, DAYS), parse("2weeks"));

    assertEquals(ms(30, DAYS), parse("1mon"));
    assertEquals(ms(30, DAYS), parse("1month"));
    assertEquals(ms(60, DAYS), parse("2mon"));
    assertEquals(ms(60, DAYS), parse("2months"));

    assertEquals(ms(365, DAYS), parse("1y"));
    assertEquals(ms(365, DAYS), parse("1year"));
    assertEquals(ms(365 * 2, DAYS), parse("2years"));
  }

  private static long ms(int cnt, TimeUnit unit) {
    return MILLISECONDS.convert(cnt, unit);
  }

  private static long parse(String string) {
    return ConfigUtil.getTimeUnit(string, 1, MILLISECONDS);
  }
}
