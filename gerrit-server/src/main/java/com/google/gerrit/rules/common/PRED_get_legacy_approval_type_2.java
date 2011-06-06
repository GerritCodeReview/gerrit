// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.gerrit.rules.common;

import static com.google.gerrit.rules.common.PRED_get_legacy_approval_types_1.export;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.rules.PrologEnvironment;

import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Get a single approval type from the server configuration:
 *
 * <pre>
 * get_legacy_approval_type(Label, approval_type(Label, Id, Fun, Min, Max))
 * </pre>
 *
 * where:
 * <ul>
 * <li>{@code Label} - the newer style label name</li>
 * <li>{@code Id} - the legacy ApprovalCategory.Id from the database</li>
 * <li>{@code Fun} - legacy function name</li>
 * <li>{@code Min, Max} - the smallest and largest configured values.</li>
 * </ul>
 */
class PRED_get_legacy_approval_type_2 extends Predicate.P2 {
  PRED_get_legacy_approval_type_2(Term a1, Term a2, Operation n) {
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

    if (!a1.isSymbol()) {
      return engine.fail();
    }

    SymbolTerm sym = (SymbolTerm) a1;
    if (sym.arity() != 0) {
      return engine.fail();
    }

    String name = sym.name();
    PrologEnvironment env = (PrologEnvironment) engine.control;
    ApprovalTypes types = env.getInjector().getInstance(ApprovalTypes.class);

    ApprovalType type = types.byLabel(name);
    if (type == null) {
      type = types.byId(new ApprovalCategory.Id(name));
      if (types == null) {
        return engine.fail();
      }
    }

    if (!a2.unify(export(type), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
