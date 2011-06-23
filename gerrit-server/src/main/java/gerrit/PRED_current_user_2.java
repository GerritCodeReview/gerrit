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

package gerrit;

import static com.googlecode.prolog_cafe.lang.SymbolTerm.intern;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Provider;

import com.googlecode.prolog_cafe.lang.HashtableOfTerm;
import com.googlecode.prolog_cafe.lang.IllegalTypeException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.InternalException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Loads a CurrentUser object for a user identity.
 * <p>
 * Values are cached in the hash {@code current_user}, avoiding recreation
 * during a single evaluation.
 *
 * <pre>
 *   current_user(user(+AccountId), -CurrentUser).
 * </pre>
 */
class PRED_current_user_2 extends Predicate.P2 {
  private static final long serialVersionUID = 1L;
  private static final SymbolTerm user = intern("user", 1);
  private static final SymbolTerm anonymous = intern("anonymous");
  private static final SymbolTerm current_user = intern("current_user");

  PRED_current_user_2(Term a1, Term a2, Operation n) {
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

    HashtableOfTerm userHash = userHash(engine);
    Term userTerm = userHash.get(a1);
    if (userTerm != null && userTerm.isJavaObject()) {
      if (!(((JavaObjectTerm) userTerm).object() instanceof CurrentUser)) {
        userTerm = createUser(engine, a1, userHash);
      }
    } else {
      userTerm = createUser(engine, a1, userHash);
    }

    if (!a2.unify(userTerm, engine.trail)) {
      return engine.fail();
    }

    return cont;
  }

  public Term createUser(Prolog engine, Term key, HashtableOfTerm userHash) {
    if (!key.isStructure()
        || key.arity() != 1
        || !((StructureTerm) key).functor().equals(user)) {
      throw new IllegalTypeException(this, 1, "user(int)", key);
    }

    Term idTerm = key.arg(0);
    CurrentUser user;
    if (idTerm.isInteger()) {
      Account.Id accountId = new Account.Id(((IntegerTerm) idTerm).intValue());

      final ReviewDb db = StoredValues.REVIEW_DB.getOrNull(engine);
      IdentifiedUser.GenericFactory userFactory = userFactory(engine);
      if (db != null) {
        user = userFactory.create(new Provider<ReviewDb>() {
          public ReviewDb get() {
            return db;
          }
        }, accountId);
      } else {
        user = userFactory.create(accountId);
      }


    } else if (idTerm.equals(anonymous)) {
      user = anonymousUser(engine);

    } else {
      throw new IllegalTypeException(this, 1, "user(int)", key);
    }

    Term userTerm = new JavaObjectTerm(user);
    userHash.put(key, userTerm);
    return userTerm;
  }

  private static HashtableOfTerm userHash(Prolog engine) {
    Term userHash = engine.getHashManager().get(current_user);
    if (userHash == null) {
      HashtableOfTerm users = new HashtableOfTerm();
      engine.getHashManager().put(current_user, new JavaObjectTerm(userHash));
      return users;
    }

    if (userHash.isJavaObject()) {
      Object obj = ((JavaObjectTerm) userHash).object();
      if (obj instanceof HashtableOfTerm) {
        return (HashtableOfTerm) obj;
      }
    }

    throw new InternalException(current_user + " is not HashtableOfTerm");
  }

  private static AnonymousUser anonymousUser(Prolog engine) {
    PrologEnvironment env = (PrologEnvironment) engine.control;
    return env.getInjector().getInstance(AnonymousUser.class);
  }

  private static IdentifiedUser.GenericFactory userFactory(Prolog engine) {
    PrologEnvironment env = (PrologEnvironment) engine.control;
    return env.getInjector().getInstance(IdentifiedUser.GenericFactory.class);
  }
}
