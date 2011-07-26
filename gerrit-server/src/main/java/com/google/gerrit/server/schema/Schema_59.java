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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Schema_59 extends SchemaVersion {
  @Inject
  Schema_59(Provider<Schema_58> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    Pattern patternA = Pattern.compile("Patch Set ([0-9]+):.*", Pattern.DOTALL);
    Pattern patternB = Pattern.compile("Uploaded patch set ([0-9]+).");
    ResultSet<ChangeMessage> results = db.changeMessages().all();
    List<ChangeMessage> updates = new LinkedList<ChangeMessage>();
    for (ChangeMessage cm : results) {
      Change.Id id = cm.getKey().getParentKey();
      String msg = cm.getMessage();
      Matcher matcherA = patternA.matcher(msg);
      Matcher matcherB = patternB.matcher(msg);
      PatchSet.Id newId = null;
      if (matcherA.matches()) {
        int patchSetNum = Integer.parseInt(matcherA.group(1));
        newId = new PatchSet.Id(id, patchSetNum);
      } else if (matcherB.matches()) {
        int patchSetNum = Integer.parseInt(matcherB.group(1));
        newId = new PatchSet.Id(id, patchSetNum);
      }
      if (newId != null) {
        cm.setPatchSetId(newId);
        updates.add(cm);
      }
      if (updates.size() >= 100) {
        db.changeMessages().update(updates);
        updates.clear();
      }
    }
    if (!updates.isEmpty()) {
      db.changeMessages().update(updates);
    }
  }
}