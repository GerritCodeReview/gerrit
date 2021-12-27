// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for text that can be persisted in data storage and should not contain user
 * identifiable information, e.g. {@link ChangeMessage} or {@link AttentionSetUpdate#reason}.
 */
@Singleton
public class AccountTemplateUtil {

  /**
   * Template to represent account in pseudonymized form in text, that might be persisted in data
   * storage.
   */
  public static final String ACCOUNT_TEMPLATE = "<GERRIT_ACCOUNT_%d>";

  public static final String ACCOUNT_TEMPLATE_REGEX = "<GERRIT_ACCOUNT_([0-9]+)>";

  public static final Pattern ACCOUNT_TEMPLATE_PATTERN = Pattern.compile(ACCOUNT_TEMPLATE_REGEX);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AccountCache accountCache;

  @Inject
  AccountTemplateUtil(AccountCache accountCache) {
    this.accountCache = accountCache;
  }

  /** Returns account ids that are used in text, that might contain {@link #ACCOUNT_TEMPLATE}. */
  public static ImmutableSet<Account.Id> parseTemplates(String textTemplate) {
    if (Strings.isNullOrEmpty(textTemplate)) {
      return ImmutableSet.of();
    }
    Matcher matcher = ACCOUNT_TEMPLATE_PATTERN.matcher(textTemplate);
    Set<Account.Id> accountsInTemplate = new HashSet<>();
    while (matcher.find()) {
      String accountId = matcher.group(1);
      Optional<Account.Id> parsedAccountId = Account.Id.tryParse(accountId);
      if (parsedAccountId.isPresent()) {
        accountsInTemplate.add(parsedAccountId.get());
      } else {
        logger.atFine().log("Failed to parse accountId from template %s", matcher.group());
      }
    }
    return ImmutableSet.copyOf(accountsInTemplate);
  }

  public static String getAccountTemplate(Account.Id accountId) {
    return String.format(ACCOUNT_TEMPLATE, accountId.get());
  }

  /** Builds user-readable text from text, that might contain {@link #ACCOUNT_TEMPLATE}. */
  public String replaceTemplates(String messageTemplate) {
    Matcher matcher = ACCOUNT_TEMPLATE_PATTERN.matcher(messageTemplate);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String accountId = matcher.group(1);
      String unrecognizedAccount = "Unrecognized Gerrit Account " + accountId;
      Optional<Account.Id> parsedAccountId = Account.Id.tryParse(accountId);
      if (parsedAccountId.isPresent()) {
        Optional<AccountState> account = accountCache.get(parsedAccountId.get());
        if (account.isPresent()) {
          matcher.appendReplacement(out, account.get().account().getNameEmail(unrecognizedAccount));
          continue;
        }
      }
      matcher.appendReplacement(out, unrecognizedAccount);
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
