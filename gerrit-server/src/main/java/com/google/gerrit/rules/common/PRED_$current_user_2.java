// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules.common;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Provider;

import com.googlecode.prolog_cafe.lang.IllegalTypeException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Loads a CurrentUser object for a user identity.
 *
 * <pre>
 *   '$current_user'(+AccountId, -CurrentUser).
 * </pre>
 */
class PRED_$current_user_2 extends Predicate.P2 {
  PRED_$current_user_2(Term a1, Term a2, Operation n) {
    arg1 = a1;
    arg2 = a2;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();

    if (a1.isVariable()) {
      throw new PInstantiationException(this, 1);
    }
    if (!a1.isInteger()) {
      throw new IllegalTypeException(this, 1, "int", a1);
    }
    Account.Id accountId = new Account.Id(((IntegerTerm) a1).intValue());

    final ReviewDb db = StoredValues.REVIEW_DB.getOrNull(engine);
    IdentifiedUser.GenericFactory userFactory = userFactory(engine);
    CurrentUser user;
    if (db != null) {
      user = userFactory.create(new Provider<ReviewDb>() {
        public ReviewDb get() {
          return db;
        }
      }, accountId);
    } else {
      user = userFactory.create(accountId);
    }

    if (!a2.unify(new JavaObjectTerm(user), engine.trail)) {
      return engine.fail();
    }

    return cont;
  }

  private IdentifiedUser.GenericFactory userFactory(Prolog engine) {
    PrologEnvironment env = (PrologEnvironment) engine.control;
    return env.getInjector().getInstance(IdentifiedUser.GenericFactory.class);
  }
}
