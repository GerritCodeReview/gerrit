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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.permissions.PermissionBackendException;
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
 * AccountLoader is the class that populates properties of the AccountInfo provided to it.
 *
 * <p>The class is designed to be used in the following way:
 *
 * <ol>
 *   <li>Call {@code get} to get AccountInfo for a given id that will be filled on next fill.
 *   <li>Call {@code put} to provide AccountInfo that will be filled on the next fill.
 *   <li>Call {@code fill} to populate properties of the AccountInfo.
 *   <li>Call {@code get} if needed again to get filled AccountInfo.
 * </ol>
 */
public class AccountLoader {
  public static final Set<FillOptions> DETAILED_OPTIONS =
      Collections.unmodifiableSet(
          EnumSet.of(
              FillOptions.ID,
              FillOptions.NAME,
              FillOptions.EMAIL,
              FillOptions.USERNAME,
              FillOptions.DISPLAY_NAME,
              FillOptions.STATUS,
              FillOptions.STATE,
              FillOptions.AVATARS,
              FillOptions.TAGS,
              FillOptions.DELETED));

  public interface Factory {
    AccountLoader create(boolean detailed);

    AccountLoader create(Set<FillOptions> options);
  }

  private final InternalAccountDirectory directory;
  private final Set<FillOptions> options;
  // Single AccountInfo per AccountId that is actually evaluated. All others (if any) in "provided"
  // are copies of these.
  private final Map<Account.Id, AccountInfo> primeAccountInfo;
  // Extra AccountInfo provided by callers that should be populated after fill().
  private final List<AccountInfo> provided;

  @AssistedInject
  AccountLoader(InternalAccountDirectory directory, @Assisted boolean detailed) {
    this(directory, detailed ? DETAILED_OPTIONS : InternalAccountDirectory.ID_ONLY);
  }

  @AssistedInject
  AccountLoader(InternalAccountDirectory directory, @Assisted Set<FillOptions> options) {
    this.directory = directory;
    this.options = options;
    primeAccountInfo = new HashMap<>();
    provided = new ArrayList<>();
  }

  /**
   * Return AccountInfo for given id.
   *
   * <p>If called before {@code fill} the AccountInfo is unfilled and will be filled on next call to
   * fill.
   *
   * <p>If called after {@code fill} will return filled AccountInfo only if account with this id was
   * specified in one of {@code get} or {@code put} call before the call to {@code fill}. Otherwise,
   * returns unfilled AccountInfo.
   */
  @Nullable
  public synchronized AccountInfo get(@Nullable Account.Id id) {
    if (id == null) {
      return null;
    }
    AccountInfo info = primeAccountInfo.get(id);
    if (info == null) {
      info = new AccountInfo(id.get());
      primeAccountInfo.put(id, info);
    }
    return info;
  }

  /** Provide AccountInfo that will be filled on the next fill. */
  public synchronized void put(AccountInfo info) {
    checkArgument(info._accountId != null, "_accountId field required");
    provided.add(info);
  }

  /**
   * Populates properties of the {@link AccountInfo} previously returned from {@code get} or
   * provided by {@code put}
   */
  @SuppressWarnings("ReferenceEquality") // Intentional reference equality check
  public void fill() throws PermissionBackendException {
    try (TraceTimer timer = TraceContext.newTimer("Fill accounts", Metadata.empty())) {
      for (AccountInfo info : provided) {
        primeAccountInfo.putIfAbsent(Account.id(info._accountId), info);
      }
      directory.fillAccountInfo(primeAccountInfo.values(), options);
      for (AccountInfo info : provided) {
        AccountInfo filledInfo = primeAccountInfo.get(Account.id(info._accountId));
        // Check if it's the same instance.
        if (filledInfo != info) {
          filledInfo.copyTo(info);
        }
      }
    }
  }

  /** Same as {@link #fill()}, but also populate {@link AccountInfo} in {@code infos} */
  public void fill(Collection<? extends AccountInfo> infos) throws PermissionBackendException {
    for (AccountInfo info : infos) {
      put(info);
    }
    fill();
  }

  /** Same as {@link #fill()}, but also create and populate {@link AccountInfo} for provided id. */
  @Nullable
  public AccountInfo fillOne(@Nullable Account.Id id) throws PermissionBackendException {
    AccountInfo info = get(id);
    fill();
    return info;
  }
}
