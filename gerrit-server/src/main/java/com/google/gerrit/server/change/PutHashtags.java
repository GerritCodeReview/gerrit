// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutHashtags.Input;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

@Singleton
public class PutHashtags implements RestModifyView<ChangeResource, Input> {
  private final ChangeUpdate.Factory updateFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeIndexer indexer;
  private final ChangeHooks hooks;

  public static class Input {
    @DefaultInput
    public String hashtags;
  }

  @Inject
  PutHashtags(ChangeUpdate.Factory updateFactory,
      Provider<ReviewDb> dbProvider, ChangeIndexer indexer,
      ChangeHooks hooks) {
    this.updateFactory = updateFactory;
    this.dbProvider = dbProvider;
    this.indexer = indexer;
    this.hooks = hooks;
  }

  @Override
  public Response<TreeSet<String>> apply(ChangeResource req, Input input)
      throws AuthException, OrmException, IOException, BadRequestException {
    if (input == null || Strings.isNullOrEmpty(input.hashtags)) {
      throw new BadRequestException("Hashtags are required");
    }

    ChangeControl control = req.getControl();
    if (!control.canEditHashtags()) {
      throw new AuthException("Editing hashtags not permitted");
    }
    ChangeUpdate update = updateFactory.create(control);
    ChangeNotes notes = control.getNotes().load();

    Set<String> existingHashtags = notes.getHashtags();
    Set<String> updatedHashtags = new HashSet<>();
    Set<String> toAdd = new HashSet<String>(
        Lists.newArrayList(Splitter.on(CharMatcher.anyOf(",;"))
        .trimResults().omitEmptyStrings().split(input.hashtags)));

    if (existingHashtags != null && !existingHashtags.isEmpty()) {
      updatedHashtags.addAll(existingHashtags);
      toAdd.removeAll(existingHashtags);
    }

    if (toAdd.size() > 0) {
      updatedHashtags.addAll(toAdd);
      update.setHashtags(updatedHashtags);
      update.commit();

      indexer.index(dbProvider.get(), update.getChange());

      IdentifiedUser currentUser = ((IdentifiedUser) control.getCurrentUser());
      hooks.doHashtagsChangedHook(
          req.getChange(), currentUser.getAccount(),
          toAdd, null, updatedHashtags,
          dbProvider.get());
    }

    return Response.ok(new TreeSet<String>(updatedHashtags));
  }
}
