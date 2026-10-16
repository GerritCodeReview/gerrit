// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static java.util.stream.Collectors.joining;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;

/**
 * An SSH key approved for use by an {@link Account}.
 *
 * @param rawKey the key as it was read, kept so that it is not lost when written back if {@code
 *     key} could not be parsed
 */
public record AccountSshKey(
    Account.Id accountId,
    int seq,
    boolean valid,
    Optional<AuthorizedKeyEntry> key,
    String rawKey,
    Optional<Instant> expiryTime) {
  private static final String EXPIRY_TIME_OPTION = "expiry-time";
  private static final DateTimeFormatter DATE_TIME_MINUTES = formatter("uuuuMMddHHmm");
  private static final DateTimeFormatter DATE_TIME_SECONDS = formatter("uuuuMMddHHmmss");
  private static final DateTimeFormatter EXPIRY_TIME_FORMAT =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

  public AccountSshKey(Account.Id accountId, int seq, String sshPublicKey) {
    this(accountId, seq, sshPublicKey, true);
  }

  public AccountSshKey(Account.Id accountId, int seq, String sshPublicKey, boolean valid) {
    this(
        accountId,
        seq,
        valid && seq > 0,
        parse(sshPublicKey, Optional.empty()),
        stripOffNewLines(sshPublicKey));
  }

  public AccountSshKey(
      Account.Id accountId, int seq, String sshPublicKey, Optional<Instant> expiryTime) {
    this(accountId, seq, seq > 0, parse(sshPublicKey, expiryTime), stripOffNewLines(sshPublicKey));
  }

  public AccountSshKey {
    if (key.isPresent()) {
      // we were able to parse and no longer need to hold duplicated rawKey
      rawKey = "";
    }
  }

  private AccountSshKey(
      Account.Id accountId,
      int seq,
      boolean valid,
      Optional<AuthorizedKeyEntry> key,
      String rawKey) {
    this(accountId, seq, valid, key, rawKey, expiryTime(key));
  }

  /** Returns a copy of this key that is flagged as invalid. */
  public AccountSshKey asInvalid() {
    return new AccountSshKey(accountId, seq, false, key, rawKey, expiryTime);
  }

  /** Returns the key in the format of a line of {@code authorized_keys}, including options. */
  public String sshPublicKey() {
    return key.map(AccountSshKey::format).orElse(rawKey);
  }

  public String algorithm() {
    return key.map(AuthorizedKeyEntry::getKeyType).orElse("none");
  }

  public @Nullable String encodedKey() {
    return key.map(k -> BaseEncoding.base64().encode(k.getKeyData())).orElse(null);
  }

  public String comment() {
    return key.map(AuthorizedKeyEntry::getComment).orElse("");
  }

  public boolean isExpired() {
    return expiryTime.map(t -> TimeUtil.now().isAfter(t)).orElse(false);
  }

  private static String format(AuthorizedKeyEntry key) {
    StringBuilder b = new StringBuilder();
    String options =
        key.getLoginOptions().entrySet().stream()
            .map(AccountSshKey::formatOption)
            .collect(joining(","));
    if (!options.isEmpty()) {
      b.append(options).append(' ');
    }
    b.append(key.getKeyType()).append(' ').append(BaseEncoding.base64().encode(key.getKeyData()));
    if (!Strings.isNullOrEmpty(key.getComment())) {
      b.append(' ').append(key.getComment());
    }
    return b.toString();
  }

  private static Optional<Instant> expiryTime(Optional<AuthorizedKeyEntry> key) {
    return key.map(k -> k.getLoginOptions().get(EXPIRY_TIME_OPTION))
        .flatMap(AccountSshKey::parseExpiryTime);
  }

  /** Boolean options are parsed to {@code name=true}, and negated ones to {@code !name=false}. */
  private static String formatOption(Map.Entry<String, String> option) {
    String name = option.getKey();
    String value = option.getValue();
    if (value.equals("true") || (name.startsWith("!") && value.equals("false"))) {
      return name;
    }
    return name + "=\"" + value + "\"";
  }

  private static String stripOffNewLines(String s) {
    return s.replace("\n", "").replace("\r", "");
  }

  private static Optional<Instant> parseExpiryTime(String value) {
    boolean utc = value.endsWith("Z");
    String s = utc ? value.substring(0, value.length() - 1) : value;
    try {
      LocalDateTime t =
          switch (s.length()) {
            case 8 -> LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            case 12 -> LocalDateTime.parse(s, DATE_TIME_MINUTES);
            case 14 -> LocalDateTime.parse(s, DATE_TIME_SECONDS);
            default -> throw new DateTimeParseException("Invalid length", s, 0);
          };
      return Optional.of(t.atZone(utc ? ZoneOffset.UTC : ZoneId.systemDefault()).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private static Optional<AuthorizedKeyEntry> parse(
      String sshPublicKey, Optional<Instant> expiryTime) {
    String s = stripOffNewLines(sshPublicKey);
    if (s.isEmpty()) {
      return Optional.empty();
    }
    try {
      AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(s);
      expiryTime.ifPresent(
          t -> {
            Map<String, String> options = new TreeMap<>(entry.getLoginOptions());
            options.put(
                EXPIRY_TIME_OPTION, EXPIRY_TIME_FORMAT.format(t.truncatedTo(ChronoUnit.SECONDS)));
            entry.setLoginOptions(options);
          });
      return Optional.of(entry);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static DateTimeFormatter formatter(String pattern) {
    return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
  }
}
