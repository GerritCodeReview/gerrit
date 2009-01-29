// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.account;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class AccountServiceImpl extends BaseServiceImplementation implements
    AccountService {
  public void myAccount(final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      public Account run(ReviewDb db) throws Failure {
        final Account a =
            Common.getAccountCache().get(Common.getAccountId(), db);
        if (a == null) {
          throw new Failure(new NoSuchEntityException());
        }
        return a;
      }
    });
  }

  public void changeShowSiteHeader(final boolean newSetting,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account a = db.accounts().get(Common.getAccountId());
        a.setShowSiteHeader(newSetting);
        db.accounts().update(Collections.singleton(a));
        Common.getAccountCache().invalidate(a.getId());
        return VoidResult.INSTANCE;
      }
    });
  }

  public void changeDefaultContext(final short newSetting,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account a = db.accounts().get(Common.getAccountId());
        a.setDefaultContext(newSetting);
        db.accounts().update(Collections.singleton(a));
        Common.getAccountCache().invalidate(a.getId());
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
            .byAccount(Common.getAccountId()).toList()) {
          final ProjectCache.Entry project =
              Common.getProjectCache().get(w.getProjectId());
          if (project == null) {
            db.accountProjectWatches().delete(Collections.singleton(w));
            continue;
          }
          r.add(new AccountProjectWatchInfo(w, project.getProject()));
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
          Failure {
        final ProjectCache.Entry project =
            Common.getProjectCache().get(new Project.NameKey(projectName));
        if (project == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountProjectWatch watch =
            new AccountProjectWatch(new AccountProjectWatch.Key(Common
                .getAccountId(), project.getProject().getId()));
        db.accountProjectWatches().insert(Collections.singleton(watch));
        return new AccountProjectWatchInfo(watch, project.getProject());
      }
    });
  }

  public void updateProjectWatch(final AccountProjectWatch watch,
      final AsyncCallback<VoidResult> callback) {
    if (!Common.getAccountId().equals(watch.getAccountId())) {
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
        final Account.Id me = Common.getAccountId();
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
    run(callback, new Action<AgreementInfo>() {
      public AgreementInfo run(final ReviewDb db) throws OrmException {
        final AgreementInfo i = new AgreementInfo();
        i.load(Common.getAccountId(), db);
        return i;
      }
    });
  }
}
