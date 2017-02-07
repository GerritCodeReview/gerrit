// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.PushCertificateInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import java.util.List;
import java.util.Map;

public interface GpgApiAdapter {
  boolean isEnabled();

  Map<String, GpgKeyInfo> listGpgKeys(AccountResource account)
      throws RestApiException, GpgException;

  Map<String, GpgKeyInfo> putGpgKeys(AccountResource account, List<String> add, List<String> delete)
      throws RestApiException, GpgException;

  GpgKeyApi gpgKey(AccountResource account, IdString idStr) throws RestApiException, GpgException;

  PushCertificateInfo checkPushCertificate(String certStr, IdentifiedUser expectedUser)
      throws GpgException;
}
