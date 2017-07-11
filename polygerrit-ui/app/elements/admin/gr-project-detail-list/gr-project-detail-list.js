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

  const DETAIL_TYPES = {
    BRANCHES: 'branches',
    TAGS: 'tags',
  };

  Polymer({
    is: 'gr-project-detail-list',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },
      /**
       * The kind of detail we are displaying, possibilities are determined by
       * the const DETAIL_TYPES.
       */
      detailType: String,

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,
      _project: Object,
      _items: Array,
      /**
       * Because  we request one more than the projectsPerPage, _shownProjects
       * maybe one less than _projects.
       * */
      _shownItems: {
        type: Array,
        computed: 'computeShownItems(_items)',
      },
      _itemsPerPage: {
        type: Number,
        value: 25,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: String,
      _isOwner: {
        type: Boolean,
        value: true,
      },
      _refName: Object,
      _deleted: Boolean,
    },

    behaviors: [
      Gerrit.ListViewBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _paramsChanged(params) {
      this._loading = true;
      if (!params || !params.project) { return; }

      this._project = params.project;
      this.detailType = params.detailType;

      this._filter = this.getFilterValue(params);
      this._offset = this.getOffsetValue(params);

      this.$.restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          this.$.restAPI.getProjectAccess(this._project).then(access => {
            // If the user is not an owner, is_owner is not a property.
            this._isOwner = !access[this._project].is_owner;
          });
        }
      });

      return this._getItems(this._filter, this._project,
          this._itemsPerPage, this._offset, this.detailType);
    },

    _getItems(filter, project, itemsPerPage, offset, detailType) {
      this._items = [];
      Polymer.dom.flush();
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return this.$.restAPI.getProjectBranches(
            filter, project, itemsPerPage, offset) .then(items => {
              if (!items) { return; }
              this._items = items;
              this._loading = false;
            });
      } else if (detailType === DETAIL_TYPES.TAGS) {
        return this.$.restAPI.getProjectTags(
            filter, project, itemsPerPage, offset) .then(items => {
              if (!items) { return; }
              this._items = items;
              this._loading = false;
            });
      }
    },

    _getPath(project) {
      return `/admin/projects/${this.encodeURL(project, false)},` +
          `${this.detailType}`;
    },

    _computeWeblink(project) {
      if (!project.web_links) { return ''; }
      const webLinks = project.web_links;
      return webLinks.length ? webLinks : null;
    },

    computeBrowserClass(detailType) {
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return 'show';
      }
      return '';
    },

    _stripRefs(item, detailType) {
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return item.replace('refs/heads/', '');
      } else if (detailType === DETAIL_TYPES.TAGS) {
        return item.replace('refs/tags/', '');
      }
    },

    _itemName(detailType) {
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return 'Branch';
      } else if (detailType === DETAIL_TYPES.TAGS) {
        return 'Tag';
      }
    },

    _handleDeleteItemConfirm() {
      this.$.overlay.close();
      if (this.detailType === DETAIL_TYPES.BRANCHES) {
        return this.$.restAPI.deleteProjectBranches(this._project,
            this._refName)
            .then(itemDeleted => {
              if (itemDeleted.status === 204) {
                location.reload();
              }
            });
      } else if (this.detailType === DETAIL_TYPES.TAGS) {
        return this.$.restAPI.deleteProjectTags(this._project,
            this._refName)
            .then(itemDeleted => {
              if (itemDeleted.status === 204) {
                location.reload();
              }
            });
      }
    },

    _handleDeleteItem(e) {
      const name = e.target.dataItem;
      if (!name) { return; }
      this._refName = name;
      this.$.overlay.close();
      this.$.overlay.open();
    },

    _computeHideClass(item, item2) {
      if (!item && item2) {
        return 'show';
      }

      if (item2) {
        return 'show';
      }

      if (!item) {
        return 'show';
      }

      return '';
    },
  });
})();
