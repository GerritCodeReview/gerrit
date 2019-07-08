// Copyright 2011 Google Inc. All Rights Reserved.

package gerrit;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.googlecode.prolog_cafe.exceptions.JavaException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

/** Exports list of {@code commit_label( label('Code-Review', 2), user(12345789) )}. */
class PRED__load_commit_labels_1 extends Predicate.P1 {
  private static final SymbolTerm sym_commit_label = SymbolTerm.intern("commit_label", 2);
  private static final SymbolTerm sym_label = SymbolTerm.intern("label", 2);
  private static final SymbolTerm sym_user = SymbolTerm.intern("user", 1);

  PRED__load_commit_labels_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    Term listHead = Prolog.Nil;
    try {
      ChangeData cd = StoredValues.CHANGE_DATA.get(engine);
      LabelTypes types = StoredValues.CHANGE_CONTROL.get(engine).getLabelTypes();

      for (PatchSetApproval a : cd.currentApprovals()) {
        LabelType t = types.byLabel(a.getLabelId());
        if (t == null) {
          continue;
        }

        StructureTerm labelTerm =
            new StructureTerm(
                sym_label, SymbolTerm.intern(t.getName()), new IntegerTerm(a.getValue()));

        StructureTerm userTerm =
            new StructureTerm(sym_user, new IntegerTerm(a.getAccountId().get()));

        listHead = new ListTerm(new StructureTerm(sym_commit_label, labelTerm, userTerm), listHead);
      }
    } catch (OrmException err) {
      throw new JavaException(this, 1, err);
    }

    if (!a1.unify(listHead, engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
