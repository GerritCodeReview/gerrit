// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Creates a {@link FromAddressGenerator} from the {@link GerritServerConfig} */
@Singleton
public class FromAddressGeneratorProvider implements
    Provider<FromAddressGenerator> {
  private final FromAddressGenerator generator;

  @Inject
  FromAddressGeneratorProvider(@GerritServerConfig final Config cfg,
      @AnonymousCowardName final String anonymousCowardName,
      @GerritPersonIdent final PersonIdent myIdent,
      final AccountCache accountCache) {

    final String from = cfg.getString("sendemail", null, "from");
    final Address srvAddr = toAddress(myIdent);

    if (from == null || "MIXED".equalsIgnoreCase(from)) {
      ParameterizedString name = new ParameterizedString("${user} (Code Review)");
      generator =
          new PatternGen(srvAddr, accountCache, anonymousCowardName, name,
              srvAddr.email);

    } else if ("USER".equalsIgnoreCase(from)) {
      generator = new UserGen(accountCache, srvAddr);

    } else if ("SERVER".equalsIgnoreCase(from)) {
      generator = new ServerGen(srvAddr);

    } else {
      final Address a = Address.parse(from);
      final ParameterizedString name = a.name != null ? new ParameterizedString(a.name) : null;
      if (name == null || name.getParameterNames().isEmpty()) {
        generator = new ServerGen(a);
      } else {
        generator =
            new PatternGen(srvAddr, accountCache, anonymousCowardName, name,
                a.email);
      }
    }
  }

  private static Address toAddress(final PersonIdent myIdent) {
    return new Address(myIdent.getName(), myIdent.getEmailAddress());
  }

  @Override
  public FromAddressGenerator get() {
    return generator;
  }

  static final class UserGen implements FromAddressGenerator {
    private final AccountCache accountCache;
    private final Address srvAddr;

    UserGen(AccountCache accountCache, Address srvAddr) {
      this.accountCache = accountCache;
      this.srvAddr = srvAddr;
    }

    @Override
    public boolean isGenericAddress(Account.Id fromId) {
      return false;
    }

    @Override
    public Address from(final Account.Id fromId) {
      if (fromId != null) {
        Account a = accountCache.get(fromId).getAccount();
        String userEmail = a.getPreferredEmail();
        return new Address(
            a.getFullName(),
            userEmail != null ? userEmail : srvAddr.getEmail());
      }
      return srvAddr;
    }
  }

  static final class ServerGen implements FromAddressGenerator {
    private final Address srvAddr;

    ServerGen(Address srvAddr) {
      this.srvAddr = srvAddr;
    }

    @Override
    public boolean isGenericAddress(Account.Id fromId) {
      return true;
    }

    @Override
    public Address from(final Account.Id fromId) {
      return srvAddr;
    }
  }

  static final class PatternGen implements FromAddressGenerator {
    private final ParameterizedString senderEmailPattern;
    private final Address serverAddress;
    private final AccountCache accountCache;
    private final String anonymousCowardName;
    private final ParameterizedString namePattern;

    PatternGen(final Address serverAddress, final AccountCache accountCache,
        final String anonymousCowardName,
        final ParameterizedString namePattern, final String senderEmail) {
      this.senderEmailPattern = new ParameterizedString(senderEmail);
      this.serverAddress = serverAddress;
      this.accountCache = accountCache;
      this.anonymousCowardName = anonymousCowardName;
      this.namePattern = namePattern;
    }

    @Override
    public boolean isGenericAddress(Account.Id fromId) {
      return false;
    }

    @Override
    public Address from(final Account.Id fromId) {
      final String senderName;

      if (fromId != null) {
        final Account account = accountCache.get(fromId).getAccount();
        String fullName = account.getFullName();
        if (fullName == null || "".equals(fullName)) {
          fullName = anonymousCowardName;
        }
        senderName = namePattern.replace("user", fullName).toString();

      } else {
        senderName = serverAddress.name;
      }

      String senderEmail;
      if (senderEmailPattern.getParameterNames().isEmpty()) {
        senderEmail = senderEmailPattern.getRawPattern();
      } else {
        senderEmail = senderEmailPattern
            .replace("userHash", hashOf(senderName))
            .toString();
      }
      return new Address(senderName, senderEmail);
    }
  }

  private static String hashOf(String data) {
    try {
      MessageDigest hash = MessageDigest.getInstance("MD5");
      byte[] bytes = hash.digest(data.getBytes(UTF_8));
      return Base64.encodeBase64URLSafeString(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("No MD5 available", e);
    }
  }
}
