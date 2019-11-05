/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  const DETAIL_TYPES = {
    BRANCHES: 'branches',
    TAGS: 'tags',
  };

  const PGP_START = '-----BEGIN PGP SIGNATURE-----';

  Polymer({
    is: 'gr-repo-detail-list',

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
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      /**
       * Offset of currently visible query results.
       */
      _offset: Number,
      _repo: Object,
      _items: Array,
      /**
       * Because  we request one more than the projectsPerPage, _shownProjects
       * maybe one less than _projects.
       */
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
      _isEditing: Boolean,
      _revisedRef: String,
    },

    behaviors: [
      Gerrit.ListViewBehavior,
      Gerrit.FireBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _determineIfOwner(repo) {
      return this.$.restAPI.getRepoAccess(repo)
          .then(access =>
            this._isOwner = access && !!access[repo].is_owner);
    },

    _paramsChanged(params) {
      if (!params || !params.repo) { return; }

      this._repo = params.repo;

      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this._determineIfOwner(this._repo);
        }
      });

      this.detailType = params.detail;

      this._filter = this.getFilterValue(params);
      this._offset = this.getOffsetValue(params);

      return this._getItems(this._filter, this._repo,
          this._itemsPerPage, this._offset, this.detailType);
    },

    _getItems(filter, repo, itemsPerPage, offset, detailType) {
      this._loading = true;
      this._items = [];
      Polymer.dom.flush();
      const errFn = response => {
        this.fire('page-error', {response});
      };
      if (detailType === DETAIL_TYPES.BRANCHES) {
        return this.$.restAPI.getRepoBranches(
            filter, repo, itemsPerPage, offset, errFn).then(items => {
          if (!items) { return; }
          this._items = items;
          this._loading = false;
        });
      } else if (detailType === DETAIL_TYPES.TAGS) {
        return this.$.restAPI.getRepoTags(
            filter, repo, itemsPerPage, offset, errFn).then(items => {
          if (!items) { return; }
          this._items = items;
          this._loading = false;
        });
      }
    },

    _getPath(repo) {
      return `/admin/repos/${this.encodeURL(repo, false)},` +
          `${this.detailType}`;
    },

    _computeWeblink(repo) {
      if (!repo.web_links) { return ''; }
      const webLinks = repo.web_links;
      return webLinks.length ? webLinks : null;
    },

    _computeMessage(message) {
      if (!message) { return; }
      // Strip PGP info.
      return message.split(PGP_START)[0];
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
      this._setRepoHead(this._repo, this._revisedRef, e);
    },

    _setRepoHead(repo, ref, e) {
      return this.$.restAPI.setRepoHead(repo, ref).then(res => {
        if (res.status < 400) {
          this._isEditing = false;
          e.model.set('item.revision', ref);
          // This is needed to refresh _items property with fresh data,
          // specifically can_delete from the json response.
          this._getItems(
              this._filter, this._repo, this._itemsPerPage,
              this._offset, this.detailType);
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
        return this.$.restAPI.deleteRepoBranches(this._repo, this._refName)
            .then(itemDeleted => {
              if (itemDeleted.status === 204) {
                this._getItems(
                    this._filter, this._repo, this._itemsPerPage,
                    this._offset, this.detailType);
              }
            });
      } else if (this.detailType === DETAIL_TYPES.TAGS) {
        return this.$.restAPI.deleteRepoTags(this._repo, this._refName)
            .then(itemDeleted => {
              if (itemDeleted.status === 204) {
                this._getItems(
                    this._filter, this._repo, this._itemsPerPage,
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

    _computeHideDeleteClass(owner, canDelete) {
      if (canDelete || owner) {
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

    _hideIfBranch(type) {
      if (type === DETAIL_TYPES.BRANCHES) {
        return 'hideItem';
      }

      return '';
    },

    _computeHideTagger(tagger) {
      return tagger ? '' : 'hide';
    },
  });
})();
