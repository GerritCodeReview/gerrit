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
import com.google.gwt.core.client.JsArray;

import java.util.ArrayList;
import java.util.List;

public class AuthInfo extends JavaScriptObject {
  public final AuthType authType() {
    return AuthType.valueOf(auth_typeRaw());
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
    for (AccountFieldNameInfo f : Natives.asList(editable_account_fields())) {
      fields.add(f.get());
    }
    return fields;
  }

  public final native boolean use_contributor_agreements()
  /*-{ return this.use_contributor_agreements || false; }-*/;
  private final native String auth_typeRaw() /*-{ return this.auth_type; }-*/;
  private final native JsArray<AccountFieldNameInfo> editable_account_fields()
  /*-{ return this.editable_account_fields; }-*/;

  protected AuthInfo() {
  }

  private static class AccountFieldNameInfo extends JavaScriptObject {
    final Account.FieldName get() {
      return Account.FieldName.valueOf(getRaw());
    }

    private final native String getRaw() /*-{ return this; }-*/;

    protected AccountFieldNameInfo() {
    }
  }
}
