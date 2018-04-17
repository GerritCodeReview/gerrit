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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritInstanceName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.ssh.SshAdvertisedAddresses;
import com.google.gerrit.server.validators.OutgoingEmailValidationListener;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.tofu.SoyTofu;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

public class EmailArguments {
  final GitRepositoryManager server;
  final ProjectAccessor.Factory projectAccessorFactory;
  final PermissionBackend permissionBackend;
  final GroupBackend groupBackend;
  final AccountCache accountCache;
  final PatchListCache patchListCache;
  final ApprovalsUtil approvalsUtil;
  final FromAddressGenerator fromAddressGenerator;
  final EmailSender emailSender;
  final PatchSetInfoFactory patchSetInfoFactory;
  final IdentifiedUser.GenericFactory identifiedUserFactory;
  final ChangeNotes.Factory changeNotesFactory;
  final AnonymousUser anonymousUser;
  final String anonymousCowardName;
  final PersonIdent gerritPersonIdent;
  final Provider<String> urlProvider;
  final AllProjectsName allProjectsName;
  final List<String> sshAddresses;
  final SitePaths site;

  final ChangeQueryBuilder queryBuilder;
  final Provider<ReviewDb> db;
  final ChangeData.Factory changeDataFactory;
  final SoyTofu soyTofu;
  final EmailSettings settings;
  final DynamicSet<OutgoingEmailValidationListener> outgoingEmailValidationListeners;
  final Provider<InternalAccountQuery> accountQueryProvider;
  final OutgoingEmailValidator validator;
  final boolean addInstanceNameInSubject;
  final Provider<String> instanceNameProvider;

  @Inject
  EmailArguments(
      GitRepositoryManager server,
      ProjectAccessor.Factory projectAccessorFactory,
      PermissionBackend permissionBackend,
      GroupBackend groupBackend,
      AccountCache accountCache,
      PatchListCache patchListCache,
      ApprovalsUtil approvalsUtil,
      FromAddressGenerator fromAddressGenerator,
      EmailSender emailSender,
      PatchSetInfoFactory patchSetInfoFactory,
      GenericFactory identifiedUserFactory,
      ChangeNotes.Factory changeNotesFactory,
      AnonymousUser anonymousUser,
      @AnonymousCowardName String anonymousCowardName,
      GerritPersonIdentProvider gerritPersonIdentProvider,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      AllProjectsName allProjectsName,
      ChangeQueryBuilder queryBuilder,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      @MailTemplates SoyTofu soyTofu,
      EmailSettings settings,
      @SshAdvertisedAddresses List<String> sshAddresses,
      SitePaths site,
      DynamicSet<OutgoingEmailValidationListener> outgoingEmailValidationListeners,
      Provider<InternalAccountQuery> accountQueryProvider,
      OutgoingEmailValidator validator,
      @GerritInstanceName Provider<String> instanceNameProvider,
      @GerritServerConfig Config cfg) {
    this.server = server;
    this.projectAccessorFactory = projectAccessorFactory;
    this.permissionBackend = permissionBackend;
    this.groupBackend = groupBackend;
    this.accountCache = accountCache;
    this.patchListCache = patchListCache;
    this.approvalsUtil = approvalsUtil;
    this.fromAddressGenerator = fromAddressGenerator;
    this.emailSender = emailSender;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.anonymousUser = anonymousUser;
    this.anonymousCowardName = anonymousCowardName;
    this.gerritPersonIdent = gerritPersonIdentProvider.get();
    this.urlProvider = urlProvider;
    this.allProjectsName = allProjectsName;
    this.queryBuilder = queryBuilder;
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.soyTofu = soyTofu;
    this.settings = settings;
    this.sshAddresses = sshAddresses;
    this.site = site;
    this.outgoingEmailValidationListeners = outgoingEmailValidationListeners;
    this.accountQueryProvider = accountQueryProvider;
    this.validator = validator;
    this.instanceNameProvider = instanceNameProvider;

    this.addInstanceNameInSubject = cfg.getBoolean("sendemail", "addInstanceNameInSubject", false);
  }
}
