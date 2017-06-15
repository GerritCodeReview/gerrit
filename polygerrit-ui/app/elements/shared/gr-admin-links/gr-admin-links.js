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

  const ADMIN_LINKS = [
    {
      url: '/admin/projects',
      name: 'Projects',
      viewableToAll: true,
    },
    {
      url: '/admin/groups',
      name: 'Groups',
    },
    {
      url: '/admin/plugins',
      name: 'Plugins',
      capability: 'viewPlugins',
    },
  ];

  const NESTED_ADMIN_LINKS = [
    {
      url: '/admin/create-project',
      name: 'Create Project',
      capability: 'createProject',
      section: 'Projects',
    },
    {
      url: '/admin/create-group',
      name: 'Create Group',
      capability: 'createGroup',
      section: 'Groups',
    },
  ];

  Polymer({
    is: 'gr-admin-links',

    properties: {
      _account: Object,
      _adminLinks: {
        type: Array,
        value() { return []; },
      },
      _nestedLinks: {
        type: Array,
        value() { return []; },
      },
      computedAdminTopLinks: {
        type: Array,
        computed: '_computeAdminTopLinks(_adminLinks)',
        notify: true,
      },
      computedAdminSideLinks: {
        type: Array,
        computed: '_computeAdminSideLinks(computedAdminTopLinks, _nestedLinks)',
        notify: true,
      },
    },

    observers: [
      '_accountLoaded(_account)',
    ],

    attached() {
      this._loadAccount();
    },

    reload() {
      this._loadAccount();
    },

    _computeAdminTopLinks(adminLinks) {
      if (!adminLinks || !adminLinks.length) {
        return ADMIN_LINKS.filter(link => link.viewableToAll);
      }
      return adminLinks;
    },

    _computeAdminSideLinks(computedAdminTopLinks, nestedLinks) {
      return computedAdminTopLinks.map((item) => {
        let section = {
          'name': item.name,
          links: [{name: 'List', 'url': item.url}],
        };
        let newLinks = nestedLinks.filter((group) => {
          return group.section === section.name;
        });
        section.links = section.links.concat(newLinks);
        return section;
      });
    },

    _loadAccount() {
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
      });
    },

    _accountLoaded(account) {
      if (!account) { return; }
      this._loadAccountCapabilities();
    },

    _loadAccountCapabilities() {
      const params = ['createProject', 'createGroup', 'viewPlugins'];
      return this.$.restAPI.getAccountCapabilities(params)
          .then(capabilities => {
            this._adminLinks = ADMIN_LINKS.filter(link => {
              return !link.capability ||
              capabilities.hasOwnProperty(link.capability);
            });
            this._nestedLinks = NESTED_ADMIN_LINKS.filter(link => {
              return !link.capability ||
              capabilities.hasOwnProperty(link.capability);
            });
          });
    },
  });
})();
