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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.TimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;

public class OutputStreamQueryTest {

  @Before
  public void setUp() throws Exception {
    // Another test doesn't clean up properly. As result, a mismatch between TimeZone.getDefault()
    // and the system property 'user.timezone' is left behind. Resolve that mismatch so that this
    // test always runs with a clean state.
    TimeZone defaultTimeZone = TimeZone.getDefault();
    System.setProperty("user.timezone", defaultTimeZone.getID());
  }

  @Test
  public void timestampsAreFormattedAsWithJodaTime() {
    long epochSeconds =
        LocalDate.of(2017, Month.SEPTEMBER, 30)
            .atStartOfDay()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .getEpochSecond();

    String formattedDateTime = OutputStreamQuery.formatDateTime(epochSeconds);

    DateTimeFormatter jodaTimeFormatter =
        DateTimeFormat.forPattern(OutputStreamQuery.TIMESTAMP_FORMAT);
    String expectedDateTime = jodaTimeFormatter.print(epochSeconds * 1000L);
    assertThat(formattedDateTime).isEqualTo(expectedDateTime);
  }
}
