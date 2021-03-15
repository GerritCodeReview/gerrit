// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.entities.Patch;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.Term;
import java.util.Collection;
import java.util.Map;

/**
 * Exports basic commit statistics.
 *
 * <pre>
 *   'commit_stats'(-Files, -Insertions, -Deletions)
 * </pre>
 */
public class PRED_commit_stats_3 extends Predicate.P3 {
  public PRED_commit_stats_3(Term a1, Term a2, Term a3, Operation n) {
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

    Map<String, FileDiffOutput> modifiedFiles = StoredValues.DIFF_LIST.get(engine);
    // Account for magic files
    if (!a1.unify(
        new IntegerTerm(modifiedFiles.size() - countMagicFiles(modifiedFiles.values())),
        engine.trail)) {
      return engine.fail();
    }
    Integer insertions =
        modifiedFiles.values().stream().map(FileDiffOutput::insertions).reduce(0, Integer::sum);
    Integer deletions =
        modifiedFiles.values().stream().map(FileDiffOutput::deletions).reduce(0, Integer::sum);
    if (!a2.unify(new IntegerTerm(insertions), engine.trail)) {
      return engine.fail();
    }
    if (!a3.unify(new IntegerTerm(deletions), engine.trail)) {
      return engine.fail();
    }
    return cont;
  }

  private int countMagicFiles(Collection<FileDiffOutput> entries) {
    int count = 0;
    for (FileDiffOutput e : entries) {
      if (e.newPath().isPresent() && Patch.isMagic(e.newPath().get())) {
        count++;
      }
    }
    return count;
  }
}
