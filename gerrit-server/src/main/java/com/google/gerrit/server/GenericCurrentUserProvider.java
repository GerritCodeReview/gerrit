// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class GenericCurrentUserProvider implements Provider<CurrentUser> {
  private final CopyOnWriteArrayList<Provider<CurrentUser>> providers =
      new CopyOnWriteArrayList<Provider<CurrentUser>>();

  public void addProvider(Provider<CurrentUser> user) {
    providers.add(user);
  }

  @Override
  public CurrentUser get() {
    for (Provider<CurrentUser> p : providers) {
      try {
        return p.get();
      } catch (OutOfScopeException err) {
      }
    }
    throw new OutOfScopeException("Not in request");
  }
}
