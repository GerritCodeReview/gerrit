// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.Relation;
import com.google.gwtorm.server.Schema;

/**
 * The review service database schema.
 *
 * <p>Root entities that are at the top level of some important data graph:
 *
 * <ul>
 *   <li>{@link Change}: All review information about a single proposed change.
 * </ul>
 */
public interface ReviewDb extends Schema {
  // Deleted @Relation(id = 1)

  // Deleted @Relation(id = 2)

  // Deleted @Relation(id = 3)

  // Deleted @Relation(id = 4)

  // Deleted @Relation(id = 6)

  // Deleted @Relation(id = 7)

  // Deleted @Relation(id = 8)

  // Deleted @Relation(id = 10)

  // Deleted @Relation(id = 11)

  // Deleted @Relation(id = 12)

  // Deleted @Relation(id = 13)

  // Deleted @Relation(id = 17)

  // Deleted @Relation(id = 18)

  // Deleted @Relation(id = 19)

  // Deleted @Relation(id = 20)

  @Relation(id = 21)
  ChangeAccess changes();

  @Relation(id = 22)
  PatchSetApprovalAccess patchSetApprovals();

  @Relation(id = 23)
  ChangeMessageAccess changeMessages();

  @Relation(id = 24)
  PatchSetAccess patchSets();

  // Deleted @Relation(id = 25)

  @Relation(id = 26)
  PatchLineCommentAccess patchComments();

  // Deleted @Relation(id = 28)

  // Deleted @Relation(id = 29)

  // Deleted @Relation(id = 30)

}
