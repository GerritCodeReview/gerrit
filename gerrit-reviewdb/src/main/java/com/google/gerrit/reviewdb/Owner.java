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

public class Owner {

  /** unique identification of this Owner. */
  public static class Id extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected char type;

    @Column(id = 2)
    protected String id = "";

    public Id(final Type type, final String id) {
      setType(type);
      set(id);
    }

    public Id(final Account.Id owner) {
      this(Owner.Type.USER, owner.toString());
    }

    public Id(final AccountGroup.Id owner) {
      this(Owner.Type.GROUP, owner.toString());
    }

    public Id(final Project.NameKey owner) {
      this(Owner.Type.PROJECT, owner.toString());
    }

    public Id() {
      this(Owner.Type.SITE, "");
    }

    @Override
    public com.google.gwtorm.client.Key<?> getParentKey() {
      switch(getType()) {
        case USER:    return getAccountId();
        case GROUP:   return getAccountGroupId();
        case PROJECT: return getProjectNameKey();
      }
      return null;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    public void set(final String id) {
      this.id = notNull(id);
    }

    public Type getType() {
      return Owner.Type.forCode(type);
    }

    protected void setType(final Type type) {
      this.type = type.getCode();
    }

    public Account.Id getAccountId() {
      return Account.Id.parse(get());
    }

    public AccountGroup.Id getAccountGroupId() {
      return AccountGroup.Id.parse(get());
    }

    public Project.NameKey getProjectNameKey() {
      return Project.NameKey.parse(get());
    }

    @Override
    public boolean equals(final Object other) {
      if (other instanceof Id) {
        Id id = (Id) other;
        return getType() == id.getType() && get().equals(id.get());
      }
      return false;
    }
  }

  public static enum Type {
    USER('U', "user"),

    GROUP('G', "group"),

    PROJECT('P', "project"),

    SITE('S', "site");

    private final char code;
    private String id;


    private Type(final char c, final String id) {
      code = c;
      this.id = id;
    }

    public char getCode() {
      return code;
    }

    public String getId() {
      return id;
    }

    public static Type forCode(final char c) {
      for (final Type s : Type.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }

    public static Type forId(final String id) {
      for (final Type s : Type.values()) {
        if (s.id.equals(id)) {
          return s;
        }
      }
      return null;
    }
  }

  public static class OwnedKey extends StringKey<Owner.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Owner.Id owner;

    @Column(id = 2)
    protected String name = "";

    public OwnedKey() {
      setOwnerId(new Owner.Id());
      set("");
    }

    public OwnedKey(final Owner.Id owner, final String name) {
      setOwnerId(owner);
      set(name);
    }

    public OwnedKey(final Owner.Type type, final String owner, final String name) {
      this(new Owner.Id(type, owner), name);
    }

    public OwnedKey(final Account.Id owner, final String name) {
      this(new Owner.Id(owner), name);
    }

    public OwnedKey(final AccountGroup.Id owner, final String name) {
      this(new Owner.Id(owner), name);
    }

    public OwnedKey(final Project.NameKey owner, final String name) {
      this(new Owner.Id(owner), name);
    }

    public OwnedKey(final String name) { // SITE
      this(new Owner.Id(), name);
    }

    @Override
    public String get() {
      return name;
    }

    @Override
    public void set(final String name) {
      this.name = notNull(name);
    }

    public Owner.Type getType() {
      return owner.getType();
    }

    public String getOwner() {
      return owner.get();
    }

    public Owner.Id getOwnerId() {
      return owner;
    }

    public void setOwnerId(Owner.Id id) {
      owner = id;
    }

    @Override
    public Owner.Id getParentKey() {
      return owner;
    }

    @Override
    public boolean equals(final Object other) {
      if (other instanceof OwnedKey) {
        OwnedKey k = (OwnedKey) other;

        if (name.equals(k.name)) {
          if (getOwner() == null) {
            return k.getOwner() == null;
          }
          return getOwner().equals(k.getOwner());
        }
      }
      return false;
    }

    public boolean isValid() {
      if (getType() == Owner.Type.SITE) {
        return isValidName(name);
      }
      return getParentKey() != null && isValidName(name);
    }

    public static boolean isValidName(String name) {
      return name != null && name.matches("[-_A-z0-9]+");
    }
  }

  private Id id;

  protected Owner() {  // Do not use, only for JSON
  }

  protected Owner(Type type) {
    this(new Id(type, null));
  }

  protected Owner(Type type, String idStr) {
    this(new Id(type, idStr));
  }

  public Owner(Id id) {
    setId(id);
  }

  public Id getId() {
    return id;
  }

  protected void setId(Id id) {
    id.getType(); // throw an excpetion if id is null!
    this.id = id;
  }

  public Type getType() {
    return getId().getType();
  }

  public static String notNull(String s) {
    return s == null ? "" : s;
  }
}
