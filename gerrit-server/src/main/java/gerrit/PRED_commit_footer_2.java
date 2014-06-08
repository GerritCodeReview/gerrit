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
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * Given a symbol, returns the first matching footer value of the current patchset.
 *
 * <pre>
 *   'commit_footer'(+footer, -Msg)
 * </pre>
 */
public class PRED_commit_footer_2 extends Predicate.P2 {
  public PRED_commit_footer_2(Term a1, Term a2, Operation n) {
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
      throw new IllegalTypeException(this, 1, "symbol", a1);
    }
    if (!a2.isVariable()) {
      throw new IllegalTypeException(this, 1, "variable", a2);
    }

    SymbolTerm footerTerm = Prolog.Nil;
    final FooterKey footerKey = new FooterKey(a1.toString());

    PatchList pl = StoredValues.PATCH_LIST.get(engine);
    Repository repo = StoredValues.REPOSITORY.get(engine);
    final ObjectReader reporeader = repo.newObjectReader();

    try {
      final RevWalk rw = new RevWalk(reporeader);
      final RevCommit commit = rw.parseCommit(pl.getNewId());

      // Return on first match
      for (final FooterLine footer : commit.getFooterLines()) {
        if (footer.matches(footerKey)) {
          footerTerm = SymbolTerm.create(footer.getValue());

          if (!a2.unify(footerTerm, engine.trail)) {
            return engine.fail();
          }
          return cont;
        }
      }
    } catch (IOException err) {
      throw new JavaException(this, 1, err);
    } finally {
      reporeader.release();
    }

    return engine.fail();
  }
}
