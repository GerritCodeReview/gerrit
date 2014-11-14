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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.extensions.common.Theme;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ConfigUtilTest {
  private final static String SECT = "foo";
  private final static String SUB = "bar";

  static class SectionInfo {
    public int i;
    public Integer ii;
    public long l;
    public Long ll;
    public boolean b;
    public Boolean bb;
    public String s;
    public Theme t;

    public SectionInfo() {
      i = 1;
      ii = 2;
      l = 3L;
      ll = 4L;
      b = true;
      bb = false;
      s = "";
      t = Theme.DEFAULT;
    }
  }

  @Test
  public void testTimeUnit() {
    assertEquals(ms(0, MILLISECONDS), parse("0"));
    assertEquals(ms(2, MILLISECONDS), parse("2ms"));
    assertEquals(ms(200, MILLISECONDS), parse("200 milliseconds"));

    assertEquals(ms(0, SECONDS), parse("0s"));
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

  @Test
  public void testStoreLoadSection() throws Exception {
    SectionInfo in = new SectionInfo();
    in.i = 42;
    in.ii = 43;
    in.l = -42L;
    in.ll = -43L;
    in.b = true;
    in.bb = false;
    in.s = "baz";
    in.t = Theme.MIDNIGHT;

    Config cfg = new Config();
    ConfigUtil.storeSection(cfg, SECT, SUB, in);

    assertThat(cfg.getBoolean(SECT, SUB, "b", false)).isEqualTo(in.b);
    assertThat(cfg.getBoolean(SECT, SUB, "bb", false)).isEqualTo(in.bb);
    assertThat(cfg.getInt(SECT, SUB, "i", 0)).isEqualTo(in.i);
    assertThat(cfg.getInt(SECT, SUB, "ii", 0)).isEqualTo(in.ii);
    assertThat(cfg.getLong(SECT, SUB, "l", 0L)).isEqualTo(in.l);
    assertThat(cfg.getLong(SECT, SUB, "ll", 0L)).isEqualTo(in.ll);
    assertThat(cfg.getString(SECT, SUB, "s")).isEqualTo(in.s);

    SectionInfo out = new SectionInfo();
    ConfigUtil.loadSection(cfg, SECT, SUB, out);
    assertThat(out.i).isEqualTo(in.i);
    assertThat(out.ii).isEqualTo(in.ii);
    assertThat(out.l).isEqualTo(in.l);
    assertThat(out.ll).isEqualTo(in.ll);
    assertThat(out.b).isEqualTo(in.b);
    assertThat(out.bb).isEqualTo(in.bb);
    assertThat(out.s).isEqualTo(in.s);
    assertThat(out.t).isEqualTo(in.t);
  }

  private static long ms(int cnt, TimeUnit unit) {
    return MILLISECONDS.convert(cnt, unit);
  }

  private static long parse(String string) {
    return ConfigUtil.getTimeUnit(string, 1, MILLISECONDS);
  }
}
