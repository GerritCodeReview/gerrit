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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountAgreement;
import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class AccountServiceImpl extends BaseServiceImplementation implements
    AccountService {
  public AccountServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void myAccount(final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      public Account run(ReviewDb db) throws OrmException {
        return db.accounts().get(RpcUtil.getAccountId());
      }
    });
  }

  public void changeDefaultContext(final short newSetting,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account a = db.accounts().get(RpcUtil.getAccountId());
        a.setDefaultContext(newSetting);
        db.accounts().update(Collections.singleton(a));
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
            .byAccount(RpcUtil.getAccountId()).toList()) {
          final Project p = db.projects().get(w.getProjectId());
          if (p == null) {
            db.accountProjectWatches().delete(Collections.singleton(w));
            continue;
          }
          r.add(new AccountProjectWatchInfo(w, p));
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
        final Project project =
            db.projects().get(new Project.NameKey(projectName));
        if (project == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountProjectWatch watch =
            new AccountProjectWatch(new AccountProjectWatch.Key(RpcUtil
                .getAccountId(), project.getId()));
        db.accountProjectWatches().insert(Collections.singleton(watch));
        return new AccountProjectWatchInfo(watch, project);
      }
    });
  }

  public void deleteProjectWatches(final Set<AccountProjectWatch.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = RpcUtil.getAccountId();
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
        i.load(RpcUtil.getAccountId(), db);
        return i;
      }
    });
  }

  public void enterAgreement(final ContributorAgreement.Id id,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final AccountAgreement a =
            new AccountAgreement(new AccountAgreement.Key(RpcUtil
                .getAccountId(), id));
        db.accountAgreements().insert(Collections.singleton(a));
        return VoidResult.INSTANCE;
      }
    });
  }
}
