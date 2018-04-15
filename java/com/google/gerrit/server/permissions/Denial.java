// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.api.access.GerritPermission;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

@AutoValue
abstract class Denial {
  static Optional<Denial> denial(GerritPermission permission, String resource) {
    return Optional.of(new AutoValue_Denial(permission, resource, Optional.empty()));
  }

  static Optional<Denial> denial(GerritPermission permission, String resource, String advice) {
    return Optional.of(new AutoValue_Denial(permission, resource, Optional.of(advice)));
  }

  @SafeVarargs
  static Optional<Denial> combineCauses(Optional<Denial> overall, Optional<Denial>... causes) {
    if (!overall.isPresent()) {
      return Optional.empty();
    }
    if (Arrays.stream(causes).noneMatch(Optional::isPresent)) {
      // No potential causes actually resulted in denial.
      return Optional.empty();
    }
    Stream<String> allAdvice =
        Streams.concat(
            Streams.stream(overall.get().advice()),
            Arrays.stream(causes)
                .flatMap(Streams::stream)
                .map(Denial::advice)
                .flatMap(Streams::stream));
    return denial(
        overall.get().permission(), overall.get().resource(), allAdvice.collect(joining("\n")));
  }

  abstract GerritPermission permission();

  abstract String resource();

  abstract Optional<String> advice();
}
