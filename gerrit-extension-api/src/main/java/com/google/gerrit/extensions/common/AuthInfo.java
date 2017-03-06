// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import java.util.List;

public class AuthInfo {
  public AuthType authType;
  public Boolean useContributorAgreements;
  public List<AgreementInfo> contributorAgreements;
  public List<AccountFieldName> editableAccountFields;
  public String loginUrl;
  public String loginText;
  public String switchAccountUrl;
  public String registerUrl;
  public String registerText;
  public String editFullNameUrl;
  public String httpPasswordUrl;
  public Boolean isGitBasicAuth;
  public GitBasicAuthPolicy gitBasicAuthPolicy;
}
