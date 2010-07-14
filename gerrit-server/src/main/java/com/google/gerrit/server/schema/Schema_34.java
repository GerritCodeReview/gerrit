// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.RefRight.RefPattern;
import com.google.gerrit.server.project.RefControl.RefRightsForPattern;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Schema_34 extends SchemaVersion {
  private static final Comparator<String> DESCENDING_SORT =
      new Comparator<String>() {

        @Override
        public int compare(String a, String b) {
          int aLength = a.length();
          int bLength = b.length();
          if (bLength == aLength) {
            return a.compareTo(b);
          }
          return bLength - aLength;
        }
      };

  @Inject
  Schema_34(Provider<Schema_33> prior) {
    super(prior);
  }


  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    Iterable<Project> projects = db.projects().all();
    boolean showedBanner = false;

    List<RefRight> toUpdate = new ArrayList<RefRight>();
    List<RefRight> toDelete = new ArrayList<RefRight>();
    for (Project p : projects) {
      boolean showedProject = false;
      List<RefRight> pr = db.refRights().byProject(p.getNameKey()).toList();
      Map<ApprovalCategory.Id, Map<String, RefRightsForPattern>> r =
        new HashMap<ApprovalCategory.Id, Map<String, RefRightsForPattern>>();
      for (RefRight right : pr) {
        ApprovalCategory.Id cat = right.getApprovalCategoryId();
        if (r.get(cat) == null) {
          Map<String, RefRightsForPattern> m =
            new TreeMap<String, RefRightsForPattern>(DESCENDING_SORT);
          r.put(cat, m);
        }
        if (r.get(cat).get(right.getRefPattern()) == null) {
          RefRightsForPattern s = new RefRightsForPattern();
          r.get(cat).put(right.getRefPattern(), s);
        }
        r.get(cat).get(right.getRefPattern()).addRight(right);
      }

      for (Map<String, RefRightsForPattern> categoryRights : r.values()) {
        for (RefRightsForPattern rrp : categoryRights.values()) {
          RefRight oldRight = rrp.getRights().get(0);
          if (shouldPrompt(oldRight)) {
            if (!showedBanner) {
              ui.message("Entering interactive reference rights migration tool...");
              showedBanner = true;
            }
            if (!showedProject) {
              ui.message("In project " + p.getName());
              showedProject = true;
            }
            ui.message("For category " + oldRight.getApprovalCategoryId());
            boolean isWildcard = oldRight.getRefPattern().endsWith("/*");
            boolean shouldUpdate = ui.yesno(!isWildcard,
                "Should rights for pattern "
                + oldRight.getRefPattern()
                + " be considered exclusive?");
            if (shouldUpdate) {
              RefRight.Key newKey = new RefRight.Key(oldRight.getProjectNameKey(),
                  new RefPattern("-" + oldRight.getRefPattern()),
                  oldRight.getApprovalCategoryId(),
                  oldRight.getAccountGroupId());
              RefRight newRight = new RefRight(newKey);
              newRight.setMaxValue(oldRight.getMaxValue());
              newRight.setMinValue(oldRight.getMinValue());
              toUpdate.add(newRight);
              toDelete.add(oldRight);
            }
          }
        }
      }
    }
    db.refRights().insert(toUpdate);
    db.refRights().delete(toDelete);
  }

  private boolean shouldPrompt(RefRight right) {
    return !right.getRefPattern().equals("refs/*")
      && !right.getRefPattern().equals("refs/heads/*")
      && !right.getRefPattern().equals("refs/tags/*");
  }
}
