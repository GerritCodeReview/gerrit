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

import com.google.gerrit.extensions.client.Theme;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ConfigUtilTest {
  private static final String SECT = "foo";
  private static final String SUB = "bar";

  static class SectionInfo {
    public static final String CONSTANT = "42";
    public transient String missing;
    public int i;
    public Integer ii;
    public Integer id;
    public long l;
    public Long ll;
    public Long ld;
    public boolean b;
    public Boolean bb;
    public Boolean bd;
    public String s;
    public String sd;
    public String nd;
    public Theme t;
    public Theme td;
    public List<String> list;
    public Map<String, String> map;

    static SectionInfo defaults() {
      SectionInfo i = new SectionInfo();
      i.i = 1;
      i.ii = 2;
      i.id = 3;
      i.l = 4L;
      i.ll = 5L;
      i.ld = 6L;
      i.b = true;
      i.bb = false;
      i.bd = true;
      i.s = "foo";
      i.sd = "bar";
      // i.nd = null; // Don't need to explicitly set it; it's null by default
      i.t = Theme.DEFAULT;
      i.td = Theme.DEFAULT;
      return i;
    }
  }

  @Test
  public void testStoreLoadSection() throws Exception {
    SectionInfo d = SectionInfo.defaults();
    SectionInfo in = new SectionInfo();
    in.missing = "42";
    in.i = 1;
    in.ii = 43;
    in.l = 4L;
    in.ll = -43L;
    in.b = false;
    in.bb = true;
    in.bd = false;
    in.s = "baz";
    in.t = Theme.MIDNIGHT;

    Config cfg = new Config();
    ConfigUtil.storeSection(cfg, SECT, SUB, in, d);

    assertThat(cfg.getString(SECT, SUB, "CONSTANT")).isNull();
    assertThat(cfg.getString(SECT, SUB, "missing")).isNull();
    assertThat(cfg.getBoolean(SECT, SUB, "b", false)).isEqualTo(in.b);
    assertThat(cfg.getBoolean(SECT, SUB, "bb", false)).isEqualTo(in.bb);
    assertThat(cfg.getInt(SECT, SUB, "i", 0)).isEqualTo(0);
    assertThat(cfg.getInt(SECT, SUB, "ii", 0)).isEqualTo(in.ii);
    assertThat(cfg.getLong(SECT, SUB, "l", 0L)).isEqualTo(0L);
    assertThat(cfg.getLong(SECT, SUB, "ll", 0L)).isEqualTo(in.ll);
    assertThat(cfg.getString(SECT, SUB, "s")).isEqualTo(in.s);
    assertThat(cfg.getString(SECT, SUB, "sd")).isNull();
    assertThat(cfg.getString(SECT, SUB, "nd")).isNull();

    SectionInfo out = new SectionInfo();
    ConfigUtil.loadSection(cfg, SECT, SUB, out, d, null);
    assertThat(out.i).isEqualTo(in.i);
    assertThat(out.ii).isEqualTo(in.ii);
    assertThat(out.id).isEqualTo(d.id);
    assertThat(out.l).isEqualTo(in.l);
    assertThat(out.ll).isEqualTo(in.ll);
    assertThat(out.ld).isEqualTo(d.ld);
    assertThat(out.b).isEqualTo(in.b);
    assertThat(out.bb).isEqualTo(in.bb);
    assertThat(out.bd).isNull();
    assertThat(out.s).isEqualTo(in.s);
    assertThat(out.sd).isEqualTo(d.sd);
    assertThat(out.nd).isNull();
    assertThat(out.t).isEqualTo(in.t);
    assertThat(out.td).isEqualTo(d.td);
  }

  @Test
  public void mergeSection() throws Exception {
    SectionInfo d = SectionInfo.defaults();
    Config cfg = new Config();
    ConfigUtil.storeSection(cfg, SECT, SUB, d, d);

    SectionInfo in = new SectionInfo();
    in.i = 42;

    SectionInfo out = new SectionInfo();
    ConfigUtil.loadSection(cfg, SECT, SUB, out, d, in);
    // Check original values preserved
    assertThat(out.id).isEqualTo(d.id);
    // Check merged values
    assertThat(out.i).isEqualTo(in.i);
    // Check that boolean attribute not nullified
    assertThat(out.bb).isFalse();
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

  private static long ms(int cnt, TimeUnit unit) {
    return MILLISECONDS.convert(cnt, unit);
  }

  private static long parse(String string) {
    return ConfigUtil.getTimeUnit(string, 1, MILLISECONDS);
  }
}
