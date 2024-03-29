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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.server.rules.prolog.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Prolog predicate for the destination branch of a change.
 *
 * <p>Checks that the term that is provided as input to this Prolog predicate is a string atom that
 * matches the destination branch of the change.
 *
 * <pre>
 *   'change_branch'(-Branch)
 * </pre>
 */
public class PRED_change_branch_1 extends Predicate.P1 {
  public PRED_change_branch_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    BranchNameKey name = StoredValues.getChange(engine).getDest();

    if (!a1.unify(SymbolTerm.create(name.branch()), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
