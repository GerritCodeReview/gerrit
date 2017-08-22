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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.googlecode.prolog_cafe.exceptions.IllegalTypeException;
import com.googlecode.prolog_cafe.exceptions.PInstantiationException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import java.util.Map;

/**
 * Loads a CurrentUser object for a user identity.
 *
 * <p>Values are cached in the hash {@code current_user}, avoiding recreation during a single
 * evaluation.
 *
 * <pre>
 *   current_user(user(+AccountId), -CurrentUser).
 * </pre>
 */
class PRED_current_user_2 extends Predicate.P2 {
  private static final SymbolTerm user = intern("user", 1);
  private static final SymbolTerm anonymous = intern("anonymous");

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

    if (a1 instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }

    if (!a2.unify(createUser(engine, a1), engine.trail)) {
      return engine.fail();
    }

    return cont;
  }

  public Term createUser(Prolog engine, Term key) {
    if (!(key instanceof StructureTerm)
        || key.arity() != 1
        || !((StructureTerm) key).functor().equals(user)) {
      throw new IllegalTypeException(this, 1, "user(int)", key);
    }

    Term idTerm = key.arg(0);
    CurrentUser user;
    if (idTerm instanceof IntegerTerm) {
      Map<Account.Id, IdentifiedUser> cache = StoredValues.USERS.get(engine);
      Account.Id accountId = new Account.Id(((IntegerTerm) idTerm).intValue());
      user = cache.get(accountId);
      if (user == null) {
        IdentifiedUser.GenericFactory userFactory = userFactory(engine);
        IdentifiedUser who = userFactory.create(accountId);
        cache.put(accountId, who);
        user = who;
      }

    } else if (idTerm.equals(anonymous)) {
      user = StoredValues.ANONYMOUS_USER.get(engine);

    } else {
      throw new IllegalTypeException(this, 1, "user(int)", key);
    }

    return new JavaObjectTerm(user);
  }

  private static IdentifiedUser.GenericFactory userFactory(Prolog engine) {
    return ((PrologEnvironment) engine.control).getArgs().getUserFactory();
  }
}
