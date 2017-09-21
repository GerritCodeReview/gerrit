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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.StoredValues;
import com.google.gwtorm.server.OrmException;
import com.googlecode.prolog_cafe.exceptions.IllegalTypeException;
import com.googlecode.prolog_cafe.exceptions.JavaException;
import com.googlecode.prolog_cafe.exceptions.PInstantiationException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.exceptions.SystemException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import java.util.Set;

/**
 * Resolves the valid range for a label on a CurrentUser.
 *
 * <pre>
 *   '_user_label_range'(+Label, +CurrentUser, -Min, -Max)
 * </pre>
 */
class PRED__user_label_range_4 extends Predicate.P4 {
  PRED__user_label_range_4(Term a1, Term a2, Term a3, Term a4, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    arg4 = a4;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();
    Term a3 = arg3.dereference();
    Term a4 = arg4.dereference();

    if (a1 instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }
    if (!(a1 instanceof SymbolTerm)) {
      throw new IllegalTypeException(this, 1, "atom", a1);
    }
    String label = a1.name();

    if (a2 instanceof VariableTerm) {
      throw new PInstantiationException(this, 2);
    }
    if (!(a2 instanceof JavaObjectTerm) || !a2.convertible(CurrentUser.class)) {
      throw new IllegalTypeException(this, 2, "CurrentUser)", a2);
    }
    CurrentUser user = (CurrentUser) ((JavaObjectTerm) a2).object();

    Set<LabelPermission.WithValue> can;
    try {
      ChangeData cd = StoredValues.CHANGE_DATA.get(engine);
      LabelType type = cd.getLabelTypes().byLabel(label);
      if (type == null) {
        return engine.fail();
      }
      can = StoredValues.PERMISSION_BACKEND.get(engine).user(user).change(cd).test(type);
    } catch (OrmException err) {
      throw new JavaException(this, 1, err);
    } catch (PermissionBackendException err) {
      SystemException se = new SystemException(err.getMessage());
      se.initCause(err);
      throw se;
    }

    int min = 0;
    int max = 0;
    for (LabelPermission.WithValue v : can) {
      min = Math.min(min, v.value());
      max = Math.max(max, v.value());
    }

    if (!a3.unify(new IntegerTerm(min), engine.trail)) {
      return engine.fail();
    }

    if (!a4.unify(new IntegerTerm(max), engine.trail)) {
      return engine.fail();
    }

    return cont;
  }
}
