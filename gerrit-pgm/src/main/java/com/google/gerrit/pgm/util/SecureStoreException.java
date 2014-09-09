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

package com.google.gerrit.pgm.util;

public class SecureStoreException extends RuntimeException {
  private static final long serialVersionUID = 5581700510568485065L;

  SecureStoreException(String msg) {
    super(msg);
  }

  SecureStoreException(String msg, Exception e) {
    super(msg, e);
  }
}
