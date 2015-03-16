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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gerrit.common.data.AccountService;
import com.google.gerrit.common.data.AgreementInfo;
import com.google.gerrit.common.errors.InvalidQueryException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

class AccountServiceImpl extends BaseServiceImplementation implements
    AccountService {
  private final Provider<IdentifiedUser> currentUser;
  private final AccountCache accountCache;
  private final ProjectControl.Factory projectControlFactory;
  private final AgreementInfoFactory.Factory agreementInfoFactory;
  private final ChangeQueryBuilder queryBuilder;

  @Inject
  AccountServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> identifiedUser,
      final AccountCache accountCache,
      final ProjectControl.Factory projectControlFactory,
      final AgreementInfoFactory.Factory agreementInfoFactory,
      final ChangeQueryBuilder queryBuilder) {
    super(schema, identifiedUser);
    this.currentUser = identifiedUser;
    this.accountCache = accountCache;
    this.projectControlFactory = projectControlFactory;
    this.agreementInfoFactory = agreementInfoFactory;
    this.queryBuilder = queryBuilder;
  }

  @Override
  public void myAccount(final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      @Override
      public Account run(ReviewDb db) throws OrmException {
        return db.accounts().get(currentUser.get().getAccountId());
      }
    });
  }

  @Override
  public void changePreferences(final AccountGeneralPreferences pref,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      @Override
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account a = db.accounts().get(getAccountId());
        if (a == null) {
          throw new Failure(new NoSuchEntityException());
        }
        a.setGeneralPreferences(pref);
        db.accounts().update(Collections.singleton(a));
        accountCache.evict(a.getId());
        return VoidResult.INSTANCE;
      }
    });
  }

  @Override
  public void changeDiffPreferences(final AccountDiffPreference diffPref,
      AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>(){
      @Override
      public VoidResult run(ReviewDb db) throws OrmException {
        if (!diffPref.getAccountId().equals(getAccountId())) {
          throw new IllegalArgumentException("diffPref.getAccountId() "
              + diffPref.getAccountId() + " doesn't match"
              + " the accountId of the signed in user " + getAccountId());
        }
        db.accountDiffPreferences().upsert(Collections.singleton(diffPref));
        return VoidResult.INSTANCE;
      }
    });
  }

  @Override
  public void myProjectWatch(
      final AsyncCallback<List<AccountProjectWatchInfo>> callback) {
    run(callback, new Action<List<AccountProjectWatchInfo>>() {
      @Override
      public List<AccountProjectWatchInfo> run(ReviewDb db) throws OrmException {
        List<AccountProjectWatchInfo> r = new ArrayList<>();

        for (final AccountProjectWatch w : db.accountProjectWatches()
            .byAccount(getAccountId()).toList()) {
          final ProjectControl ctl;
          try {
            ctl = projectControlFactory.validateFor(w.getProjectNameKey());
          } catch (NoSuchProjectException e) {
            db.accountProjectWatches().delete(Collections.singleton(w));
            continue;
          }
          r.add(new AccountProjectWatchInfo(w, ctl.getProject()));
        }
        Collections.sort(r, new Comparator<AccountProjectWatchInfo>() {
          @Override
          public int compare(final AccountProjectWatchInfo a,
              final AccountProjectWatchInfo b) {
            return a.getProject().getName().compareTo(b.getProject().getName());
          }
        });
        return r;
      }
    });
  }

  @Override
  public void addProjectWatch(final String projectName, final String filter,
      final AsyncCallback<AccountProjectWatchInfo> callback) {
    run(callback, new Action<AccountProjectWatchInfo>() {
      @Override
      public AccountProjectWatchInfo run(ReviewDb db) throws OrmException,
          NoSuchProjectException, InvalidQueryException {
        final Project.NameKey nameKey = new Project.NameKey(projectName);
        final ProjectControl ctl = projectControlFactory.validateFor(nameKey);

        if (filter != null) {
          try {
            queryBuilder.parse(filter);
          } catch (QueryParseException badFilter) {
            throw new InvalidQueryException(badFilter.getMessage(), filter);
          }
        }

        AccountProjectWatch watch =
            new AccountProjectWatch(new AccountProjectWatch.Key(
                ((IdentifiedUser) ctl.getCurrentUser()).getAccountId(),
                nameKey, filter));
        try {
          db.accountProjectWatches().insert(Collections.singleton(watch));
        } catch (OrmDuplicateKeyException alreadyHave) {
          watch = db.accountProjectWatches().get(watch.getKey());
        }
        return new AccountProjectWatchInfo(watch, ctl.getProject());
      }
    });
  }

  @Override
  public void updateProjectWatch(final AccountProjectWatch watch,
      final AsyncCallback<VoidResult> callback) {
    if (!getAccountId().equals(watch.getAccountId())) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    run(callback, new Action<VoidResult>() {
      @Override
      public VoidResult run(ReviewDb db) throws OrmException {
        db.accountProjectWatches().update(Collections.singleton(watch));
        return VoidResult.INSTANCE;
      }
    });
  }

  @Override
  public void deleteProjectWatches(final Set<AccountProjectWatch.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      @Override
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = getAccountId();
        for (final AccountProjectWatch.Key keyId : keys) {
          if (!me.equals(keyId.getParentKey())) {
            throw new Failure(new NoSuchEntityException());
          }
        }

        db.accountProjectWatches().deleteKeys(keys);
        return VoidResult.INSTANCE;
      }
    });
  }

  @Override
  public void myAgreements(final AsyncCallback<AgreementInfo> callback) {
    agreementInfoFactory.create().to(callback);
  }
}
