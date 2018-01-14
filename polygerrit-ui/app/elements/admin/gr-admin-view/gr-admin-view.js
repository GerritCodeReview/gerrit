// Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  Polymer({
    is: 'gr-admin-view',

    properties: {
      /** @type {?} */
      params: Object,

      _showGroup: Boolean,
      _showGroupAuditLog: Boolean,
      _showGroupList: Boolean,
      _showGroupMembers: Boolean,
      _showRepoAccess: Boolean,
      _showRepoCommands: Boolean,
      _showRepoDetailList: Boolean,
      _showRepoMain: Boolean,
      _showRepoList: Boolean,
      _showPluginList: Boolean,
    },

    observers: [
      '_paramsChanged(params)',
    ],

    _paramsChanged(params) {
      const isGroupView = params.view === Gerrit.Nav.View.GROUP;
      const isRepoView = params.view === Gerrit.Nav.View.REPO;
      const isAdminView = params.view === Gerrit.Nav.View.ADMIN;

      this.set('_showGroup', isGroupView && !params.detail);
      this.set('_showGroupAuditLog', isGroupView &&
          params.detail === Gerrit.Nav.GroupDetailView.LOG);
      this.set('_showGroupMembers', isGroupView &&
          params.detail === Gerrit.Nav.GroupDetailView.MEMBERS);

      this.set('_showGroupList', isAdminView &&
          params.adminView === 'gr-admin-group-list');

      this.set('_showRepoAccess', isRepoView &&
          params.detail === Gerrit.Nav.RepoDetailView.ACCESS);
      this.set('_showRepoCommands', isRepoView &&
          params.detail === Gerrit.Nav.RepoDetailView.COMMANDS);
      this.set('_showRepoDetailList', isRepoView &&
          (params.detail === Gerrit.Nav.RepoDetailView.BRANCHES ||
           params.detail === Gerrit.Nav.RepoDetailView.TAGS));
      this.set('_showRepoMain', isRepoView && !params.detail);

      this.set('_showRepoList', isAdminView &&
          params.adminView === 'gr-repo-list');

      this.set('_showPluginList', isAdminView &&
          params.adminView === 'gr-plugin-list');
    },
  });
})();
