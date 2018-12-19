// Copyright (C) 2014 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.json;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.json.JavaSqlTimestampHelper.parseTimestamp;

import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JavaSqlTimestampHelperTest {
  private SimpleDateFormat format;
  private TimeZone systemTimeZone;

  @Before
  public void setUp() throws Exception {
    synchronized (TimeZone.class) {
      systemTimeZone = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("GMT-5:00"));
      format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");
    }
  }

  @After
  public void resetTimeZone() {
    TimeZone.setDefault(systemTimeZone);
  }

  @Test
  public void parseFullTimestamp() {
    assertThat(reformat("2006-01-02 20:04:05.789000000"))
        .isEqualTo("2006-01-02 15:04:05.789 -0500");
    assertThat(reformat("2006-01-02 20:04:05")).isEqualTo("2006-01-02 15:04:05.000 -0500");
  }

  @Test
  public void parseDateOnly() {
    assertThat(reformat("2006-01-02")).isEqualTo("2006-01-01 19:00:00.000 -0500");
  }

  @Test
  public void parseTimeZone() {
    assertThat(reformat("2006-01-02 15:04:05.789 -0100"))
        .isEqualTo("2006-01-02 11:04:05.789 -0500");
    assertThat(reformat("2006-01-02 15:04:05.789 -0000"))
        .isEqualTo("2006-01-02 10:04:05.789 -0500");
    assertThat(reformat("2006-01-02 15:04:05.789 +0100"))
        .isEqualTo("2006-01-02 09:04:05.789 -0500");
  }

  @Test
  public void parseInvalidTimestamps() {
    assertInvalid("2006-01-02-15:04:05.789000000");
    assertInvalid("2006-01-02T15:04:05.789000000");
    assertInvalid("15:04:05");
    assertInvalid("15:04:05.999000000");
  }

  private static void assertInvalid(String input) {
    try {
      parseTimestamp(input);
      assert_().fail("Expected IllegalArgumentException for: " + input);
    } catch (IllegalArgumentException e) {
      // Expected;
    }
  }

  private String reformat(String input) {
    return format.format(parseTimestamp(input));
  }
}
