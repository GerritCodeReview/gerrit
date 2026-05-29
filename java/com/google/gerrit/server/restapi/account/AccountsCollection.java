// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.account.AccountResource;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** A generic interface for parsing account IDs from URL resources. */
public interface AccountsCollection extends RestCollection<TopLevelResource, AccountResource> {
  @Override
  AccountResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, AuthException, IOException, ConfigInvalidException;

  @Override
  RestView<TopLevelResource> list() throws ResourceNotFoundException;

  @Override
  DynamicMap<RestView<AccountResource>> views();
}
