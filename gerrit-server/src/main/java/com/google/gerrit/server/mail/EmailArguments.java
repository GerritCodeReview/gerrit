// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.ssh.SshAdvertisedAddresses;
import com.google.gerrit.server.validators.OutgoingEmailValidationListener;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.velocity.runtime.RuntimeInstance;
import org.eclipse.jgit.lib.PersonIdent;

import java.util.List;

public class EmailArguments {
  final GitRepositoryManager server;
  final ProjectCache projectCache;
  final GroupBackend groupBackend;
  final GroupIncludeCache groupIncludes;
  final AccountCache accountCache;
  final PatchListCache patchListCache;
  final ApprovalsUtil approvalsUtil;
  final FromAddressGenerator fromAddressGenerator;
  final EmailSender emailSender;
  final PatchSetInfoFactory patchSetInfoFactory;
  final IdentifiedUser.GenericFactory identifiedUserFactory;
  final CapabilityControl.Factory capabilityControlFactory;
  final ChangeNotes.Factory changeNotesFactory;
  final AnonymousUser anonymousUser;
  final String anonymousCowardName;
  final PersonIdent gerritPersonIdent;
  final Provider<String> urlProvider;
  final AllProjectsName allProjectsName;
  final List<String> sshAddresses;

  final ChangeQueryBuilder queryBuilder;
  final Provider<ReviewDb> db;
  final ChangeData.Factory changeDataFactory;
  final RuntimeInstance velocityRuntime;
  final EmailSettings settings;
  final DynamicSet<OutgoingEmailValidationListener> outgoingEmailValidationListeners;
  final StarredChangesUtil starredChangesUtil;
  final AccountIndexCollection accountIndexes;
  final Provider<InternalAccountQuery> accountQueryProvider;

  @Inject
  EmailArguments(GitRepositoryManager server, ProjectCache projectCache,
      GroupBackend groupBackend, GroupIncludeCache groupIncludes,
      AccountCache accountCache,
      PatchListCache patchListCache,
      ApprovalsUtil approvalsUtil,
      FromAddressGenerator fromAddressGenerator,
      EmailSender emailSender, PatchSetInfoFactory patchSetInfoFactory,
      GenericFactory identifiedUserFactory,
      CapabilityControl.Factory capabilityControlFactory,
      ChangeNotes.Factory changeNotesFactory,
      AnonymousUser anonymousUser,
      @AnonymousCowardName String anonymousCowardName,
      GerritPersonIdentProvider gerritPersonIdentProvider,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      AllProjectsName allProjectsName,
      ChangeQueryBuilder queryBuilder,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      RuntimeInstance velocityRuntime,
      EmailSettings settings,
      @SshAdvertisedAddresses List<String> sshAddresses,
      DynamicSet<OutgoingEmailValidationListener> outgoingEmailValidationListeners,
      StarredChangesUtil starredChangesUtil,
      AccountIndexCollection accountIndexes,
      Provider<InternalAccountQuery> accountQueryProvider) {
    this.server = server;
    this.projectCache = projectCache;
    this.groupBackend = groupBackend;
    this.groupIncludes = groupIncludes;
    this.accountCache = accountCache;
    this.patchListCache = patchListCache;
    this.approvalsUtil = approvalsUtil;
    this.fromAddressGenerator = fromAddressGenerator;
    this.emailSender = emailSender;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.capabilityControlFactory = capabilityControlFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.anonymousUser = anonymousUser;
    this.anonymousCowardName = anonymousCowardName;
    this.gerritPersonIdent = gerritPersonIdentProvider.get();
    this.urlProvider = urlProvider;
    this.allProjectsName = allProjectsName;
    this.queryBuilder = queryBuilder;
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.velocityRuntime = velocityRuntime;
    this.settings = settings;
    this.sshAddresses = sshAddresses;
    this.outgoingEmailValidationListeners = outgoingEmailValidationListeners;
    this.starredChangesUtil = starredChangesUtil;
    this.accountIndexes = accountIndexes;
    this.accountQueryProvider = accountQueryProvider;
  }
}
