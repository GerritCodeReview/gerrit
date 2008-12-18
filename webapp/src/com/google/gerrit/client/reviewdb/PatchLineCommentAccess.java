// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface PatchLineCommentAccess extends
    Access<PatchLineComment, PatchLineComment.Id> {
  @PrimaryKey("key")
  PatchLineComment get(PatchLineComment.Id id) throws OrmException;

  @Query("WHERE key.patchId = ? AND status = '"
      + PatchLineComment.STATUS_PUBLISHED + "' ORDER BY lineNbr,writtenOn")
  ResultSet<PatchLineComment> published(Patch.Id id) throws OrmException;

  @Query("WHERE key.patchId = ? AND status = '" + PatchLineComment.STATUS_DRAFT
      + "' AND author = ? ORDER BY lineNbr,writtenOn")
  ResultSet<PatchLineComment> draft(Patch.Id patch, Account.Id author)
      throws OrmException;
}
