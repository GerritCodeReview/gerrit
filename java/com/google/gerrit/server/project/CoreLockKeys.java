// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.entities.Project;

/**
 * {@link LockKey}s for locks owned by Gerrit core.
 *
 * <p>Centralizing them here prevents two unrelated core call sites from silently colliding on the
 * same lock name. Plugins should use {@link LockKey#plugin(String, String, String...)} instead.
 */
public final class CoreLockKeys {
  public static final LockKey ACCOUNT_PATCH_REVIEW_DB = LockKey.core("account-patch-review-db");
  public static final LockKey CHANGE_CLEANUP = LockKey.core("change-cleanup");
  public static final LockKey DRAFT_COMMENTS_CLEANUP = LockKey.core("draft-comments-cleanup");
  public static final LockKey MIGRATE_PASSWORDS_TO_TOKENS =
      LockKey.core("migrate-passwords-to-tokens");
  public static final LockKey REDUCE_MAX_AUTH_TOKEN_LIFETIME =
      LockKey.core("reduce-max-auth-token-lifetime");

  public static LockKey createProject(Project.NameKey projectName) {
    return LockKey.core("create-project", projectName.get());
  }

  private CoreLockKeys() {}
}
