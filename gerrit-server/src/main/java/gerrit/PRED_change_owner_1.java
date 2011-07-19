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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.rules.StoredValues;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.JavaException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Iterator;

public class PRED_change_owner_1 extends Predicate.P1 {
  private static final long serialVersionUID = 1L;
  private static final SymbolTerm user = SymbolTerm.intern("user", 1);
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  public PRED_change_owner_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    Change change = StoredValues.CHANGE.getOrNull(engine);
    if (change == null) {
      // Attempt to retrieve change from db, if it doesn't exist (change hasn't
      // been made yet), return patch committer as the owner.
      Change.Key changeKey = null;
      RevCommit c = StoredValues.REV_COMMIT.get(engine);
      for (final String changeId : c.getFooterLines(CHANGE_ID)) {
        Change.Key newKey = new Change.Key(changeId.trim());
        if (newKey.isValid()) {
          changeKey = newKey;
        }
      }
      if (changeKey != null) {
        ReviewDb db = StoredValues.REVIEW_DB.get(engine);
        try {
          ResultSet<Change> changes = db.changes().byKey(changeKey);
          Iterator<Change> iter = changes.iterator();
          if (iter.hasNext()) {
            // Assuming 0 or 1 changes here.
            change = iter.next();
            StoredValues.CHANGE.set(engine, change);
          }
        } catch (OrmException err) {
          throw new JavaException(this, 1, err);
        }
      }
    }

    Account.Id ownerId;
    if (change == null) {
      ownerId = StoredValues.COMMITTER_IDENT.get(engine).getAccount();
    } else {
      ownerId = change.getOwner();
    }

    if (!a1.unify(new StructureTerm(user, new IntegerTerm(ownerId.get())), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}