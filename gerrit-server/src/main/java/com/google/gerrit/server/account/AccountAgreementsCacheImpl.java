package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.List;

@Singleton
public class AccountAgreementsCacheImpl implements AccountAgreementsCache {
  private static final String BY_ACCOUNT_ID = "account_agreements";

  protected static class AccountAgreementsList {
    @Column(id = 1)
    protected List<AccountAgreement> list;

    protected AccountAgreementsList() {
    }

    public AccountAgreementsList(List<AccountAgreement> list) {
      this.list = list;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountAgreementsList>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, AccountAgreementsList>>() {};
        core(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        bind(AccountAgreementsCacheImpl.class);
        bind(AccountAgreementsCache.class).to(AccountAgreementsCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Id, AccountAgreementsList> byAccountId;

  @Inject
  AccountAgreementsCacheImpl(
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, AccountAgreementsList> byAccountId) {
    this.byAccountId = byAccountId;
  }

  @Override
  public List<AccountAgreement> byAccount(Account.Id id) {
    return byAccountId.get(id).list;
  }

  @Override
  public void evict(AccountAgreement.Key key) {
    byAccountId.remove(key.getParentKey());
  }

  static class ByAccountIdLoader extends
      EntryCreator<Account.Id, AccountAgreementsList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByAccountIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountAgreementsList createEntry(Account.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountAgreementsList(db.accountAgreements().byAccount(id)
            .toList());
      } finally {
        db.close();
      }
    }
  }
}
