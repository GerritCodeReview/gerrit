// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import static com.google.common.base.Strings.nullToEmpty;

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;

@Singleton
public class ListIncludedGroups implements RestReadView<GroupResource> {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ListIncludedGroups.class);

  private final GroupControl.Factory controlFactory;
  private final Provider<ReviewDb> dbProvider;
  private final GroupJson json;

  @Inject
  ListIncludedGroups(
      GroupControl.Factory controlFactory, Provider<ReviewDb> dbProvider, GroupJson json) {
    this.controlFactory = controlFactory;
    this.dbProvider = dbProvider;
    this.json = json;
  }

  @Override
  public List<GroupInfo> apply(GroupResource rsrc) throws MethodNotAllowedException, OrmException {
    if (rsrc.toAccountGroup() == null) {
      throw new MethodNotAllowedException();
    }

    boolean ownerOfParent = rsrc.getControl().isOwner();
    List<GroupInfo> included = new ArrayList<>();
    for (AccountGroupById u :
        dbProvider.get().accountGroupById().byGroup(rsrc.toAccountGroup().getId())) {
      try {
        GroupControl i = controlFactory.controlFor(u.getIncludeUUID());
        if (ownerOfParent || i.isVisible()) {
          included.add(json.format(i.getGroup()));
        }
      } catch (NoSuchGroupException notFound) {
        log.warn(
            String.format(
                "Group %s no longer available, included into %s",
                u.getIncludeUUID(), rsrc.getGroup().getName()));
        continue;
      }
    }
    Collections.sort(
        included,
        new Comparator<GroupInfo>() {
          @Override
          public int compare(GroupInfo a, GroupInfo b) {
            int cmp = nullToEmpty(a.name).compareTo(nullToEmpty(b.name));
            if (cmp != 0) {
              return cmp;
            }
            return nullToEmpty(a.id).compareTo(nullToEmpty(b.id));
          }
        });
    return included;
  }
}
