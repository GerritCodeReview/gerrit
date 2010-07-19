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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.inject.Inject;
import com.google.inject.Provider;

import javax.annotation.Nullable;

class EmailArguments {
  final GitRepositoryManager server;
  final ProjectCache projectCache;
  final AccountCache accountCache;
  final PatchListCache patchListCache;
  final FromAddressGenerator fromAddressGenerator;
  final EmailSender emailSender;
  final PatchSetInfoFactory patchSetInfoFactory;
  final IdentifiedUser.GenericFactory identifiedUserFactory;
  final Provider<String> urlProvider;
  final Project.NameKey wildProject;

  final ChangeQueryBuilder.Factory queryBuilder;
  final Provider<ChangeQueryRewriter> queryRewriter;
  final Provider<ReviewDb> db;

  @Inject
  EmailArguments(GitRepositoryManager server, ProjectCache projectCache,
      AccountCache accountCache, PatchListCache patchListCache,
      FromAddressGenerator fromAddressGenerator, EmailSender emailSender,
      PatchSetInfoFactory patchSetInfoFactory,
      GenericFactory identifiedUserFactory,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      @WildProjectName Project.NameKey wildProject,
      ChangeQueryBuilder.Factory queryBuilder,
      Provider<ChangeQueryRewriter> queryRewriter, Provider<ReviewDb> db) {
    this.server = server;
    this.projectCache = projectCache;
    this.accountCache = accountCache;
    this.patchListCache = patchListCache;
    this.fromAddressGenerator = fromAddressGenerator;
    this.emailSender = emailSender;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.urlProvider = urlProvider;
    this.wildProject = wildProject;
    this.queryBuilder = queryBuilder;
    this.queryRewriter = queryRewriter;
    this.db = db;
  }
}
