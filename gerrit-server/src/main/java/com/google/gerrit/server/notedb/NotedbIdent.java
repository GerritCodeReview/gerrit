// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.CanonicalWebUrl;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

public interface NotedbIdent {
  PersonIdent create(IdentifiedUser user, Date when);
  PersonIdent create(Account.Id user, Date when);
  Optional<Account.Id> parse(PersonIdent ident);

  @Singleton
  static class Impl implements NotedbIdent {
    private static final Logger log = LoggerFactory.getLogger(NotedbIdent.class);
    private final AccountCache accountCache;
    private final AccountByEmailCache emailCache;
    private final IdentifiedUser.GenericFactory userFactory;
    private final TimeZone serverTimeZone;
    private final String serverHost;

    @Inject
    Impl(AccountCache accountCache,
        AccountByEmailCache emailCache,
        IdentifiedUser.GenericFactory userFactory,
        @GerritPersonIdent PersonIdent serverIdent,
        @Nullable @CanonicalWebUrl String canonicalUrl) {
      this.accountCache = accountCache;
      this.emailCache = emailCache;
      this.userFactory = userFactory;
      this.serverTimeZone = serverIdent.getTimeZone();

      String host;
      if (canonicalUrl != null) {
        try {
          host = new URL(canonicalUrl).getHost();
        } catch (MalformedURLException e) {
          host = SystemReader.getInstance().getHostname();
        }
      } else {
        host = SystemReader.getInstance().getHostname();
      }
      serverHost = host;
    }

    @Override
    public PersonIdent create(Account.Id user, Date when) {
      return create(userFactory.create(user), when);
    }

    @Override
    public PersonIdent create(IdentifiedUser user, Date when) {
      return user.newCommitterIdent(when, serverTimeZone);
    }

    @Override
    public Optional<Account.Id> parse(PersonIdent ident) {
      String e = ident.getEmailAddress();
      Set<Account.Id> ids = emailCache.get(e);
      if (ids.size() == 1) {
        return Optional.of(Iterables.getOnlyElement(ids));
      }
      if (ids.size() > 1) {
        return pickOnlyActive(e, ids);
      }
      if (ids.isEmpty()) {
        return byUsernameOrId(e);
      }
      return Optional.absent();
    }

    private Optional<Account.Id> pickOnlyActive(String e, Set<Account.Id> ids) {
      log.warn("email {} has {} matching accounts", e, ids.size());
      List<AccountState> matches = new ArrayList<>(ids.size());
      for (Account.Id id : ids) {
        AccountState s = accountCache.get(id);
        if (s.getAccount().isActive()) {
          matches.add(s);
        }
      }
      if (matches.size() == 1) {
        return Optional.of(matches.get(0).getAccount().getId());
      }
      return Optional.absent();
    }

    private Optional<Account.Id> byUsernameOrId(String email) {
      int a = email.indexOf('@');
      if (a > 0 && serverHost.equals(email.substring(a + 1))) {
        String user = email.substring(0, a);
        AccountState s = accountCache.getByUsername(user);
        if (s != null) {
          return Optional.of(s.getAccount().getId());
        }

        Pattern p = Pattern.compile("account-([1-9]\\d*)");
        Matcher m = p.matcher(user);
        if (m.matches()) {
          int i = Integer.parseInt(m.group(1), 10);
          return Optional.of(new Account.Id(i));
        }
      }
      return Optional.absent();
    }
  }
}
