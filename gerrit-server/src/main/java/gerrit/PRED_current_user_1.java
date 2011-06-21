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
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.ReplicationUser;
import com.google.gerrit.server.project.ChangeControl;

import com.googlecode.prolog_cafe.lang.EvaluationException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

public class PRED_current_user_1 extends Predicate.P1 {
  private static final long serialVersionUID = 1L;
  private static final SymbolTerm user = SymbolTerm.intern("user", 1);
  private static final SymbolTerm anonymous = SymbolTerm.intern("anonymous");
  private static final SymbolTerm peerDaemon = SymbolTerm.intern("peer_daemon");

  public PRED_current_user_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    PrologEnvironment env = (PrologEnvironment) engine.control;
    ChangeControl cControl = StoredValues.CHANGE_CONTROL.get(engine);
    CurrentUser curUser = cControl.getCurrentUser();
    Term resultTerm;

    if (curUser instanceof IdentifiedUser) {
      Account.Id id = ((IdentifiedUser)curUser).getAccountId();
      resultTerm = new IntegerTerm(id.get());
    } else if (curUser instanceof AnonymousUser) {
      resultTerm = anonymous;
    } else if (curUser instanceof PeerDaemonUser) {
      resultTerm = peerDaemon;
    } else if (curUser instanceof ReplicationUser) {
      resultTerm = SymbolTerm.intern("replication");
    } else {
      throw new EvaluationException("Unknown user type");
    }

    if (!a1.unify(new StructureTerm(user, resultTerm), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}