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

import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.Text;
import com.googlecode.prolog_cafe.exceptions.IllegalDomainException;
import com.googlecode.prolog_cafe.exceptions.IllegalTypeException;
import com.googlecode.prolog_cafe.exceptions.JavaException;
import com.googlecode.prolog_cafe.exceptions.PInstantiationException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Returns true if "any of" or "all" the files that match FileNameRegex
 * have edited lines that match EditRegex. Qualifier can assume
 * only one of the following values:
 *
 * "all_of": EditRegex must match with all files that match FileNameRegex
 *
 * "any_of": EditRegex must match at least with one of the files that
 *           matches FileNameRegex
 *
 * <pre>
 *   'commit_edits'(+FileNameRegex, +EditRegex, +Qualifier)
 * </pre>
 */
public class PRED_commit_edits_3 extends Predicate.P3 {
  private static final String ALL_OF = "all_of";
  private static final String ANY_OF = "any_of";

  private interface Matcher {
    /**
     * The following values enumerate the possible outcome of a match.
     *
     * OK: the match is enough to make the predicate TRUE, no need to check for
     *     other diffs. That never happens with the ALL_OF predicate.
     *
     * EVALUATE_NEXT: the match is not enough to make the predicate TRUE.
     * That means:
     * -   in the ALL_OF case, that the match is OK, but all other diffs must be checked too.
     * -   in the ANY_OF case, that the match fails, but another diff could match,
     *     so the loop must continue.
     *
     * EMPTY: there's no diff to evaluate.
     *
     * FAIL: the match fails, and there's no need to check other diffs,
     *       the predicate is FALSE. That never happens with the ANY_OF predicate.
     *
     * If all diffs are EMPTY, whatever is the qualifier, the predicate is FALSE.
     */
    public enum Result {
      OK,
      EVALUATE_NEXT,
      EMPTY,
      FAIL
    }

    Result match(int startSeq, int endSeq);
  }

  private class BaseMatcher {
    private Text text;
    private Pattern regex;

    protected BaseMatcher(Text t, Pattern r) {
      text = t;
      regex = r;
    }

    protected Text getText() {
      return text;
    }

    protected Pattern getRegex() {
      return regex;
    }
  }

  private class MatchAnyOf extends BaseMatcher implements Matcher {
    public MatchAnyOf(Text t, Pattern r) {
      super(t, r);
    }

    @Override
    public Matcher.Result match(int startSeq, int endSeq) {
      if (getText() != Text.EMPTY) {
        String diff = getText().getString(startSeq, endSeq, true);
        if (getRegex().matcher(diff).find()) {
          return Matcher.Result.OK;
        } else {
          return Matcher.Result.EVALUATE_NEXT;
        }
      } else {
        return Matcher.Result.EMPTY;
      }
    }
  }

  private class MatchAllOf extends BaseMatcher implements Matcher {
    public MatchAllOf(Text t, Pattern r) {
      super(t, r);
    }

    @Override
    public Matcher.Result match(int startSeq, int endSeq) {
      if (getText() != Text.EMPTY) {
        String diff = getText().getString(startSeq, endSeq, true);
        if (!getRegex().matcher(diff).find()) {
          return Matcher.Result.FAIL;
        } else {
          return Matcher.Result.EVALUATE_NEXT;
        }
      } else {
        return Matcher.Result.EMPTY;
      }
    }
  }

  public PRED_commit_edits_3(Term a1, Term a2, Term a3, Operation n) {
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

    Pattern fileRegex = getRegexParameter(a1);
    Pattern editRegex = getRegexParameter(a2);

    PatchList pl = StoredValues.PATCH_LIST.get(engine);
    Repository repo = StoredValues.REPOSITORY.get(engine);

    try (ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      final RevTree aTree;
      final RevTree bTree;
      final RevCommit bCommit = rw.parseCommit(pl.getNewId());
      boolean allOf = isAllOf(arg3.dereference());

      if (pl.getOldId() != null) {
        aTree = rw.parseTree(pl.getOldId());
      } else {
        // Octopus merge with unknown automatic merge result, since the
        // web UI returns no files to match against, just fail.
        return engine.fail();
      }
      bTree = bCommit.getTree();

      boolean atLeastOneDiff = false;
      for (PatchListEntry entry : pl.getPatches()) {
        String newName = entry.getNewName();
        String oldName = entry.getOldName();

        if (newName.equals("/COMMIT_MSG")) {
          continue;
        }

        if (fileRegex.matcher(newName).find()
            || (oldName != null && fileRegex.matcher(oldName).find())) {
          List<Edit> edits = entry.getEdits();

          if (edits.isEmpty()) {
            continue;
          }

          Text tA;
          if (oldName != null) {
            tA = load(aTree, oldName, reader);
          } else {
            tA = load(aTree, newName, reader);
          }
          Matcher matcherA = (allOf)? new MatchAllOf(tA, editRegex) : new MatchAnyOf(tA, editRegex);

          Text tB = load(bTree, newName, reader);
          Matcher matcherB = (allOf)? new MatchAllOf(tB, editRegex) : new MatchAnyOf(tB, editRegex);

          for (Edit edit : edits) {
            Matcher.Result mrA = matcherA.match(edit.getBeginA(), edit.getEndA());
            if (mrA == Matcher.Result.OK) {
              return cont;
            }

            Matcher.Result mrB = matcherB.match(edit.getBeginB(), edit.getEndB());
            if (mrB == Matcher.Result.OK) {
              return cont;
            }

            if (mrA == Matcher.Result.FAIL || mrB == Matcher.Result.FAIL) {
              return engine.fail();
            }

            if (mrA != Matcher.Result.EMPTY || mrB != Matcher.Result.EMPTY) {
              atLeastOneDiff = true;
            }
          }
        }
      }

      /* When using the ALL_OF qualifier, if we had at least one EVALUATE_NEXT,
       * the predicate is TRUE.
       */
      if (atLeastOneDiff && allOf) {
        return cont;
      }
    } catch (IOException err) {
      throw new JavaException(this, 1, err);
    }

    return engine.fail();
  }

  private void checkIsASymbolTerm(Term term) {
    if (term instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }
    if (!(term instanceof SymbolTerm)) {
      throw new IllegalTypeException(this, 1, "symbol", term);
    }
  }

  private Pattern getRegexParameter(Term term) {
    checkIsASymbolTerm(term);

    return Pattern.compile(term.name(), Pattern.MULTILINE);
  }

  private boolean isAllOf(Term term) {
    checkIsASymbolTerm(term);

    if (!term.name().equals(ALL_OF) && !term.name().equals(ANY_OF))
      throw new IllegalDomainException(this, 3, String.format("[%s, %s]", ANY_OF, ALL_OF), term);

    return term.name().equals(ALL_OF);
  }

  private Text load(ObjectId tree, String path, ObjectReader reader)
      throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
          IOException {
    if (path == null) {
      return Text.EMPTY;
    }
    final TreeWalk tw = TreeWalk.forPath(reader, path, tree);
    if (tw == null) {
      return Text.EMPTY;
    }
    if (tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
      return Text.EMPTY;
    }
    return new Text(reader.open(tw.getObjectId(0), Constants.OBJ_BLOB));
  }
}
