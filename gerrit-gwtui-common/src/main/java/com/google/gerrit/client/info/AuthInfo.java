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

package com.google.gerrit.client.info;

import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;

import java.util.ArrayList;
import java.util.List;

public class AuthInfo extends JavaScriptObject {
  public final AuthType authType() {
    return AuthType.valueOf(authTypeRaw());
  }

  public final boolean isLdap() {
    return authType() == AuthType.LDAP || authType() == AuthType.LDAP_BIND;
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

  public final boolean isCustomExtension() {
    return authType() == AuthType.CUSTOM_EXTENSION;
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

  public final boolean siteHasUsernames() {
    if (isCustomExtension()
        && httpPasswordUrl() != null
        && !canEdit(FieldName.USER_NAME)) {
      return false;
    }
    return true;
  }

  public final boolean isHttpPasswordSettingsEnabled() {
    if (isLdap() && isGitBasicAuth()) {
      return false;
    }
    return true;
  }

  public final native boolean useContributorAgreements()
  /*-{ return this.use_contributor_agreements || false; }-*/;
  public final native String loginUrl() /*-{ return this.login_url; }-*/;
  public final native String loginText() /*-{ return this.login_text; }-*/;
  public final native String switchAccountUrl() /*-{ return this.switch_account_url; }-*/;
  public final native String registerUrl() /*-{ return this.register_url; }-*/;
  public final native String registerText() /*-{ return this.register_text; }-*/;
  public final native String editFullNameUrl() /*-{ return this.edit_full_name_url; }-*/;
  public final native String httpPasswordUrl() /*-{ return this.http_password_url; }-*/;
  public final native boolean isGitBasicAuth() /*-{ return this.is_git_basic_auth || false; }-*/;
  private native String authTypeRaw() /*-{ return this.auth_type; }-*/;
  private native JsArrayString _editableAccountFields()
  /*-{ return this.editable_account_fields; }-*/;

  protected AuthInfo() {
  }
}
