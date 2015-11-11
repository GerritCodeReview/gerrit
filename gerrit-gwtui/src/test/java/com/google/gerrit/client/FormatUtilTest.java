// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client;

import static com.google.gerrit.client.FormatUtil.formatBytes;
import static com.google.gerrit.client.FormatUtil.formatPercentage;
import static org.junit.Assert.assertEquals;

import com.googlecode.gwt.test.GwtModule;
import com.googlecode.gwt.test.GwtTest;

import org.junit.Ignore;
import org.junit.Test;

@GwtModule("com.google.gerrit.GerritGwtUI")
@Ignore
public class FormatUtilTest extends GwtTest {
  @Test
  public void testFormatBytes() {
    assertEquals("+/- 0 B", formatBytes(0));
    assertEquals("+27 B", formatBytes(27));
    assertEquals("+999 B", formatBytes(999));
    assertEquals("+1000 B", formatBytes(1000));
    assertEquals("+1023 B", formatBytes(1023));
    assertEquals("+1.0 KiB", formatBytes(1024));
    assertEquals("+1.7 KiB", formatBytes(1728));
    assertEquals("+108.0 KiB", formatBytes(110592));
    assertEquals("+6.8 MiB", formatBytes(7077888));
    assertEquals("+432.0 MiB", formatBytes(452984832));
    assertEquals("+27.0 GiB", formatBytes(28991029248L));
    assertEquals("+1.7 TiB", formatBytes(1855425871872L));
    assertEquals("+8.0 EiB", formatBytes(9223372036854775807L));

    assertEquals("-27 B", formatBytes(-27));
    assertEquals("-1.7 MiB", formatBytes(-1728));
  }

  @Test
  public void testFormatPercentage() {
    assertEquals("N/A", formatPercentage(0, 10));
    assertEquals("0%", formatPercentage(100, 0));
    assertEquals("+25%", formatPercentage(100, 25));
    assertEquals("-25%", formatPercentage(100, -25));
    assertEquals("+50%", formatPercentage(100, 50));
    assertEquals("-50%", formatPercentage(100, -50));
    assertEquals("+100%", formatPercentage(100, 100));
    assertEquals("-100%", formatPercentage(100, -100));
    assertEquals("+500%", formatPercentage(100, 500));
  }
}
