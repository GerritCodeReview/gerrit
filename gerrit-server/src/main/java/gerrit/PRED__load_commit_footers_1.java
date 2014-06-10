// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.server.patch.PatchList;

import com.googlecode.prolog_cafe.lang.IllegalTypeException;
import com.googlecode.prolog_cafe.lang.JavaException;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * Returns the list of footers of the current patchset.
 *
 * <pre>
 *   'commit_footer'(+footer, +value).
 * </pre>
 */
class PRED__load_commit_footers_1 extends Predicate.P1 {
  private static final SymbolTerm sym_commit_footer = SymbolTerm.intern("commit_footer", 2);

  PRED__load_commit_footers_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();

    if (!a1.isVariable()) {
      throw new IllegalTypeException(this, 1, "variable", a1);
    }

    Term footerList = Prolog.Nil;

    PatchList pl = StoredValues.PATCH_LIST.get(engine);
    Repository repo = StoredValues.REPOSITORY.get(engine);
    final ObjectReader reporeader = repo.newObjectReader();

    try {
      final RevWalk rw = new RevWalk(reporeader);
      final RevCommit commit = rw.parseCommit(pl.getNewId());

      for (final FooterLine footer : commit.getFooterLines()) {
        SymbolTerm footerKeyTerm = SymbolTerm.create(footer.getKey());
        SymbolTerm footerValueTerm = SymbolTerm.create(footer.getValue());

        footerList = new ListTerm(
          new StructureTerm(
            sym_commit_footer, footerKeyTerm, footerValueTerm),
          footerList);
      }
    } catch (IOException err) {
      throw new JavaException(this, 1, err);
    } finally {
      reporeader.release();
    }

    if (!a1.unify(footerList, engine.trail)) {
      return engine.fail();
    }
    return cont;
  }
}
