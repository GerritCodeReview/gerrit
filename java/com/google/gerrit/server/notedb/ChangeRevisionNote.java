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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.SubmitRequirementResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.MutableInteger;

/** Implements the parsing of comment data, handling JSON decoding and push certificates. */
class ChangeRevisionNote extends RevisionNote<HumanComment> {
  private final ChangeNoteJson noteJson;
  private final HumanComment.Status status;
  private String pushCert;

  private ImmutableList<SubmitRequirementResult> submitRequirementsResult;

  ChangeRevisionNote(
      ChangeNoteJson noteJson, ObjectReader reader, ObjectId noteId, HumanComment.Status status) {
    super(reader, noteId);
    this.noteJson = noteJson;
    this.status = status;
  }

  public ImmutableList<SubmitRequirementResult> getSubmitRequirementsResult() {
    checkParsed();
    return submitRequirementsResult;
  }

  public String getPushCert() {
    checkParsed();
    return pushCert;
  }

  @Override
  protected List<HumanComment> parse(byte[] raw, int offset)
      throws IOException, ConfigInvalidException {
    MutableInteger p = new MutableInteger();
    p.value = offset;

    ChangeRevisionNoteData data = parseJson(noteJson, raw, p.value);
    if (status == HumanComment.Status.PUBLISHED) {
      pushCert = data.pushCert;
    } else {
      pushCert = null;
    }
    this.submitRequirementsResult =
        data.submitRequirementResults == null
            ? ImmutableList.of()
            : ImmutableList.copyOf(data.submitRequirementResults);
    return data.comments;
  }

  private ChangeRevisionNoteData parseJson(ChangeNoteJson noteUtil, byte[] raw, int offset)
      throws IOException {
    try (InputStream is = new ByteArrayInputStream(raw, offset, raw.length - offset);
        Reader r = new InputStreamReader(is, UTF_8)) {
      return noteUtil.getGson().fromJson(r, ChangeRevisionNoteData.class);
    }
  }
}
