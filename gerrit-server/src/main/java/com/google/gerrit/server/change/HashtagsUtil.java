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

import static com.google.common.base.CharMatcher.WHITESPACE;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.validators.HashtagValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class HashtagsUtil {
  private static final CharMatcher LEADER = WHITESPACE.or(CharMatcher.is('#'));
  private static final String PATTERN = "(?:\\s|\\A)#[\\p{L}[0-9]-_]+";

  private final ChangeUpdate.Factory updateFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeIndexer indexer;
  private final ChangeHooks hooks;
  private final DynamicSet<HashtagValidationListener> hashtagValidationListeners;

  @Inject
  HashtagsUtil(ChangeUpdate.Factory updateFactory,
      Provider<ReviewDb> dbProvider,
      ChangeIndexer indexer,
      ChangeHooks hooks,
      DynamicSet<HashtagValidationListener> hashtagValidationListeners) {
    this.updateFactory = updateFactory;
    this.dbProvider = dbProvider;
    this.indexer = indexer;
    this.hooks = hooks;
    this.hashtagValidationListeners = hashtagValidationListeners;
  }

  public static String cleanupHashtag(String hashtag) {
    hashtag = LEADER.trimLeadingFrom(hashtag);
    hashtag = WHITESPACE.trimTrailingFrom(hashtag);
    return hashtag;
  }

  public static Set<String> extractTags(String input) {
    Set<String> result = new HashSet<>();
    if (!Strings.isNullOrEmpty(input)) {
      Matcher matcher = Pattern.compile(PATTERN).matcher(input);
      while (matcher.find()) {
        result.add(cleanupHashtag(matcher.group()));
      }
    }
    return result;
  }

  private Set<String> extractTags(Set<String> input)
      throws IllegalArgumentException {
    if (input == null) {
      return Collections.emptySet();
    } else {
      HashSet<String> result = new HashSet<>();
      for (String hashtag : input) {
        if (hashtag.contains(",")) {
          throw new IllegalArgumentException("Hashtags may not contain commas");
        }
        hashtag = cleanupHashtag(hashtag);
        if (!hashtag.isEmpty()) {
          result.add(hashtag);
        }
      }
      return result;
    }
  }

  public TreeSet<String> setHashtags(ChangeControl control,
      HashtagsInput input, boolean runHooks, boolean index)
          throws IllegalArgumentException, IOException,
          ValidationException, AuthException, OrmException {
    if (input == null
        || (input.add == null && input.remove == null)) {
      throw new IllegalArgumentException("Hashtags are required");
    }

    if (!control.canEditHashtags()) {
      throw new AuthException("Editing hashtags not permitted");
    }
    ChangeUpdate update = updateFactory.create(control);
    ChangeNotes notes = control.getNotes().load();

    Set<String> existingHashtags = notes.getHashtags();
    Set<String> updatedHashtags = new HashSet<>();
    Set<String> toAdd = new HashSet<>(extractTags(input.add));
    Set<String> toRemove = new HashSet<>(extractTags(input.remove));

    for (HashtagValidationListener validator : hashtagValidationListeners) {
      validator.validateHashtags(update.getChange(), toAdd, toRemove);
    }

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

      if (index) {
        indexer.index(dbProvider.get(), update.getChange());
      }

      if (runHooks) {
        IdentifiedUser currentUser = ((IdentifiedUser) control.getCurrentUser());
        hooks.doHashtagsChangedHook(
            update.getChange(), currentUser.getAccount(),
            toAdd, toRemove, updatedHashtags,
            dbProvider.get());
      }
    }
    return new TreeSet<>(updatedHashtags);
  }
}
