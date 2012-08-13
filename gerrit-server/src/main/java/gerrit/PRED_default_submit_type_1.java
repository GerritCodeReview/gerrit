package gerrit;

import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.project.ChangeControl;

import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

public class PRED_default_submit_type_1 extends Predicate.P1 {

  public PRED_default_submit_type_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    ChangeControl control = StoredValues.CHANGE_CONTROL.get(engine);
    String submitType = control.getProject().getSubmitType().name();

    if (!a1.unify(SymbolTerm.create(submitType), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
