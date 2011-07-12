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

import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;

import com.googlecode.prolog_cafe.lang.JavaException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Returns the commit message as a symbol
 *
 * <pre>
 *   'commit_msg'(-Msg)
 * </pre>
 */
public class PRED_commit_msg_1 extends Predicate.P1 {
  private static final long serialVersionUID = 1L;

  public PRED_commit_msg_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    PrologEnvironment env = (PrologEnvironment) engine.control;
    PatchSetInfo psInfo;
    try {
      psInfo = getPatchSetInfo(env);
    } catch (PatchSetInfoNotAvailableException err) {
      throw new JavaException(this, 1, err);
    }

    SymbolTerm msg = SymbolTerm.create(psInfo.getMessage());
    if (!a1.unify(msg, engine.trail)) {
      return engine.fail();
    }
    return cont;
  }

  protected PatchSetInfo getPatchSetInfo(PrologEnvironment env)
      throws PatchSetInfoNotAvailableException {
    PatchSetInfo psInfo = env.get(StoredValues.PATCH_SET_INFO);
    if (psInfo == null) {
      PatchSet.Id patchSetId = env.get(StoredValues.PATCH_SET_ID);
      PatchSetInfoFactory patchInfoFactory =
        env.getInjector().getInstance(PatchSetInfoFactory.class);
      psInfo = patchInfoFactory.get(patchSetId);
      env.set(StoredValues.PATCH_SET_INFO, psInfo);
    }

    return psInfo;
  }
}