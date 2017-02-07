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

package com.google.gerrit.server.schema;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

public class Schema_83 extends SchemaVersion {

  @Inject
  Schema_83() {
    super(
        new Provider<SchemaVersion>() {
          @Override
          public SchemaVersion get() {
            throw new ProvisionException("Upgrade first to 2.8 or 2.9");
          }
        });
  }
}
