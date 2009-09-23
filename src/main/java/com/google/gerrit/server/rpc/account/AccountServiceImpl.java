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

package com.google.gerrit.server.rpc.account;

import com.google.gerrit.client.account.AccountProjectWatchInfo;
import com.google.gerrit.client.account.AccountService;
import com.google.gerrit.client.account.AgreementInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.UserDb;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.Provider;

import scala.Tuple2;

import com.google.gimd.modification.DatabaseModification;
import com.google.gimd.Snapshot;
import com.google.gimd.javaglue.JFunction1;
import com.google.gimd.javaglue.ScalaUtil;

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
  private final UserDb userDb;

  @Inject
  AccountServiceImpl(final Provider<ReviewDb> schema, final UserDb userDb,
      final Provider<IdentifiedUser> identifiedUser,
      final AccountCache accountCache,
      final ProjectControl.Factory projectControlFactory,
      final AgreementInfoFactory.Factory agreementInfoFactory) {
    super(schema, identifiedUser);
    this.currentUser = identifiedUser;
    this.accountCache = accountCache;
    this.projectControlFactory = projectControlFactory;
    this.agreementInfoFactory = agreementInfoFactory;
    this.userDb = userDb;
  }

  public void myAccount(final AsyncCallback<Account> callback) {
    callback.onSuccess(currentUser.get().getAccount());
  }

  public void changePreferences(final AccountGeneralPreferences pref,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        Account account = userDb.modifyAndReturn(ScalaUtil.fun1(
          new JFunction1<Snapshot, scala.Tuple2<DatabaseModification, Account>>() {

            @Override
            public Tuple2<DatabaseModification, Account> apply(Snapshot snapshot)
              throws OrmException {
              final Account a = userDb.byOldId(getAccountId().get(), snapshot);
              a.setGeneralPreferences(pref);
              DatabaseModification modif = userDb.emptyModification();
              modif = userDb.updateAccount(a, modif, snapshot);
              return ScalaUtil.tuple2(modif, a);
            }
          }
        ));

        if (account == null) {
          throw new Failure(new NoSuchEntityException());
        }
        accountCache.evict(account.getId());
        return VoidResult.INSTANCE;
      }
    });
  }

  public void myProjectWatch(
      final AsyncCallback<List<AccountProjectWatchInfo>> callback) {
    run(callback, new Action<List<AccountProjectWatchInfo>>() {
      public List<AccountProjectWatchInfo> run(ReviewDb db) throws OrmException {
        final List<AccountProjectWatchInfo> r =
            new ArrayList<AccountProjectWatchInfo>();

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
          public int compare(final AccountProjectWatchInfo a,
              final AccountProjectWatchInfo b) {
            return a.getProject().getName().compareTo(b.getProject().getName());
          }
        });
        return r;
      }
    });
  }

  public void addProjectWatch(final String projectName,
      final AsyncCallback<AccountProjectWatchInfo> callback) {
    run(callback, new Action<AccountProjectWatchInfo>() {
      public AccountProjectWatchInfo run(ReviewDb db) throws OrmException,
          NoSuchProjectException {
        final Project.NameKey nameKey = new Project.NameKey(projectName);
        final ProjectControl ctl = projectControlFactory.validateFor(nameKey);

        final AccountProjectWatch watch =
            new AccountProjectWatch(
                new AccountProjectWatch.Key(((IdentifiedUser) ctl
                    .getCurrentUser()).getAccountId(), nameKey));
        db.accountProjectWatches().insert(Collections.singleton(watch));
        return new AccountProjectWatchInfo(watch, ctl.getProject());
      }
    });
  }

  public void updateProjectWatch(final AccountProjectWatch watch,
      final AsyncCallback<VoidResult> callback) {
    if (!getAccountId().equals(watch.getAccountId())) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException {
        db.accountProjectWatches().update(Collections.singleton(watch));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void deleteProjectWatches(final Set<AccountProjectWatch.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = getAccountId();
        for (final AccountProjectWatch.Key keyId : keys) {
          if (!me.equals(keyId.getParentKey()))
            throw new Failure(new NoSuchEntityException());
        }

        final List<AccountProjectWatch> k =
            db.accountProjectWatches().get(keys).toList();
        if (!k.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.accountProjectWatches().delete(k, txn);
          txn.commit();
        }

        return VoidResult.INSTANCE;
      }
    });
  }

  public void myAgreements(final AsyncCallback<AgreementInfo> callback) {
    agreementInfoFactory.create().to(callback);
  }
}
