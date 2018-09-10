/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.js';

import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-list-view/gr-list-view.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-create-pointer-dialog/gr-create-pointer-dialog.js';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog.js';

const DETAIL_TYPES = {
  BRANCHES: 'branches',
  TAGS: 'tags',
};

const PGP_START = '-----BEGIN PGP SIGNATURE-----';

Polymer({
  _template: Polymer.html`
    <style include="gr-form-styles"></style>
    <style include="gr-table-styles"></style>
    <style include="shared-styles">
      .tags td.name {
        min-width: 25em;
      }
      td.name,
      td.revision,
      td.message {
        word-break: break-word;
      }
      td.revision.tags {
        width: 27em;
      }
      td.message,
      td.tagger {
        max-width: 15em;
      }
      .editing .editItem {
        display: inherit;
      }
      .editItem,
      .editing .editBtn,
      .canEdit .revisionNoEditing,
      .editing .revisionWithEditing,
      .revisionEdit,
      .hideItem {
        display: none;
      }
      .revisionEdit gr-button {
        margin-left: .6em;
      }
      .editBtn {
        margin-left: 1em;
      }
      .canEdit .revisionEdit{
        align-items: center;
        display: flex;
        line-height: 1;
      }
      .deleteButton:not(.show) {
        display: none;
      }
      .tagger.hide {
        display: none;
      }
    </style>
    <style include="gr-table-styles"></style>
    <gr-list-view create-new="[[_loggedIn]]" filter="[[_filter]]" items-per-page="[[_itemsPerPage]]" items="[[_items]]" loading="[[_loading]]" offset="[[_offset]]" on-create-clicked="_handleCreateClicked" path="[[_getPath(_repo, detailType)]]">
      <table id="list" class="genericList gr-form-styles">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Name</th>
          <th class="revision topHeader">Revision</th>
          <th class\$="message topHeader [[_hideIfBranch(detailType)]]">
            Message</th>
          <th class\$="tagger topHeader [[_hideIfBranch(detailType)]]">
            Tagger</th>
          <th class="repositoryBrowser topHeader">
            Repository Browser</th>
          <th class="delete topHeader"></th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownItems]]">
            <tr class="table">
              <td class\$="[[detailType]] name">[[_stripRefs(item.ref, detailType)]]</td>
              <td class\$="[[detailType]] revision [[_computeCanEditClass(item.ref, detailType, _isOwner)]]">
                <span class="revisionNoEditing">
                  [[item.revision]]
                </span>
                <span class\$="revisionEdit [[_computeEditingClass(_isEditing)]]">
                  <span class="revisionWithEditing">
                    [[item.revision]]
                  </span>
                  <gr-button link="" on-tap="_handleEditRevision" class="editBtn">
                    edit
                  </gr-button>
                  <input is="iron-input" bind-value="{{_revisedRef}}" class="editItem">
                  <gr-button link="" on-tap="_handleCancelRevision" class="cancelBtn editItem">
                    Cancel
                  </gr-button>
                  <gr-button link="" on-tap="_handleSaveRevision" class="saveBtn editItem" disabled="[[!_revisedRef]]">
                    Save
                  </gr-button>
                </span>
              </td>
              <td class\$="message [[_hideIfBranch(detailType)]]">
                [[_computeMessage(item.message)]]
              </td>
              <td class\$="tagger [[_hideIfBranch(detailType)]]">
                <div class\$="tagger [[_computeHideTagger(item.tagger)]]">
                  <gr-account-link account="[[item.tagger]]">
                  </gr-account-link>
                  (<gr-date-formatter has-tooltip="" date-str="[[item.tagger.date]]">
                  </gr-date-formatter>)
                </div>
              </td>
              <td class="repositoryBrowser">
                <template is="dom-repeat" items="[[_computeWeblink(item)]]" as="link">
                  <a href\$="[[link.url]]" class="webLink" rel="noopener" target="_blank">
                    ([[link.name]])
                  </a>
                </template>
              </td>
              <td class="delete">
                <gr-button link="" class\$="deleteButton [[_computeHideDeleteClass(_isOwner, item.can_delete)]]" on-tap="_handleDeleteItem">
                  Delete
                </gr-button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <gr-overlay id="overlay" with-backdrop="">
        <gr-confirm-delete-item-dialog class="confirmDialog" on-confirm="_handleDeleteItemConfirm" on-cancel="_handleConfirmDialogCancel" item="[[_refName]]" item-type="[[detailType]]"></gr-confirm-delete-item-dialog>
      </gr-overlay>
    </gr-list-view>
    <gr-overlay id="createOverlay" with-backdrop="">
      <gr-dialog id="createDialog" disabled="[[!_hasNewItemName]]" confirm-label="Create" on-confirm="_handleCreateItem" on-cancel="_handleCloseCreate">
        <div class="header" slot="header">
          Create [[_computeItemName(detailType)]]
        </div>
        <div class="main" slot="main">
          <gr-create-pointer-dialog id="createNewModal" detail-type="[[_computeItemName(detailType)]]" has-new-item-name="{{_hasNewItemName}}" item-detail="[[detailType]]" repo-name="[[_repo]]"></gr-create-pointer-dialog>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

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
    _isEditing: Boolean,
    _revisedRef: String,
  },

  behaviors: [
    Gerrit.ListViewBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  _determineIfOwner(repo) {
    return this.$.restAPI.getRepoAccess(repo)
        .then(access =>
              this._isOwner = access && access[repo].is_owner);
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

  _hideIfBranch(type) {
    if (type === DETAIL_TYPES.BRANCHES) {
      return 'hideItem';
    }

    return '';
  },

  _computeHideTagger(tagger) {
    return tagger ? '' : 'hide';
  }
});
