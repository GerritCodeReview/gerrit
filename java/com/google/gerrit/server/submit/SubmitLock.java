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

package com.google.gerrit.server.submit;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.google.gerrit.entities.Change;
import com.google.gerrit.server.query.change.ChangeData;

public interface SubmitLock {
    public Lock get(ChangeData cd);
    public Set<Lock> get(Collection<ChangeData> cds);
}
