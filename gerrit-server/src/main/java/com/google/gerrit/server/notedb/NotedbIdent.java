package com.google.gerrit.server.notedb;

import com.google.common.base.Optional;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;

import org.eclipse.jgit.lib.PersonIdent;

import java.util.Date;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;

public interface NotedbIdent {
  static final String GERRIT_PLACEHOLDER_HOST = "gerrit";

  PersonIdent create(IdentifiedUser user, Date when);
  PersonIdent create(Account.Id user, Date when);
  Optional<Account.Id> parse(PersonIdent ident);

  @Singleton
  static class Impl implements NotedbIdent {
    private final AccountCache accountCache;
    private final String anonymousCowardName;
    private final TimeZone serverTimeZone;

    @Inject
    Impl(AccountCache accountCache,
        @AnonymousCowardName String anonymousCowardName,
        @GerritPersonIdent PersonIdent serverIdent) {
      this.accountCache = accountCache;
      this.anonymousCowardName = anonymousCowardName;
      this.serverTimeZone = serverIdent.getTimeZone();
    }

    @Override
    public PersonIdent create(Account.Id user, Date when) {
      return create(accountCache.get(user).getAccount(), when);
    }

    @Override
    public PersonIdent create(IdentifiedUser user, Date when) {
      return create(user.getAccount(), when);
    }

    private PersonIdent create(Account user, Date when) {
      AccountInfo a = new AccountInfo(user);
      String name = a.getName(anonymousCowardName);
      String email = user.getId().get() + "@" + GERRIT_PLACEHOLDER_HOST;
      return new PersonIdent(name, email, when, serverTimeZone);
    }

    @Override
    public Optional<Account.Id> parse(PersonIdent ident) {
      String email = ident.getEmailAddress();
      int at = email.indexOf('@');
      if (at >= 0) {
        String host = email.substring(at + 1, email.length());
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null && host.equals(GERRIT_PLACEHOLDER_HOST)) {
          return Optional.of(new Account.Id(id));
        }
      }
      return Optional.absent();
    }

  }
}
