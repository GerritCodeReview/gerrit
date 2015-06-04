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

package com.google.gerrit.client.config;

import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;

import java.util.ArrayList;
import java.util.List;

public class AuthInfo extends JavaScriptObject {
  public final AuthType authType() {
    return AuthType.valueOf(authTypeRaw());
  }

  public final boolean isOpenId() {
    return authType() == AuthType.OPENID;
  }

  public final boolean isOAuth() {
    return authType() == AuthType.OAUTH;
  }

  public final boolean isDev() {
    return authType() == AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT;
  }

  public final boolean isClientSslCertLdap() {
    return authType() == AuthType.CLIENT_SSL_CERT_LDAP;
  }

  public final boolean canEdit(Account.FieldName f) {
    return editableAccountFields().contains(f);
  }

  public final List<Account.FieldName> editableAccountFields() {
    List<Account.FieldName> fields = new ArrayList<>();
    for (String f : Natives.asList(_editableAccountFields())) {
      fields.add(Account.FieldName.valueOf(f));
    }
    return fields;
  }

  public final native boolean useContributorAgreements()
  /*-{ return this.use_contributor_agreements || false; }-*/;
  private final native String authTypeRaw() /*-{ return this.auth_type; }-*/;
  private final native JsArrayString _editableAccountFields()
  /*-{ return this.editable_account_fields; }-*/;

  protected AuthInfo() {
  }
}
