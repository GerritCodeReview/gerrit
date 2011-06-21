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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;

import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.JavaException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

class PRED_commit_author_3 extends Predicate.P3 {
  private static final long serialVersionUID = 1L;
  private static final SymbolTerm user = SymbolTerm.intern("user", 1);

  PRED_commit_author_3(Term a1, Term a2, Term a3, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();
    Term a3 = arg3.dereference();

    PrologEnvironment env = (PrologEnvironment) engine.control;
    final PatchSet.Id id = StoredValues.PATCH_SET_ID.get(engine);
    PatchSetInfoFactory psiFactory =
      env.getInjector().getInstance(PatchSetInfoFactory.class);

    Term idTerm;
    Term nameTerm = Prolog.Nil;
    Term emailTerm = Prolog.Nil;

    try {
      UserIdentity author = psiFactory.get(id).getAuthor();

      Account.Id authorId = author.getAccount();
      if (authorId == null) {
        idTerm = SymbolTerm.intern("anonymous");
      } else {
        idTerm = new IntegerTerm(authorId.get());
      }

      String name = author.getName();
      if (name != null && !name.equals("")) {
        nameTerm = SymbolTerm.intern(name);
      }

      String email = author.getEmail();
      if (email != null && !email.equals("")) {
        emailTerm = SymbolTerm.intern(email);
      }
    } catch (PatchSetInfoNotAvailableException err) {
      throw new JavaException(this, 1, err);
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