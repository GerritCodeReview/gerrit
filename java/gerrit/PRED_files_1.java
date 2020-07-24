// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/** Exports list of Strings that each represent a file name in the current patchset. */
public class PRED_files_1 extends Predicate.P1 {
  private static final SymbolTerm file = SymbolTerm.intern("file", 3);

  PRED_files_1(Term a1, Operation n) {
    arg1 = a1;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();
    Term a1 = arg1.dereference();
    Term listHead = Prolog.Nil;
    try (RevWalk revWalk = new RevWalk(StoredValues.REPOSITORY.get(engine))) {
      RevCommit commit = revWalk.parseCommit(StoredValues.getPatchSet(engine).commitId());
      List<PatchListEntry> patches = StoredValues.PATCH_LIST.get(engine).getPatches();
      TreeWalk treeWalk = new TreeWalk(StoredValues.REPOSITORY.get(engine));
      HashMap<String, Boolean> isSubmodule =
          createFileToIsSubmoduleMapping(treeWalk, commit, patches);
      for (PatchListEntry entry : patches) {
        if (Patch.isMagic(entry.getNewName())) {
          continue;
        }
        SymbolTerm fileNameTerm = SymbolTerm.create(entry.getNewName());
        SymbolTerm changeType = SymbolTerm.create(entry.getChangeType().getCode());
        SymbolTerm fileType;
        if (isSubmodule.get(entry.getNewName())) {
          fileType = SymbolTerm.create("SUBMODULE");
        } else {
          fileType = SymbolTerm.create("REGULAR");
        }
        listHead =
            new ListTerm(new StructureTerm(file, fileNameTerm, changeType, fileType), listHead);
      }
    } catch (IOException ex) {
      return engine.fail();
    }
    if (!a1.unify(listHead, engine.trail)) {
      return engine.fail();
    }
    return cont;
  }

  private static HashMap<String, Boolean> createFileToIsSubmoduleMapping(
      TreeWalk treeWalk, RevCommit commit, List<PatchListEntry> patches)
      throws PrologException, IOException {
    HashMap<String, Boolean> isSubmodule = new HashMap();

    treeWalk.addTree(commit.getTree());
    List<PathFilter> files = new ArrayList();
    for (PatchListEntry entry : patches) {
      if (Patch.isMagic(entry.getNewName())) {
        continue;
      }
      files.add(PathFilter.create(entry.getNewName()));
      isSubmodule.put(entry.getNewName(), false);
    }
    treeWalk.setFilter(PathFilterGroup.create(files));

    while (treeWalk.next()) {
      if (treeWalk.getFileMode() == FileMode.GITLINK) {
        isSubmodule.put(treeWalk.getPathString(), true);
      }
    }
    return isSubmodule;
  }
}
