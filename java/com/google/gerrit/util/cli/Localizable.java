// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.util.cli;

import java.util.Locale;

public class Localizable implements org.kohsuke.args4j.Localizable {
  private final String format;

  @Override
  public String formatWithLocale(Locale locale, Object... args) {
    return String.format(locale, format, args);
  }

  @Override
  public String format(Object... args) {
    return String.format(format, args);
  }

  private Localizable(String format) {
    this.format = format;
  }

  public static Localizable localizable(String format) {
    return new Localizable(format);
  }
}
