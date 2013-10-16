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

package com.google.gerrit.server.account;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountDirectory.DirectoryException;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccountInfo {
  public static class Loader {
    private static final Set<FillOptions> DETAILED_OPTIONS =
        Collections.unmodifiableSet(EnumSet.of(
            FillOptions.NAME,
            FillOptions.EMAIL,
            FillOptions.USERNAME,
            FillOptions.AVATARS));

    public interface Factory {
      Loader create(boolean detailed);
    }

    private final InternalAccountDirectory directory;
    private final boolean detailed;
    private final Map<Account.Id, AccountInfo> created;
    private final List<AccountInfo> provided;

    @Inject
    Loader(InternalAccountDirectory directory, @Assisted boolean detailed) {
      this.directory = directory;
      this.detailed = detailed;
      created = Maps.newHashMap();
      provided = Lists.newArrayList();
    }

    public AccountInfo get(Account.Id id) {
      if (id == null) {
        return null;
      }
      AccountInfo info = created.get(id);
      if (info == null) {
        info = new AccountInfo(id);
        if (detailed) {
          info._account_id = id.get();
        }
        created.put(id, info);
      }
      return info;
    }

    public void put(AccountInfo info) {
      if (detailed) {
        info._account_id = info._id.get();
      }
      provided.add(info);
    }

    public void fill() throws OrmException {
      try {
        directory.fillAccountInfo(
            Iterables.concat(created.values(), provided),
            detailed ? DETAILED_OPTIONS : EnumSet.of(FillOptions.NAME));
      } catch (DirectoryException e) {
        Throwables.propagateIfPossible(e.getCause(), OrmException.class);
        throw new OrmException(e);
      }
    }

    public void fill(Collection<? extends AccountInfo> infos)
        throws OrmException {
      for (AccountInfo info : infos) {
        put(info);
      }
      fill();
    }
  }

  public transient Account.Id _id;

  public AccountInfo(Account.Id id) {
    _id = id;
  }

  public Integer _account_id;
  public String name;
  public String email;
  public String username;
  public List<AvatarInfo> avatars;

  public static class AvatarInfo {
    /**
     * Size in pixels the UI prefers an avatar image to be.
     *
     * The web UI prefers avatar images to be square, both
     * the height and width of the image should be this size.
     * The height is the more important dimension to match
     * than the width.
     */
    public static final int DEFAULT_SIZE = 26;

    public String url;
    public Integer height;
    public Integer width;
  }
}
