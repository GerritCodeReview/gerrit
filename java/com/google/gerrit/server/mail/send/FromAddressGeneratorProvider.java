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

package com.google.gerrit.server.mail.send;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.config.AnonymousCowardName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.MailUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

/** Creates a {@link FromAddressGenerator} from the {@link GerritServerConfig} */
@Singleton
public class FromAddressGeneratorProvider implements Provider<FromAddressGenerator> {
  private final FromAddressGenerator generator;

  @Inject
  FromAddressGeneratorProvider(
      @GerritServerConfig Config cfg,
      @AnonymousCowardName String anonymousCowardName,
      @GerritPersonIdent PersonIdent myIdent,
      AccountCache accountCache) {
    final String from = cfg.getString("sendemail", null, "from");
    final Address srvAddr = toAddress(myIdent);

    if (from == null || "MIXED".equalsIgnoreCase(from)) {
      ParameterizedString name = new ParameterizedString("${user} (Code Review)");
      generator =
          new PatternGen(srvAddr, accountCache, anonymousCowardName, name, srvAddr.getEmail());
    } else if ("USER".equalsIgnoreCase(from)) {
      String[] domains = cfg.getStringList("sendemail", null, "allowedDomain");
      Pattern domainPattern = MailUtil.glob(domains);
      ParameterizedString namePattern = new ParameterizedString("${user} (Code Review)");
      generator =
          new UserGen(accountCache, domainPattern, anonymousCowardName, namePattern, srvAddr);
    } else if ("SERVER".equalsIgnoreCase(from)) {
      generator = new ServerGen(srvAddr);
    } else {
      final Address a = Address.parse(from);
      final ParameterizedString name =
          a.getName() != null ? new ParameterizedString(a.getName()) : null;
      if (name == null || name.getParameterNames().isEmpty()) {
        generator = new ServerGen(a);
      } else {
        generator = new PatternGen(srvAddr, accountCache, anonymousCowardName, name, a.getEmail());
      }
    }
  }

  private static Address toAddress(PersonIdent myIdent) {
    return new Address(myIdent.getName(), myIdent.getEmailAddress());
  }

  @Override
  public FromAddressGenerator get() {
    return generator;
  }

  static final class UserGen implements FromAddressGenerator {
    private final AccountCache accountCache;
    private final Pattern domainPattern;
    private final String anonymousCowardName;
    private final ParameterizedString nameRewriteTmpl;
    private final Address serverAddress;

    /**
     * From address generator for USER mode
     *
     * @param accountCache get user account from id
     * @param domainPattern allowed user domain pattern that Gerrit can send as the user
     * @param anonymousCowardName name used when user's full name is missing
     * @param nameRewriteTmpl name template used for rewriting the sender's name when Gerrit can not
     *     send as the user
     * @param serverAddress serverAddress.name is used when fromId is null and serverAddress.email
     *     is used when Gerrit can not send as the user
     */
    UserGen(
        AccountCache accountCache,
        Pattern domainPattern,
        String anonymousCowardName,
        ParameterizedString nameRewriteTmpl,
        Address serverAddress) {
      this.accountCache = accountCache;
      this.domainPattern = domainPattern;
      this.anonymousCowardName = anonymousCowardName;
      this.nameRewriteTmpl = nameRewriteTmpl;
      this.serverAddress = serverAddress;
    }

    @Override
    public boolean isGenericAddress(Account.Id fromId) {
      return false;
    }

    @Override
    public Address from(Account.Id fromId) {
      String senderName;
      if (fromId != null) {
        Optional<Account> a = accountCache.get(fromId).map(AccountState::getAccount);
        String fullName = a.map(Account::getFullName).orElse(null);
        String userEmail = a.map(Account::getPreferredEmail).orElse(null);
        if (canRelay(userEmail)) {
          return new Address(fullName, userEmail);
        }

        if (fullName == null || "".equals(fullName.trim())) {
          fullName = anonymousCowardName;
        }
        senderName = nameRewriteTmpl.replace("user", fullName).toString();
      } else {
        senderName = serverAddress.getName();
      }

      String senderEmail;
      ParameterizedString senderEmailPattern = new ParameterizedString(serverAddress.getEmail());
      if (senderEmailPattern.getParameterNames().isEmpty()) {
        senderEmail = senderEmailPattern.getRawPattern();
      } else {
        senderEmail = senderEmailPattern.replace("userHash", hashOf(senderName)).toString();
      }
      return new Address(senderName, senderEmail);
    }

    /** check if Gerrit is allowed to send from {@code userEmail}. */
    private boolean canRelay(String userEmail) {
      if (userEmail != null) {
        int index = userEmail.indexOf('@');
        if (index > 0 && index < userEmail.length() - 1) {
          return domainPattern.matcher(userEmail.substring(index + 1)).matches();
        }
      }
      return false;
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
    public Address from(Account.Id fromId) {
      return srvAddr;
    }
  }

  static final class PatternGen implements FromAddressGenerator {
    private final ParameterizedString senderEmailPattern;
    private final Address serverAddress;
    private final AccountCache accountCache;
    private final String anonymousCowardName;
    private final ParameterizedString namePattern;

    PatternGen(
        final Address serverAddress,
        final AccountCache accountCache,
        final String anonymousCowardName,
        final ParameterizedString namePattern,
        final String senderEmail) {
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
    public Address from(Account.Id fromId) {
      final String senderName;

      if (fromId != null) {
        String fullName =
            accountCache.get(fromId).map(a -> a.getAccount().getFullName()).orElse(null);
        if (fullName == null || "".equals(fullName)) {
          fullName = anonymousCowardName;
        }
        senderName = namePattern.replace("user", fullName).toString();

      } else {
        senderName = serverAddress.getName();
      }

      String senderEmail;
      if (senderEmailPattern.getParameterNames().isEmpty()) {
        senderEmail = senderEmailPattern.getRawPattern();
      } else {
        senderEmail = senderEmailPattern.replace("userHash", hashOf(senderName)).toString();
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
