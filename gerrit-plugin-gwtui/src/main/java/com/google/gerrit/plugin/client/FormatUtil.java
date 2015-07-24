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

package com.google.gerrit.plugin.client;

import com.google.gerrit.client.DateFormatter;

import java.util.Date;

public class FormatUtil {
  /** Format a date using a really short format. */
  public static String shortFormat(Date dt) {
    return createDateFormatter().shortFormat(dt);
  }

  /** Format a date using a really short format. */
  public static String shortFormatDayTime(Date dt) {
    return createDateFormatter().shortFormatDayTime(dt);
  }

  /** Format a date using the locale's medium length format. */
  public static String mediumFormat(Date dt) {
    return createDateFormatter().mediumFormat(dt);
  }

  private static DateFormatter createDateFormatter() {
    return new DateFormatter(Plugin.get().getUserPreferences());
  }
}
