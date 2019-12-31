// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Prolog predicate for the uploader of the current patch set of a change.
 *
 * <p>Checks that the term that is provided as input to this Prolog predicate is a user ID term that
 * matches the account ID of the uploader of the current patch set.
 *
 * <pre>
 *   'uploader'(user(-ID))
 * </pre>
 */
public class PRED_uploader_1 extends Predicate.P1 {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SymbolTerm user = SymbolTerm.intern("user", 1);

  public PRED_uploader_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    PatchSet patchSet = StoredValues.getPatchSet(engine);
    if (patchSet == null) {
      logger.atSevere().log(
          "Failed to load current patch set of change %s",
          StoredValues.getChange(engine).getChangeId());
      return engine.fail();
    }

    Account.Id uploaderId = patchSet.uploader();

    if (!a1.unify(new StructureTerm(user, new IntegerTerm(uploaderId.get())), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
