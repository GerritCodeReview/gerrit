// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.extensions.events.AbstractChangeEvent;
import com.google.gerrit.server.extensions.events.ChangeEventUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Set;

public class HashtagsEdited {

  private final DynamicSet<HashtagsEditedListener> listeners;
  private final ChangeEventUtil util;

  @Inject
  public HashtagsEdited(DynamicSet<HashtagsEditedListener> listeners,
      ChangeEventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, AccountInfo editor, Collection<String> hashtags,
      Collection<String> added, Collection<String> removed) {
    Event e = new Event(change, editor, hashtags, added, removed);
    for (HashtagsEditedListener l : listeners) {
      l.onHashtagsEdited(e);
    }
  }

  public void fire(Change change, Id accountId,
      ImmutableSortedSet<String> updatedHashtags, Set<String> toAdd,
      Set<String> toRemove) throws OrmException {
    fire(util.changeInfo(change),
        util.accountInfo(accountId),
        updatedHashtags, toAdd, toRemove);
  }

  private static class Event extends AbstractChangeEvent
      implements HashtagsEditedListener.Event {

    private AccountInfo editor;
    private Collection<String> updatedHashtags;
    private Collection<String> addedHashtags;
    private Collection<String> removedHashtags;

    Event(ChangeInfo change, AccountInfo editor, Collection<String> updated,
        Collection<String> added, Collection<String> removed) {
      super(change);
      this.editor = editor;
      this.updatedHashtags = updated;
      this.addedHashtags = added;
      this.removedHashtags = removed;
    }

    @Override
    public AccountInfo getEditor() {
      return editor;
    }

    @Override
    public Collection<String> getHashtags() {
      return updatedHashtags;
    }

    @Override
    public Collection<String> getAddedHashtags() {
      return addedHashtags;
    }

    @Override
    public Collection<String> getRemovedHashtags() {
      return removedHashtags;
    }
  }
}
