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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.rules.PrologEnvironment;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.exceptions.SystemException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import java.io.IOException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Abstract Prolog predicate for a Git person identity of a change.
 *
 * <p>Checks that the terms that are provided as input to this Prolog predicate match a Git person
 * identity of the change (either author or committer).
 *
 * <p>The terms that are provided as input to this Prolog predicate are:
 *
 * <ul>
 *   <li>a user ID term that matches the account ID of the Git person identity
 *   <li>a string atom that matches the full name of the Git person identity
 *   <li>a string atom that matches the email of the Git person identity
 * </ul>
 */
abstract class AbstractCommitUserIdentityPredicate extends Predicate.P3 {
  private static final SymbolTerm user = SymbolTerm.intern("user", 1);
  private static final SymbolTerm anonymous = SymbolTerm.intern("anonymous");

  AbstractCommitUserIdentityPredicate(Term a1, Term a2, Term a3, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    cont = n;
  }

  protected Operation exec(Prolog engine, PersonIdent userId) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();
    Term a3 = arg3.dereference();

    Term idTerm;
    Term nameTerm = Prolog.Nil;
    Term emailTerm = Prolog.Nil;

    PrologEnvironment env = (PrologEnvironment) engine.control;
    Emails emails = env.getArgs().getEmails();
    Account.Id id = null;
    try {
      ImmutableSet<Account.Id> ids = emails.getAccountForExternal(userId.getEmailAddress());
      if (ids.size() == 1) {
        id = ids.iterator().next();
      }
    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }

    if (id == null) {
      idTerm = anonymous;
    } else {
      idTerm = new IntegerTerm(id.get());
    }

    String name = userId.getName();
    if (name != null && !name.equals("")) {
      nameTerm = SymbolTerm.create(name);
    }

    String email = userId.getEmailAddress();
    if (email != null && !email.equals("")) {
      emailTerm = SymbolTerm.create(email);
    }

    if (!a1.unify(new StructureTerm(user, idTerm), engine.trail)) {
      return engine.fail();
    }
    if (!a2.unify(nameTerm, engine.trail)) {
      return engine.fail();
    }
    if (!a3.unify(emailTerm, engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
