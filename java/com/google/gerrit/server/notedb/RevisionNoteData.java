// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.SubmitRequirementResult;
import java.util.List;

/**
 * Holds the raw data of a RevisionNote.
 *
 * <p>It is intended for serialization to JSON only. It is used for human comments, as well as for
 * storing submit requirements.
 */
class RevisionNoteData {
  String pushCert;
  List<Comment> comments;
  List<SubmitRequirementResult> submitRequirementResults;
}
