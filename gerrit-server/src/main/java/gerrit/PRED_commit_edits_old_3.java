// Copyright (C) 2017 The Android Open Source Project
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

import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Term;

/**
 * Returns true if "any of" or "all" the files that match FileNameRegex
 * have edited lines that match EditRegex in the old revision.
 * Qualifier can assume only one of the following values:
 *
 * "all_of": EditRegex must match with all files that match FileNameRegex
 *
 * "any_of": EditRegex must match at least with one of the files that
 *           matches FileNameRegex
 *
 * <pre>
 *   'commit_edits_old'(+FileNameRegex, +EditRegex, +Qualifier)
 * </pre>
 */
public class PRED_commit_edits_old_3 extends PRED_commit_edits_3 {
  public PRED_commit_edits_old_3(Term a1, Term a2, Term a3, Operation n) {
    super(a1, a2, a3, n, Revision.Old);
  }
}
