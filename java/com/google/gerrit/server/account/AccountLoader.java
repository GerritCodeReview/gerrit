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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountDirectory.DirectoryException;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides methods to fill in {@link AccountInfo}s lazily. Accounts can be queued up for filling by
 * calling {@code get} or {@code put} and are filled once {@code fill} is called.
 *
 * <p>This class is thread-safe.
 */
public class AccountLoader {
  public static final Set<FillOptions> DETAILED_OPTIONS =
      Collections.unmodifiableSet(
          EnumSet.of(
              FillOptions.ID,
              FillOptions.NAME,
              FillOptions.EMAIL,
              FillOptions.USERNAME,
              FillOptions.STATUS,
              FillOptions.AVATARS));

  public interface Factory {
    AccountLoader create(boolean detailed);

    AccountLoader create(Set<FillOptions> options);
  }

  private final InternalAccountDirectory directory;
  private final ImmutableSet<FillOptions> options;
  private final Map<Account.Id, AccountInfo> created;
  private final List<AccountInfo> provided;

  private boolean fillingCompleted = false;

  @AssistedInject
  AccountLoader(InternalAccountDirectory directory, @Assisted boolean detailed) {
    this(directory, detailed ? DETAILED_OPTIONS : InternalAccountDirectory.ID_ONLY);
  }

  @AssistedInject
  AccountLoader(InternalAccountDirectory directory, @Assisted Set<FillOptions> options) {
    this.directory = directory;
    this.options = ImmutableSet.copyOf(options);
    created = Collections.synchronizedMap(new HashMap<>());
    provided = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * Returns an {@link AccountInfo} that contains only the provided {@link Account.Id}. Deduplicates
   * {@link AccountInfo}s by their {@link Account.Id}.
   *
   * <p>Callers are not supposed to mutate the returned value while calling methods on this class.
   *
   * <p>Calling this method after {@link #fill()} was called will throw an {@link
   * IllegalStateException}.
   *
   * @param id that the {@link AccountInfo} is for
   * @return {@link AccountInfo} based on the provided id
   */
  public AccountInfo get(Account.Id id) {
    checkState(!fillingCompleted, "fill() was already called");
    if (id == null) {
      return null;
    }
    AccountInfo info = created.get(id);
    if (info == null) {
      info = new AccountInfo(id.get());
      created.put(id, info);
    }
    return info;
  }

  /**
   * Stores an {@link AccountInfo} for later filling.
   *
   * <p>Callers are not supposed to mutate the returned value while calling methods on this class.
   *
   * <p>Calling this method after {@link #fill()} was called will throw an {@link
   * IllegalStateException}.
   *
   * @param info that should be filled with details later on
   */
  public void put(AccountInfo info) {
    checkState(!fillingCompleted, "fill() was already called");
    checkArgument(info._accountId != null, "_accountId field required");
    provided.add(info);
  }

  /**
   * Fills all {@link AccountInfo} objects that were previously provided through {@link
   * #get(Account.Id)} and {@link #put(AccountInfo)} with data.
   *
   * <p>Can be called only once. Successive calls will throw an {@link IllegalStateException}.
   */
  public synchronized void fill() throws OrmException {
    checkState(!fillingCompleted, "fill() was already called before");
    // Make a copy of the concurrent data structures so that they are only used inside this class.
    // This makes it so that implementation changes in InternalAccountDirectory do not affect
    // locking on these collections.
    ImmutableList.Builder<AccountInfo> accountInfos =
        ImmutableList.<AccountInfo>builder().addAll(created.values()).addAll(provided);
    fill(accountInfos.build());
    fillingCompleted = true;
  }

  /**
   * Fills all provided {@link AccountInfo} objects with data.
   *
   * <p>Does not cache results internally between consecutive calls. Does not touch any internal
   * object state. Can be called multiple times in succession.
   */
  public void fill(Collection<? extends AccountInfo> infos) throws OrmException {
    infos.forEach(a -> checkArgument(a._accountId != null, "_accountId field required"));

    try {
      directory.fillAccountInfo(infos, options);
    } catch (DirectoryException e) {
      Throwables.throwIfInstanceOf(e.getCause(), OrmException.class);
      throw new OrmException(e);
    }
  }

  /**
   * Returns a filled {@link AccountInfo} object for the provided {@link Account.Id}.
   *
   * <p>Does not cache results internally between consecutive calls. Does not touch any internal
   * object state. Can be called multiple times in succession.
   */
  public AccountInfo fillOne(Account.Id id) throws OrmException {
    AccountInfo info = new AccountInfo(id.get());
    fill(ImmutableList.of(info));
    return info;
  }
}
