// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.api.accounts;

import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public interface AccountApi {
  AccountInfo get() throws RestApiException;

  boolean getActive() throws RestApiException;

  void setActive(boolean active) throws RestApiException;

  String getAvatarUrl(int size) throws RestApiException;

  GeneralPreferencesInfo getPreferences() throws RestApiException;

  GeneralPreferencesInfo setPreferences(GeneralPreferencesInfo in) throws RestApiException;

  DiffPreferencesInfo getDiffPreferences() throws RestApiException;

  DiffPreferencesInfo setDiffPreferences(DiffPreferencesInfo in) throws RestApiException;

  EditPreferencesInfo getEditPreferences() throws RestApiException;

  EditPreferencesInfo setEditPreferences(EditPreferencesInfo in) throws RestApiException;

  List<ProjectWatchInfo> getWatchedProjects() throws RestApiException;

  List<ProjectWatchInfo> setWatchedProjects(List<ProjectWatchInfo> in) throws RestApiException;

  void deleteWatchedProjects(List<ProjectWatchInfo> in) throws RestApiException;

  void starChange(String changeId) throws RestApiException;

  void unstarChange(String changeId) throws RestApiException;

  void setStars(String changeId, StarsInput input) throws RestApiException;

  SortedSet<String> getStars(String changeId) throws RestApiException;

  List<ChangeInfo> getStarredChanges() throws RestApiException;

  List<EmailInfo> getEmails() throws RestApiException;

  void addEmail(EmailInput input) throws RestApiException;

  void deleteEmail(String email) throws RestApiException;

  void setStatus(String status) throws RestApiException;

  List<SshKeyInfo> listSshKeys() throws RestApiException;

  SshKeyInfo addSshKey(String key) throws RestApiException;

  void deleteSshKey(int seq) throws RestApiException;

  Map<String, GpgKeyInfo> listGpgKeys() throws RestApiException;

  Map<String, GpgKeyInfo> putGpgKeys(List<String> add, List<String> remove) throws RestApiException;

  GpgKeyApi gpgKey(String id) throws RestApiException;

  List<AgreementInfo> listAgreements() throws RestApiException;

  void signAgreement(String agreementName) throws RestApiException;

  void index() throws RestApiException;

  List<AccountExternalIdInfo> getExternalIds() throws RestApiException;

  void deleteExternalIds(List<String> externalIds) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements AccountApi {
    @Override
    public AccountInfo get() {
      throw new NotImplementedException();
    }

    @Override
    public boolean getActive() {
      throw new NotImplementedException();
    }

    @Override
    public void setActive(boolean active) {
      throw new NotImplementedException();
    }

    @Override
    public String getAvatarUrl(int size) {
      throw new NotImplementedException();
    }

    @Override
    public GeneralPreferencesInfo getPreferences() {
      throw new NotImplementedException();
    }

    @Override
    public GeneralPreferencesInfo setPreferences(GeneralPreferencesInfo in) {
      throw new NotImplementedException();
    }

    @Override
    public DiffPreferencesInfo getDiffPreferences() {
      throw new NotImplementedException();
    }

    @Override
    public DiffPreferencesInfo setDiffPreferences(DiffPreferencesInfo in) {
      throw new NotImplementedException();
    }

    @Override
    public EditPreferencesInfo getEditPreferences() {
      throw new NotImplementedException();
    }

    @Override
    public EditPreferencesInfo setEditPreferences(EditPreferencesInfo in) {
      throw new NotImplementedException();
    }

    @Override
    public List<ProjectWatchInfo> getWatchedProjects() {
      throw new NotImplementedException();
    }

    @Override
    public List<ProjectWatchInfo> setWatchedProjects(List<ProjectWatchInfo> in) {
      throw new NotImplementedException();
    }

    @Override
    public void deleteWatchedProjects(List<ProjectWatchInfo> in) {
      throw new NotImplementedException();
    }

    @Override
    public void starChange(String changeId) {
      throw new NotImplementedException();
    }

    @Override
    public void unstarChange(String changeId) {
      throw new NotImplementedException();
    }

    @Override
    public void setStars(String changeId, StarsInput input) {
      throw new NotImplementedException();
    }

    @Override
    public SortedSet<String> getStars(String changeId) {
      throw new NotImplementedException();
    }

    @Override
    public List<ChangeInfo> getStarredChanges() {
      throw new NotImplementedException();
    }

    @Override
    public List<EmailInfo> getEmails() {
      throw new NotImplementedException();
    }

    @Override
    public void addEmail(EmailInput input) {
      throw new NotImplementedException();
    }

    @Override
    public void deleteEmail(String email) {
      throw new NotImplementedException();
    }

    @Override
    public void setStatus(String status) {
      throw new NotImplementedException();
    }

    @Override
    public List<SshKeyInfo> listSshKeys() {
      throw new NotImplementedException();
    }

    @Override
    public SshKeyInfo addSshKey(String key) {
      throw new NotImplementedException();
    }

    @Override
    public void deleteSshKey(int seq) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, GpgKeyInfo> putGpgKeys(List<String> add, List<String> remove) {
      throw new NotImplementedException();
    }

    @Override
    public GpgKeyApi gpgKey(String id) {
      throw new NotImplementedException();
    }

    @Override
    public Map<String, GpgKeyInfo> listGpgKeys() {
      throw new NotImplementedException();
    }

    @Override
    public List<AgreementInfo> listAgreements() {
      throw new NotImplementedException();
    }

    @Override
    public void signAgreement(String agreementName) {
      throw new NotImplementedException();
    }

    @Override
    public void index() {
      throw new NotImplementedException();
    }

    @Override
    public List<AccountExternalIdInfo> getExternalIds() {
      throw new NotImplementedException();
    }

    @Override
    public void deleteExternalIds(List<String> externalIds) {
      throw new NotImplementedException();
    }
  }
}
