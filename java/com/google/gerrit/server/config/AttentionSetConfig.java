// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
public class AttentionSetConfig {
  private static final String SECTION = "attentionSet";
  private static final String KEY_READD_AFTER = "readdOwnerAfter";
  private static final String KEY_READD_MESSAGE = "readdOwnerMessage";
  private static final String KEY_MAIL_SENDER = "mailSender";
  private static final String DEFAULT_READD_MESSAGE =
      "Owner readded to attention-set due to inactivity, see "
          + "${URL}\n"
          + "\n"
          + "If you do not want to be readded to the attention-set when the timer has counted down,"
          + " set this change as WIP or private.";

  private final DynamicItem<UrlFormatter> urlFormatter;
  private final Optional<Schedule> schedule;
  private final long readdAfter;
  private final String readdMessage;
  private final Optional<String> mailSender;

  @Inject
  AttentionSetConfig(@GerritServerConfig Config cfg, DynamicItem<UrlFormatter> urlFormatter) {
    this.urlFormatter = urlFormatter;
    schedule = ScheduleConfig.createSchedule(cfg, SECTION);
    readdAfter = readReaddAfter(cfg);
    readdMessage = readReaddMessage(cfg);
    mailSender = readMailSender(cfg);
  }

  private long readReaddAfter(Config cfg) {
    long readdAfter =
        ConfigUtil.getTimeUnit(cfg, SECTION, null, KEY_READD_AFTER, 0, TimeUnit.MILLISECONDS);
    return readdAfter >= 0 ? readdAfter : 0;
  }

  private String readReaddMessage(Config cfg) {
    String readdMessage = cfg.getString(SECTION, null, KEY_READD_MESSAGE);
    return Strings.isNullOrEmpty(readdMessage) ? DEFAULT_READD_MESSAGE : readdMessage;
  }

  private Optional<String> readMailSender(Config cfg) {
    String mailSender = cfg.getString(SECTION, null, KEY_MAIL_SENDER);
    if (mailSender == null) {
      return Optional.empty();
    }
    return Optional.of(mailSender);
  }

  public Optional<Schedule> getSchedule() {
    return schedule;
  }

  public long getReaddAfter() {
    return readdAfter;
  }

  public String getReaddMessage() {
    String docUrl =
        urlFormatter.get().getDocUrl("user-attention-set.html", "auto-readd-owner").orElse("");
    return docUrl.isEmpty() ? readdMessage : readdMessage.replace("${URL}", docUrl);
  }

  public Optional<String> getMailSender() {
    return mailSender;
  }
}
