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

package com.google.gerrit.server.git.validators;

import java.util.List;
import java.util.Optional;

public class RefOperationValidationResult {
  private final List<ValidationMessage> validationMessages;
  private final Optional<RefUpdateContextValidationData> validationData;
  public RefOperationValidationResult(List<ValidationMessage> validationMessages, Optional<RefUpdateContextValidationData> validationData) {
    this.validationMessages = validationMessages;
    this.validationData = validationData;
  }

  public List<ValidationMessage> getValidationMessages() {
    return validationMessages;
  }
  public Optional<RefUpdateContextValidationData> getValidationData() {
    return validationData;
  }
  public interface RefUpdateContextValidationData {
  }
}

