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

import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.rules.StoredValues;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.Term;

public class PRED_commit_committer_3 extends AbstractCommitUserIdentityPredicate {
  public PRED_commit_committer_3(Term a1, Term a2, Term a3, Operation n) {
    super(a1, a2, a3, n);
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    PatchSetInfo psInfo = StoredValues.PATCH_SET_INFO.get(engine);
    UserIdentity committer = psInfo.getCommitter();
    return exec(engine, committer);
  }
}
