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

package com.google.gerrit.client.account;

import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.GpgKeyInfo;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.Set;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * accounts.
 */
public class AccountApi {
  public static RestApi self() {
    return accounts().view("self");
  }

  /** Retrieve the account edit preferences */
  public static void getEditPreferences(AsyncCallback<EditPreferences> cb) {
    self().view("preferences.edit").get(cb);
  }

  /** Put the account edit preferences */
  public static void putEditPreferences(EditPreferences in,
      AsyncCallback<VoidResult> cb) {
    self().view("preferences.edit").put(in, cb);
  }

  public static void suggest(String query, int limit,
      AsyncCallback<JsArray<AccountInfo>> cb) {
    accounts()
      .addParameter("q", query)
      .addParameter("n", limit)
      .background()
      .get(cb);
  }

  public static void putDiffPreferences(DiffPreferences in,
      AsyncCallback<DiffPreferences> cb) {
    self().view("preferences.diff").put(in, cb);
  }

  /** Retrieve the username */
  public static void getUsername(String account, AsyncCallback<NativeString> cb) {
    accounts().id(account).view("username").get(cb);
  }

  /** Set the username */
  public static void setUsername(String account, String username,
      AsyncCallback<NativeString> cb) {
    UsernameInput input = UsernameInput.create();
    input.username(username);
    accounts().id(account).view("username").put(input, cb);
  }

  /** Retrieve email addresses */
  public static void getEmails(String account,
      AsyncCallback<JsArray<EmailInfo>> cb) {
    accounts().id(account).view("emails").get(cb);
  }

  /** Register a new email address */
  public static void registerEmail(String account, String email,
      AsyncCallback<EmailInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    accounts().id(account).view("emails").id(email)
        .ifNoneMatch().put(in, cb);
  }

  /** Retrieve SSH keys */
  public static void getSshKeys(String account,
      AsyncCallback<JsArray<SshKeyInfo>> cb) {
    accounts().id(account).view("sshkeys").get(cb);
  }

  /** Add a new SSH keys */
  public static void addSshKey(String account, String sshPublicKey,
      AsyncCallback<SshKeyInfo> cb) {
    accounts().id(account).view("sshkeys")
        .post(sshPublicKey, cb);
  }

  /**
   * Delete SSH keys. For each key to be deleted a separate DELETE request is
   * fired to the server. The {@code onSuccess} method of the provided callback
   * is invoked once after all requests succeeded. If any request fails the
   * callbacks' {@code onFailure} method is invoked. In a failure case it can be
   * that still some of the keys were successfully deleted.
   */
  public static void deleteSshKeys(String account,
      Set<Integer> sequenceNumbers, AsyncCallback<VoidResult> cb) {
    CallbackGroup group = new CallbackGroup();
    for (int seq : sequenceNumbers) {
      accounts().id(account).view("sshkeys").id(seq)
          .delete(group.add(cb));
      cb = CallbackGroup.emptyCallback();
    }
    group.done();
  }

  /** Retrieve the HTTP password */
  public static void getHttpPassword(String account,
      AsyncCallback<NativeString> cb) {
    accounts().id(account).view("password.http").get(cb);
  }

  /** Generate a new HTTP password */
  public static void generateHttpPassword(String account,
      AsyncCallback<NativeString> cb) {
    HttpPasswordInput in = HttpPasswordInput.create();
    in.generate(true);
    accounts().id(account).view("password.http").put(in, cb);
  }

  /** Clear HTTP password */
  public static void clearHttpPassword(String account,
      AsyncCallback<VoidResult> cb) {
    accounts().id(account).view("password.http").delete(cb);
  }

  private static class HttpPasswordInput extends JavaScriptObject {
    final native void generate(boolean g) /*-{ if(g)this.generate=g; }-*/;

    static HttpPasswordInput create() {
      return createObject().cast();
    }

    protected HttpPasswordInput() {
    }
  }

  private static class UsernameInput extends JavaScriptObject {
    final native void username(String u) /*-{ if(u)this.username=u; }-*/;

    static UsernameInput create() {
      return createObject().cast();
    }

    protected UsernameInput() {
    }
  }

  public static void addGpgKey(String account, String armored,
      AsyncCallback<NativeMap<GpgKeyInfo>> cb) {
    accounts()
      .id(account)
      .view("gpgkeys")
      .post(GpgKeysInput.add(armored), cb);
  }

  public static void deleteGpgKeys(String account,
      Iterable<String> fingerprints, AsyncCallback<NativeMap<GpgKeyInfo>> cb) {
    accounts()
      .id(account)
      .view("gpgkeys")
      .post(GpgKeysInput.delete(fingerprints), cb);
  }

  private static RestApi accounts() {
    return new RestApi("accounts");
  }

  private static class GpgKeysInput extends JavaScriptObject {
    static GpgKeysInput add(String key) {
      return createWithAdd(Natives.arrayOf(key));
    }

    static GpgKeysInput delete(Iterable<String> fingerprints) {
      return createWithDelete(Natives.arrayOf(fingerprints));
    }

    private static native GpgKeysInput createWithAdd(JsArrayString keys) /*-{
      return {'add': keys};
    }-*/;

    private static native GpgKeysInput createWithDelete(
        JsArrayString fingerprints) /*-{
      return {'delete': fingerprints};
    }-*/;

    protected GpgKeysInput() {
    }
  }
}
