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
import com.google.gerrit.client.info.AgreementInfo;
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
import java.util.HashSet;
import java.util.Set;

/** A collection of static methods which work on the Gerrit REST API for specific accounts. */
public class AccountApi {
  public static RestApi self() {
    return new RestApi("/accounts/").view("self");
  }

  /** Retrieve the account edit preferences */
  public static void getEditPreferences(AsyncCallback<EditPreferences> cb) {
    self().view("preferences.edit").get(cb);
  }

  /** Put the account edit preferences */
  public static void putEditPreferences(EditPreferences in, AsyncCallback<EditPreferences> cb) {
    self().view("preferences.edit").put(in, cb);
  }

  public static void suggest(String query, int limit, AsyncCallback<JsArray<AccountInfo>> cb) {
    new RestApi("/accounts/")
        .addParameterTrue("suggest")
        .addParameter("q", query)
        .addParameter("n", limit)
        .background()
        .get(cb);
  }

  public static void putDiffPreferences(DiffPreferences in, AsyncCallback<DiffPreferences> cb) {
    self().view("preferences.diff").put(in, cb);
  }

  /** Retrieve the username */
  public static void getUsername(String account, AsyncCallback<NativeString> cb) {
    new RestApi("/accounts/").id(account).view("username").get(cb);
  }

  /** Set the username */
  public static void setUsername(String account, String username, AsyncCallback<NativeString> cb) {
    UsernameInput input = UsernameInput.create();
    input.username(username);
    new RestApi("/accounts/").id(account).view("username").put(input, cb);
  }

  /** Retrieve the account name */
  public static void getName(String account, AsyncCallback<NativeString> cb) {
    new RestApi("/accounts/").id(account).view("name").get(cb);
  }

  /** Set the account name */
  public static void setName(String account, String name, AsyncCallback<NativeString> cb) {
    AccountNameInput input = AccountNameInput.create();
    input.name(name);
    new RestApi("/accounts/").id(account).view("name").put(input, cb);
  }

  /** Retrieve email addresses */
  public static void getEmails(String account, AsyncCallback<JsArray<EmailInfo>> cb) {
    new RestApi("/accounts/").id(account).view("emails").get(cb);
  }

  /** Register a new email address */
  public static void registerEmail(String account, String email, AsyncCallback<EmailInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    new RestApi("/accounts/").id(account).view("emails").id(email).ifNoneMatch().put(in, cb);
  }

  /** Set preferred email address */
  public static void setPreferredEmail(
      String account, String email, AsyncCallback<NativeString> cb) {
    new RestApi("/accounts/").id(account).view("emails").id(email).view("preferred").put(cb);
  }

  /** Retrieve SSH keys */
  public static void getSshKeys(String account, AsyncCallback<JsArray<SshKeyInfo>> cb) {
    new RestApi("/accounts/").id(account).view("sshkeys").get(cb);
  }

  /** Add a new SSH keys */
  public static void addSshKey(String account, String sshPublicKey, AsyncCallback<SshKeyInfo> cb) {
    new RestApi("/accounts/").id(account).view("sshkeys").post(sshPublicKey, cb);
  }

  /** Retrieve Watched Projects */
  public static void getWatchedProjects(
      String account, AsyncCallback<JsArray<ProjectWatchInfo>> cb) {
    new RestApi("/accounts/").id(account).view("watched.projects").get(cb);
  }

  /** Create/Update Watched Project */
  public static void updateWatchedProject(
      String account,
      ProjectWatchInfo watchedProjectInfo,
      AsyncCallback<JsArray<ProjectWatchInfo>> cb) {
    Set<ProjectWatchInfo> watchedProjectInfos = new HashSet<>();
    watchedProjectInfos.add(watchedProjectInfo);
    updateWatchedProjects(account, watchedProjectInfos, cb);
  }

  /** Create/Update Watched Projects */
  public static void updateWatchedProjects(
      String account,
      Set<ProjectWatchInfo> watchedProjectInfos,
      AsyncCallback<JsArray<ProjectWatchInfo>> cb) {
    new RestApi("/accounts/")
        .id(account)
        .view("watched.projects")
        .post(projectWatchArrayFromSet(watchedProjectInfos), cb);
  }

  /** Delete Watched Project */
  public static void deleteWatchedProject(
      String account,
      ProjectWatchInfo watchedProjectInfo,
      AsyncCallback<JsArray<ProjectWatchInfo>> cb) {
    Set<ProjectWatchInfo> watchedProjectInfos = new HashSet<>();
    watchedProjectInfos.add(watchedProjectInfo);
    deleteWatchedProjects(account, watchedProjectInfos, cb);
  }

  /** Delete Watched Projects */
  public static void deleteWatchedProjects(
      String account,
      Set<ProjectWatchInfo> watchedProjectInfos,
      AsyncCallback<JsArray<ProjectWatchInfo>> cb) {
    new RestApi("/accounts/")
        .id(account)
        .view("watched.projects:delete")
        .post(projectWatchArrayFromSet(watchedProjectInfos), cb);
  }

  /**
   * Delete SSH keys. For each key to be deleted a separate DELETE request is fired to the server.
   * The {@code onSuccess} method of the provided callback is invoked once after all requests
   * succeeded. If any request fails the callbacks' {@code onFailure} method is invoked. In a
   * failure case it can be that still some of the keys were successfully deleted.
   */
  public static void deleteSshKeys(
      String account, Set<Integer> sequenceNumbers, AsyncCallback<VoidResult> cb) {
    CallbackGroup group = new CallbackGroup();
    for (int seq : sequenceNumbers) {
      new RestApi("/accounts/").id(account).view("sshkeys").id(seq).delete(group.add(cb));
      cb = CallbackGroup.emptyCallback();
    }
    group.done();
  }

  /** Retrieve the HTTP password */
  public static void getHttpPassword(String account, AsyncCallback<NativeString> cb) {
    new RestApi("/accounts/").id(account).view("password.http").get(cb);
  }

  /** Generate a new HTTP password */
  public static void generateHttpPassword(String account, AsyncCallback<NativeString> cb) {
    HttpPasswordInput in = HttpPasswordInput.create();
    in.generate(true);
    new RestApi("/accounts/").id(account).view("password.http").put(in, cb);
  }

  /** Clear HTTP password */
  public static void clearHttpPassword(String account, AsyncCallback<VoidResult> cb) {
    new RestApi("/accounts/").id(account).view("password.http").delete(cb);
  }

  /** Enter a contributor agreement */
  public static void enterAgreement(String account, String name, AsyncCallback<NativeString> cb) {
    AgreementInput in = AgreementInput.create();
    in.name(name);
    new RestApi("/accounts/").id(account).view("agreements").put(in, cb);
  }

  private static JsArray<ProjectWatchInfo> projectWatchArrayFromSet(Set<ProjectWatchInfo> set) {
    JsArray<ProjectWatchInfo> jsArray = JsArray.createArray().cast();
    for (ProjectWatchInfo p : set) {
      jsArray.push(p);
    }
    return jsArray;
  }

  private static class AgreementInput extends JavaScriptObject {
    final native void name(String n) /*-{ if(n)this.name=n; }-*/;

    static AgreementInput create() {
      return createObject().cast();
    }

    protected AgreementInput() {}
  }

  private static class HttpPasswordInput extends JavaScriptObject {
    final native void generate(boolean g) /*-{ if(g)this.generate=g; }-*/;

    static HttpPasswordInput create() {
      return createObject().cast();
    }

    protected HttpPasswordInput() {}
  }

  private static class UsernameInput extends JavaScriptObject {
    final native void username(String u) /*-{ if(u)this.username=u; }-*/;

    static UsernameInput create() {
      return createObject().cast();
    }

    protected UsernameInput() {}
  }

  private static class AccountNameInput extends JavaScriptObject {
    final native void name(String n) /*-{ if(n)this.name=n; }-*/;

    static AccountNameInput create() {
      return createObject().cast();
    }

    protected AccountNameInput() {}
  }

  public static void addGpgKey(
      String account, String armored, AsyncCallback<NativeMap<GpgKeyInfo>> cb) {
    new RestApi("/accounts/").id(account).view("gpgkeys").post(GpgKeysInput.add(armored), cb);
  }

  public static void deleteGpgKeys(
      String account, Iterable<String> fingerprints, AsyncCallback<NativeMap<GpgKeyInfo>> cb) {
    new RestApi("/accounts/")
        .id(account)
        .view("gpgkeys")
        .post(GpgKeysInput.delete(fingerprints), cb);
  }

  /** List contributor agreements */
  public static void getAgreements(String account, AsyncCallback<JsArray<AgreementInfo>> cb) {
    new RestApi("/accounts/").id(account).view("agreements").get(cb);
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

    private static native GpgKeysInput createWithDelete(JsArrayString fingerprints) /*-{
      return {'delete': fingerprints};
    }-*/;

    protected GpgKeysInput() {}
  }
}
