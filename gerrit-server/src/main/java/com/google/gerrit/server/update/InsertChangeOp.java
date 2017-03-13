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

package com.google.gerrit.server.update;

import com.google.gerrit.reviewdb.client.Change;

/**
 * Specialization of {@link BatchUpdateOp} for creating changes.
 *
 * <p>A typical {@code BatchUpdateOp} operates on a change that has been read from a transaction;
 * this type, by contrast, is responsible for creating the change from scratch.
 *
 * <p>Ops of this type must be used via {@link BatchUpdate#insertChange(InsertChangeOp)}. They may
 * be mixed with other {@link BatchUpdateOp}s for the same change, in which case the insert op runs
 * first.
 */
public interface InsertChangeOp extends BatchUpdateOp {
  Change createChange(Context ctx);
}
