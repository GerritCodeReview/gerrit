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

      _editing: {
        type: Boolean,
        value: false,
      },
      _isOwner: {
        type: Boolean,
        value: false,
      },
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
      _refName: String,
      _hasNewItemName: Boolean,
    },

    behaviors: [
      Gerrit.ListViewBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _determineIfOwner(project) {
      return this._getLoggedIn()
          .then(loggedIn =>
                loggedIn ? this.$.restAPI.getProjectAccess(project) : null)
          .then(access =>
                this._isOwner = access && access[project].is_owner);
    },

    _paramsChanged(params) {
      if (!params || !params.project) { return; }

      this._project = params.project;
      this.detailType = params.detailType;

      this._determineIfOwner(this._project);

      this._filter = this.getFilterValue(params);
      this._offset = this.getOffsetValue(params);

      return this._getItems(this._filter, this._project,
          this._itemsPerPage, this._offset, this.detailType);
    },

    _getItems(filter, project, itemsPerPage, offset, detailType) {
      this._loading = true;
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

    _stripRefs(item, detailType) {
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return item.replace('refs/heads/', '');
      } else if (detailType === DETAIL_TYPES.TAGS) {
        return item.replace('refs/tags/', '');
      }
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _computeEditingClass(isEditing) {
      return isEditing ? 'editing' : '';
    },

    _computeCanEditClass(ref, detailType, isOwner) {
      return isOwner && this._stripRefs(ref, detailType) === 'HEAD' ?
          'canEdit' : '';
    },

    _handleEditRevision(e) {
      this._revisedRef = e.model.get('item.revision');
      this._isEditing = true;
    },

    _handleCancelRevision() {
      this._isEditing = false;
    },

    _handleSaveRevision(e) {
      this._setProjectHead(this._project, this._revisedRef, e);
    },

    _setProjectHead(project, ref, e) {
      return this.$.restAPI.setProjectHead(project, ref).then(res => {
        if (res.status < 400) {
          this._isEditing = false;
          e.model.set('item.revision', ref);
        }
      });
    },

    _computeItemName(detailType) {
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
                this._getItems(
                    this._filter, this._project, this._itemsPerPage,
                    this._offset, this.detailType);
              }
            });
      } else if (this.detailType === DETAIL_TYPES.TAGS) {
        return this.$.restAPI.deleteProjectTags(this._project,
            this._refName)
            .then(itemDeleted => {
              if (itemDeleted.status === 204) {
                this._getItems(
                    this._filter, this._project, this._itemsPerPage,
                    this._offset, this.detailType);
              }
            });
      }
    },

    _handleConfirmDialogCancel() {
      this.$.overlay.close();
    },

    _handleDeleteItem(e) {
      const name = this._stripRefs(e.model.get('item.ref'), this.detailType);
      if (!name) { return; }
      this._refName = name;
      this.$.overlay.open();
    },

    _computeHideDeleteClass(owner, deleteRef) {
      if (owner && !deleteRef || owner && deleteRef || deleteRef || owner) {
        return 'show';
      }
      return '';
    },

    _handleCreateItem() {
      this.$.createNewModal.handleCreateItem();
      this._handleCloseCreate();
    },

    _handleCloseCreate() {
      this.$.createOverlay.close();
    },

    _handleCreateClicked() {
      this.$.createOverlay.open();
    },
  });
})();
