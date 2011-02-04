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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

public final class Search {

  /** unique identification of this Search. */
  public static class Key extends Owner.OwnedKey {
    private static final long serialVersionUID = 1L;

    protected Key() {
      super();
    }

    public Key(final Owner.Id owner, final String name) {
      super(owner, name);
    }

    public Key(final Owner.Type type, final String owner, final String name) {
      super(type, owner, name);
    }

    public Key(final Account.Id owner, final String name) {
      super(owner, name);
    }

    public Key(final AccountGroup.Id owner, final String name) {
      super(owner, name);
    }

    public Key(final Project.NameKey owner, final String name) {
      super(owner, name);
    }

    public Key(final String name) { // SITE
      super(name);
    }
  }

  /** unique identifier of the search  */
  @Column(id = 1)
  protected Key key;

  @Column(id = 2, length = Integer.MAX_VALUE, notNull = false)
  protected String description = "";

  @Column(id = 3, length = Integer.MAX_VALUE, notNull = false)
  protected String query = "";

  protected Search() {
  }

  public Search(final Key key, final String description, final String query) {
    setKey(key);
    setDescription(description);
    setQuery(query);
  }

  public Search(final Key key) {
    this(key, "", "");
  }

  public Key getKey() {
    return key;
  }

  public void setKey(final Key key) {
    this.key = key;
  }

  public String getName() {
    if (getKey() == null) {
      return "";
    }
    return getKey().get();
  }

  public Owner.Type getType() {
    if (getKey() == null) {
      return null;
    }
    return getKey().getType();
  }

  public String getOwner() {
    if (getKey() == null) {
      return "";
    }
    return getKey().getOwner();
  }

  public Owner.Id getOwnerId() {
    if (getKey() == null) {
      return null;
    }
    return getKey().getOwnerId();
  }

  public void setOwnerId(Owner.Id id) {
    if (getKey() == null) {
      setKey(new Key(id, ""));
    } else {
      getKey().setOwnerId(id);
    }
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String d) {
    description = notNull(d);
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = notNull(query);
  }

  public boolean isValid() {
    if (getKey() == null) {
      return false;
    }
    return getKey().isValid();
  }

  public static String notNull(String s) {
    return s == null ? "" : s;
  }
}
