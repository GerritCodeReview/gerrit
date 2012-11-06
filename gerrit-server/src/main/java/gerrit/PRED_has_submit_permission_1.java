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

import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;

import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.Term;

public class PRED_has_submit_permission_1 extends Predicate.P1 {

  public PRED_has_submit_permission_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    if (!a1.isJavaObject()) {
      return engine.fail();
    }
    CurrentUser who = (CurrentUser) a1.toJava();
    ChangeControl cControl = StoredValues.CHANGE_CONTROL.get(engine);
    cControl = cControl.forUser(who);
    if(! cControl.getRefControl().canSubmit()) {
      return engine.fail();
    }
    return cont;
  }
}