// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.i18n;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

import java.util.Locale;
import java.util.ResourceBundle;

@Singleton
public class I18nImpl implements I18n {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Config cfg;

  static final String SECTION_I18N = "i18n";
  static final String DEFAULT_LANGUAGE = "en";
  static final String DEFAULT_COUNTRY = "US";
  static final String BUNDLE_NAME = "com.google.gerrit.server.gerrit";

  static final String KEY_LANGUAGE = "language";
  static final String KEY_COUNTRY = "country";

  final ResourceBundle messages;

  @Inject
  I18nImpl(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    String language = cfg.getString(SECTION_I18N, null, KEY_LANGUAGE);
    String country = cfg.getString(SECTION_I18N, null, KEY_COUNTRY);
    if (language == null) {
      language = "en";
    }
    if (country == null) {
      country = "US";
    }
    messages = ResourceBundle.getBundle(BUNDLE_NAME, new Locale(language, country));
    logger.atInfo().log("I18n language: %s, country %s", language, country);
  }

  @Override
  public String getText(String text) {
    return messages.getString(text);
  }
}
