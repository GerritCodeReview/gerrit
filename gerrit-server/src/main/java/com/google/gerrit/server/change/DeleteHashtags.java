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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.DeleteHashtags.Input;
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

@Singleton
public class DeleteHashtags implements RestModifyView<ChangeResource, Input> {
  private final ChangeUpdate.Factory updateFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeIndexer indexer;

  public static class Input {
    @DefaultInput
    public String hashtags;
  }

  @Inject
  DeleteHashtags(ChangeUpdate.Factory updateFactory,
      Provider<ReviewDb> dbProvider, ChangeIndexer indexer) {
    this.updateFactory = updateFactory;
    this.dbProvider = dbProvider;
    this.indexer = indexer;
  }

  @Override
  public Response<Set<String>> apply(ChangeResource req, Input input)
      throws AuthException, OrmException, IOException, BadRequestException {
    if (input == null || Strings.isNullOrEmpty(input.hashtags)) {
      throw new BadRequestException("Hashtags are required");
    }

    ChangeControl control = req.getControl();
    if(!control.canEditHashtags()){
      throw new AuthException("Editing hashtags not permitted");
    }
    ChangeUpdate update = updateFactory.create(control);
    ChangeNotes notes = control.getNotes().load();
    Set<String> hashtags = new HashSet<String>();
    Set<String> oldHashtags = notes.getHashtags();
    if (oldHashtags != null) {
      hashtags.addAll(oldHashtags);
    }
    hashtags.removeAll(Lists.newArrayList(Splitter.on(CharMatcher.anyOf(",;"))
        .trimResults().omitEmptyStrings().split(input.hashtags)));
    update.setHashtags(hashtags);
    update.commit();
    indexer.index(dbProvider.get(), update.getChange());

    return Response.ok(hashtags);
  }
}
