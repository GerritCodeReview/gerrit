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

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritInstanceName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.ssh.SshAdvertisedAddresses;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.validators.OutgoingEmailValidationListener;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Arguments used for sending notification emails.
 *
 * <p>Notification emails are sent by out by {@link OutgoingEmailNew} . To construct an email class
 * (or its decorators) needs to get various other classes injected. Instead of injecting these
 * classes into the sender classes directly, they only get {@code EmailArguments} injected and
 * {@code EmailArguments} provides them all dependencies that they need.
 *
 * <p>This class is public because plugins need access to it for sending emails.
 */
@Singleton
@UsedAt(UsedAt.Project.PLUGINS_ALL)
public class EmailArguments {
  final GitRepositoryManager server;
  final ProjectCache projectCache;
  final PermissionBackend permissionBackend;
  final GroupBackend groupBackend;
  final AccountCache accountCache;
  final DiffOperations diffOperations;
  final PatchSetUtil patchSetUtil;
  final ApprovalsUtil approvalsUtil;
  final Provider<FromAddressGenerator> fromAddressGenerator;
  final EmailSender emailSender;
  final PatchSetInfoFactory patchSetInfoFactory;
  final IdentifiedUser.GenericFactory identifiedUserFactory;
  final ChangeNotes.Factory changeNotesFactory;
  final Provider<AnonymousUser> anonymousUser;
  final String anonymousCowardName;
  final Provider<PersonIdent> gerritPersonIdent;
  final DynamicItem<UrlFormatter> urlFormatter;
  final AllProjectsName allProjectsName;
  final List<String> sshAddresses;
  final SitePaths site;
  final Provider<ChangeQueryBuilder> queryBuilder;
  final ChangeData.Factory changeDataFactory;
  final Provider<SoySauce> soySauce;
  final EmailSettings settings;
  final DynamicSet<OutgoingEmailValidationListener> outgoingEmailValidationListeners;
  final Provider<InternalAccountQuery> accountQueryProvider;
  final OutgoingEmailValidator validator;
  final boolean addInstanceNameInSubject;
  final Provider<String> instanceNameProvider;
  final Provider<CurrentUser> currentUserProvider;
  final RetryHelper retryHelper;

  @Inject
  EmailArguments(
      GitRepositoryManager server,
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      GroupBackend groupBackend,
      AccountCache accountCache,
      DiffOperations diffOperations,
      PatchSetUtil patchSetUtil,
      ApprovalsUtil approvalsUtil,
      Provider<FromAddressGenerator> fromAddressGenerator,
      EmailSender emailSender,
      PatchSetInfoFactory patchSetInfoFactory,
      GenericFactory identifiedUserFactory,
      ChangeNotes.Factory changeNotesFactory,
      Provider<AnonymousUser> anonymousUser,
      @AnonymousCowardName String anonymousCowardName,
      GerritPersonIdentProvider gerritPersonIdent,
      DynamicItem<UrlFormatter> urlFormatter,
      AllProjectsName allProjectsName,
      Provider<ChangeQueryBuilder> queryBuilder,
      ChangeData.Factory changeDataFactory,
      @MailTemplates Provider<SoySauce> soySauce,
      EmailSettings settings,
      @SshAdvertisedAddresses List<String> sshAddresses,
      SitePaths site,
      DynamicSet<OutgoingEmailValidationListener> outgoingEmailValidationListeners,
      Provider<InternalAccountQuery> accountQueryProvider,
      OutgoingEmailValidator validator,
      @GerritInstanceName Provider<String> instanceNameProvider,
      @GerritServerConfig Config cfg,
      Provider<CurrentUser> currentUserProvider,
      RetryHelper retryHelper) {
    this.server = server;
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.groupBackend = groupBackend;
    this.accountCache = accountCache;
    this.diffOperations = diffOperations;
    this.patchSetUtil = patchSetUtil;
    this.approvalsUtil = approvalsUtil;
    this.fromAddressGenerator = fromAddressGenerator;
    this.emailSender = emailSender;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.changeNotesFactory = changeNotesFactory;
    this.anonymousUser = anonymousUser;
    this.anonymousCowardName = anonymousCowardName;
    this.gerritPersonIdent = gerritPersonIdent;
    this.urlFormatter = urlFormatter;
    this.allProjectsName = allProjectsName;
    this.queryBuilder = queryBuilder;
    this.changeDataFactory = changeDataFactory;
    this.soySauce = soySauce;
    this.settings = settings;
    this.sshAddresses = sshAddresses;
    this.site = site;
    this.outgoingEmailValidationListeners = outgoingEmailValidationListeners;
    this.accountQueryProvider = accountQueryProvider;
    this.validator = validator;
    this.instanceNameProvider = instanceNameProvider;
    this.addInstanceNameInSubject = cfg.getBoolean("sendemail", "addInstanceNameInSubject", false);
    this.currentUserProvider = currentUserProvider;
    this.retryHelper = retryHelper;
  }

  /** Fetch ChangeData for the specified change. */
  public ChangeData newChangeData(Project.NameKey project, Change.Id id) {
    return changeDataFactory.create(project, id);
  }

  /** Fetch ChangeData for specified change and revision. */
  public ChangeData newChangeData(Project.NameKey project, Change.Id id, ObjectId metaId) {
    return changeDataFactory.create(changeNotesFactory.createChecked(project, id, metaId));
  }
}
