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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.googlecode.prolog_cafe.exceptions.IllegalTypeException;
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

/**
 * Checks user can set label to val.
 *
 * <pre>
 *   '_check_user_label'(+Label, +CurrentUser, +Val)
 * </pre>
 */
class PRED__check_user_label_3 extends Predicate.P3 {
  PRED__check_user_label_3(Term a1, Term a2, Term a3, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();
    Term a3 = arg3.dereference();

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

    if (a3 instanceof VariableTerm) {
      throw new PInstantiationException(this, 3);
    }
    if (!(a3 instanceof IntegerTerm)) {
      throw new IllegalTypeException(this, 3, "integer", a3);
    }
    short val = (short) ((IntegerTerm) a3).intValue();

    ChangeData cd = StoredValues.CHANGE_DATA.get(engine);
    LabelType type = cd.getLabelTypes().byLabel(label);
    if (type == null) {
      return engine.fail();
    }

    try {
      StoredValues.PERMISSION_BACKEND
          .get(engine)
          .user(user)
          .change(cd)
          .check(new LabelPermission.WithValue(type, val));
      return cont;
    } catch (AuthException err) {
      return engine.fail();
    } catch (PermissionBackendException err) {
      SystemException se = new SystemException(err.getMessage());
      se.initCause(err);
      throw se;
    }
  }
}
