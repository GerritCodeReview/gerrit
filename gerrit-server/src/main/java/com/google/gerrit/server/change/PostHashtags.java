// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.PostHashtags.Input;
import com.google.gerrit.server.IdentifiedUser;
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
public class PostHashtags implements RestModifyView<ChangeResource, Input> {
  private final ChangeUpdate.Factory updateFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeIndexer indexer;
  private final ChangeHooks hooks;

  public static class Input {
    @DefaultInput
    public Set<String> add;
    public Set<String> remove;
  }

  @Inject
  PostHashtags(ChangeUpdate.Factory updateFactory,
      Provider<ReviewDb> dbProvider, ChangeIndexer indexer,
      ChangeHooks hooks) {
    this.updateFactory = updateFactory;
    this.dbProvider = dbProvider;
    this.indexer = indexer;
    this.hooks = hooks;
  }

  private Set<String> extractTags(Set<String> input)
      throws BadRequestException {
    if (input == null) {
      return ImmutableSet.of();
    } else {
      HashSet<String> result = new HashSet<>();
      for (String hashtag : input) {
        if (hashtag.contains(",")) {
          throw new BadRequestException("Hashtags may not contain commas");
        }
        if (!hashtag.trim().isEmpty()) {
          result.add(hashtag.trim());
        }
      }
      return result;
    }
  }

  @Override
  public Response<? extends Set<String>> apply(ChangeResource req, Input input)
      throws AuthException, OrmException, IOException, BadRequestException {
    if (input == null
        || (input.add == null && input.remove == null)) {
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
    Set<String> toAdd = new HashSet<>(extractTags(input.add));
    Set<String> toRemove = new HashSet<>(extractTags(input.remove));

    if (existingHashtags != null && !existingHashtags.isEmpty()) {
      updatedHashtags.addAll(existingHashtags);
      toAdd.removeAll(existingHashtags);
      toRemove.retainAll(existingHashtags);
    }

    if (toAdd.size() > 0 || toRemove.size() > 0) {
      updatedHashtags.addAll(toAdd);
      updatedHashtags.removeAll(toRemove);
      update.setHashtags(updatedHashtags);
      update.commit();

      indexer.index(dbProvider.get(), update.getChange());

      IdentifiedUser currentUser = ((IdentifiedUser) control.getCurrentUser());
      hooks.doHashtagsChangedHook(
          req.getChange(), currentUser.getAccount(),
          toAdd, toRemove, updatedHashtags,
          dbProvider.get());
    }

    return Response.ok(new TreeSet<String>(updatedHashtags));
  }
}
