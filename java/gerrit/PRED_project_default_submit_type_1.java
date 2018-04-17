// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

public class PRED_project_default_submit_type_1 extends Predicate.P1 {

  private static final SymbolTerm[] term;

  static {
    SubmitType[] val = SubmitType.values();
    term = new SymbolTerm[val.length];
    for (int i = 0; i < val.length; i++) {
      term[i] = SymbolTerm.create(val[i].name());
    }
  }

  public PRED_project_default_submit_type_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    SubmitType submitType = StoredValues.PROJECT_ACCESSOR.get(engine).getSubmitType();
    if (!a1.unify(term[submitType.ordinal()], engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
