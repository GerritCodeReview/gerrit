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

import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-table-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-account-link/gr-account-link';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../gr-create-pointer-dialog/gr-create-pointer-dialog';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-detail-list_html';
import {ListViewMixin} from '../../../mixins/gr-list-view-mixin/gr-list-view-mixin';
import {encodeURL} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import { ErrorCallback, RestApiService } from '../../../services/services/gr-rest-api/gr-rest-api';
import { GrOverlay } from '../../shared/gr-overlay/gr-overlay';
import { GrCreatePointerDialog } from '../gr-create-pointer-dialog/gr-create-pointer-dialog';
import { RepoName, ProjectInfo, BranchInfo, GitRef, TagInfo } from '../../../types/common';
import { AppElementRepoParams } from '../../gr-app-types';
import { PolymerDomRepeatEvent } from '../../../types/types';

enum DETAIL_TYPES {
  BRANCHES = 'branches',
  TAGS = 'tags',
};

const PGP_START = '-----BEGIN PGP SIGNATURE-----';

export interface GrRepoDetailList {
  $: {
    restAPI: RestApiService & Element;
    overlay: GrOverlay;
    createOverlay: GrOverlay;
    createNewModal: GrCreatePointerDialog
  };
}
@customElement('gr-repo-detail-list')
export class GrRepoDetailList extends ListViewMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object, observer: '_paramsChanged'})
  params?: AppElementRepoParams;

  @property({type: String})
  detailType?: string;

  @property({type: Boolean})
  _editing = false;

  @property({type: Boolean})
  _isOwner = false;

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Number})
  _offset?: number;

  @property({type: Object})
  _repo?: RepoName;

  @property({type: Array})
  _items?: BranchInfo[] | TagInfo[];

  @property({type: Array, computed: 'computeShownItems(_items)'})
  _shownItems?: unknown;

  @property({type: Number})
  _itemsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter?: string;

  @property({type: String})
  _refName?: string;

  @property({type: Boolean})
  _hasNewItemName?: boolean;

  @property({type: Boolean})
  _isEditing?: boolean;

  @property({type: String})
  _revisedRef?: string;

  _determineIfOwner(repo: RepoName) {
    return this.$.restAPI
      .getRepoAccess(repo)
      .then(access => (this._isOwner = !!access && !!access[repo].is_owner));
  }

  _paramsChanged(params: AppElementRepoParams) {
    if (!params || !params.repo) {
      return;
    }

    this._repo = params.repo;

    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
      if (loggedIn && this._repo) {
        this._determineIfOwner(this._repo);
      }
    });

    this.detailType = params.detail;

    this._filter = this.getFilterValue(params);
    this._offset = this.getOffsetValue(params);
    if (!this.detailType)
      return;

    return this._getItems(
      this._filter,
      this._repo,
      this._itemsPerPage,
      this._offset,
      this.detailType
    );
  }

  _getItems(filter: string, repo: RepoName, itemsPerPage: number, offset: number, detailType: string) {
    this._loading = true;
    this._items = [];
    flush();
    const errFn: ErrorCallback = response => {
      this.dispatchEvent(
        new CustomEvent('page-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    };
    if (detailType === DETAIL_TYPES.BRANCHES) {
      return this.$.restAPI
        .getRepoBranches(filter, repo, itemsPerPage, offset, errFn)
        .then(items => {
          if (!items) {
            return;
          }
          this._items = items;
          this._loading = false;
        });
    } else if (detailType === DETAIL_TYPES.TAGS) {
      return this.$.restAPI
        .getRepoTags(filter, repo, itemsPerPage, offset, errFn)
        .then(items => {
          if (!items) {
            return;
          }
          this._items = items;
          this._loading = false;
        });
    }
  }

  _getPath(repo: RepoName) {
    return `/admin/repos/${encodeURL(repo, false)},` + `${this.detailType}`;
  }

  _computeWeblink(repo: ProjectInfo) {
    if (!repo.web_links) {
      return '';
    }
    const webLinks = repo.web_links;
    return webLinks.length ? webLinks : null;
  }

  _computeMessage(message?: string) {
    if (!message) {
      return;
    }
    // Strip PGP info.
    return message.split(PGP_START)[0];
  }

  _stripRefs(item: GitRef, detailType?: string) {
    if (detailType === DETAIL_TYPES.BRANCHES) {
      return item.replace('refs/heads/', '');
    } else if (detailType === DETAIL_TYPES.TAGS) {
      return item.replace('refs/tags/', '');
    }
    throw new Error('unknown detailType');
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _computeEditingClass(isEditing: boolean) {
    return isEditing ? 'editing' : '';
  }

  _computeCanEditClass(ref: GitRef, detailType: string, isOwner: boolean) {
    return isOwner && this._stripRefs(ref, detailType) === 'HEAD'
      ? 'canEdit'
      : '';
  }

  _handleEditRevision(e: PolymerDomRepeatEvent<BranchInfo | TagInfo>) {
    this._revisedRef = e.model.get('item.revision');
    this._isEditing = true;
  }

  _handleCancelRevision() {
    this._isEditing = false;
  }

  _handleSaveRevision(e: PolymerDomRepeatEvent<>) {
    this._setRepoHead(this._repo, this._revisedRef, e);
  }

  _setRepoHead(repo: RepoName, ref: GitRef, e) {
    return this.$.restAPI.setRepoHead(repo, ref).then(res => {
      if (res.status < 400) {
        this._isEditing = false;
        e.model.set('item.revision', ref);
        // This is needed to refresh _items property with fresh data,
        // specifically can_delete from the json response.
        this._getItems(
          this._filter,
          this._repo,
          this._itemsPerPage,
          this._offset,
          this.detailType
        );
      }
    });
  }

  _computeItemName(detailType: string) {
    if (detailType === DETAIL_TYPES.BRANCHES) {
      return 'Branch';
    } else if (detailType === DETAIL_TYPES.TAGS) {
      return 'Tag';
    }
    throw new Error('unknown detailType');
  }

  _handleDeleteItemConfirm() {
    this.$.overlay.close();
    if (this.detailType === DETAIL_TYPES.BRANCHES) {
      return this.$.restAPI
        .deleteRepoBranches(this._repo, this._refName)
        .then(itemDeleted => {
          if (itemDeleted.status === 204) {
            this._getItems(
              this._filter,
              this._repo,
              this._itemsPerPage,
              this._offset,
              this.detailType
            );
          }
        });
    } else if (this.detailType === DETAIL_TYPES.TAGS) {
      return this.$.restAPI
        .deleteRepoTags(this._repo, this._refName)
        .then(itemDeleted => {
          if (itemDeleted.status === 204) {
            this._getItems(
              this._filter,
              this._repo,
              this._itemsPerPage,
              this._offset,
              this.detailType
            );
          }
        });
    }
  }

  _handleConfirmDialogCancel() {
    this.$.overlay.close();
  }

  _handleDeleteItem(e) {
    const name = this._stripRefs(e.model.get('item.ref'), this.detailType);
    if (!name) {
      return;
    }
    this._refName = name;
    this.$.overlay.open();
  }

  _computeHideDeleteClass(owner: boolean, canDelete: boolean) {
    if (canDelete || owner) {
      return 'show';
    }

    return '';
  }

  _handleCreateItem() {
    this.$.createNewModal.handleCreateItem();
    this._handleCloseCreate();
  }

  _handleCloseCreate() {
    this.$.createOverlay.close();
  }

  _handleCreateClicked() {
    this.$.createOverlay.open();
  }

  _hideIfBranch(type: string) {
    if (type === DETAIL_TYPES.BRANCHES) {
      return 'hideItem';
    }

    return '';
  }

  _computeHideTagger(tagger: GitPersonInfo) {
    return tagger ? '' : 'hide';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-detail-list': GrRepoDetailList;
  }
}
