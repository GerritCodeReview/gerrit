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
import '../gr-create-pointer-dialog/gr-create-pointer-dialog';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-detail-list_html';
import {
  computeLoadingClass,
  computeShownItems,
  getFilterValue,
  getOffsetValue,
} from '../../../utils/list-util';
import {encodeURL} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrCreatePointerDialog} from '../gr-create-pointer-dialog/gr-create-pointer-dialog';
import {
  RepoName,
  ProjectInfo,
  BranchInfo,
  GitRef,
  TagInfo,
  GitPersonInfo,
} from '../../../types/common';
import {AppElementRepoParams} from '../../gr-app-types';
import {PolymerDomRepeatEvent} from '../../../types/types';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation';
import {firePageError} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';

const PGP_START = '-----BEGIN PGP SIGNATURE-----';

export interface GrRepoDetailList {
  $: {
    overlay: GrOverlay;
    createOverlay: GrOverlay;
    createNewModal: GrCreatePointerDialog;
  };
}

@customElement('gr-repo-detail-list')
export class GrRepoDetailList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object, observer: '_paramsChanged'})
  params?: AppElementRepoParams;

  @property({type: String})
  detailType?: RepoDetailView.BRANCHES | RepoDetailView.TAGS;

  @property({type: Boolean})
  _editing = false;

  @property({type: Boolean})
  _isOwner = false;

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Number})
  _offset?: number;

  @property({type: String})
  _repo?: RepoName;

  @property({type: Array})
  _items?: BranchInfo[] | TagInfo[];

  // _shownItems should be BranchInfo[] | TagInfo[],
  // but TS incorrectly assumes that in the loop for(const item of _shownItems)
  // item has type BranchInfo, not BranchInfo | TagInfo.
  @property({type: Array, computed: 'computeShownItems(_items)'})
  _shownItems?: Array<BranchInfo | TagInfo>;

  @property({type: Number})
  _itemsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter?: string;

  @property({type: String})
  _refName?: GitRef;

  @property({type: Boolean})
  _hasNewItemName = false;

  @property({type: Boolean})
  _isEditing = false;

  @property({type: String})
  _revisedRef?: GitRef;

  private readonly restApiService = appContext.restApiService;

  _determineIfOwner(repo: RepoName) {
    return this.restApiService
      .getRepoAccess(repo)
      .then(access => (this._isOwner = !!access?.[repo]?.is_owner));
  }

  _paramsChanged(params?: AppElementRepoParams) {
    if (!params?.repo) {
      return Promise.reject(new Error('undefined repo'));
    }

    // paramsChanged is called before gr-admin-view can set _showRepoDetailList
    // to false and polymer removes this component, hence check for params
    if (
      !(
        params?.detail === RepoDetailView.BRANCHES ||
        params?.detail === RepoDetailView.TAGS
      )
    ) {
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

    this._filter = getFilterValue(params);
    this._offset = getOffsetValue(params);
    if (!this.detailType)
      return Promise.reject(new Error('undefined detailType'));

    return this._getItems(
      this._filter,
      this._repo,
      this._itemsPerPage,
      this._offset,
      this.detailType
    );
  }

  // TODO(TS) Move this to object for easier read, understand.
  _getItems(
    filter: string | undefined,
    repo: RepoName | undefined,
    itemsPerPage: number,
    offset: number | undefined,
    detailType: string
  ) {
    if (filter === undefined || !repo || offset === undefined) {
      return Promise.reject(new Error('filter or repo or offset undefined'));
    }
    this._loading = true;
    this._items = [];
    flush();
    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    if (detailType === RepoDetailView.BRANCHES) {
      return this.restApiService
        .getRepoBranches(filter, repo, itemsPerPage, offset, errFn)
        .then(items => {
          if (!items) {
            return;
          }
          this._items = items;
          this._loading = false;
        });
    } else if (detailType === RepoDetailView.TAGS) {
      return this.restApiService
        .getRepoTags(filter, repo, itemsPerPage, offset, errFn)
        .then(items => {
          if (!items) {
            return;
          }
          this._items = items;
          this._loading = false;
        });
    }
    return Promise.reject(new Error('unknown detail type'));
  }

  _getPath(repo?: RepoName, detailType?: RepoDetailView) {
    return `/admin/repos/${encodeURL(repo ?? '', false)},${detailType}`;
  }

  _computeWeblink(repo: ProjectInfo | BranchInfo | TagInfo) {
    if (!repo.web_links) {
      return '';
    }
    const webLinks = repo.web_links;
    return webLinks.length ? webLinks : null;
  }

  _computeFirstWebLink(repo: ProjectInfo | BranchInfo | TagInfo) {
    const webLinks = this._computeWeblink(repo);
    return webLinks ? webLinks[0].url : null;
  }

  _computeMessage(message?: string) {
    if (!message) {
      return;
    }
    // Strip PGP info.
    return message.split(PGP_START)[0];
  }

  _stripRefs(item: GitRef, detailType?: RepoDetailView) {
    if (detailType === RepoDetailView.BRANCHES) {
      return item.replace('refs/heads/', '');
    } else if (detailType === RepoDetailView.TAGS) {
      return item.replace('refs/tags/', '');
    }
    throw new Error('unknown detailType');
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _computeEditingClass(isEditing: boolean) {
    return isEditing ? 'editing' : '';
  }

  _computeCanEditClass(
    ref?: GitRef,
    detailType?: RepoDetailView,
    isOwner?: boolean
  ) {
    if (ref === undefined || detailType === undefined) return '';
    return isOwner && this._stripRefs(ref, detailType) === 'HEAD'
      ? 'canEdit'
      : '';
  }

  _handleEditRevision(e: PolymerDomRepeatEvent<BranchInfo | TagInfo>) {
    this._revisedRef = e.model.get('item.revision') as unknown as GitRef;
    this._isEditing = true;
  }

  _handleCancelRevision() {
    this._isEditing = false;
  }

  _handleSaveRevision(e: PolymerDomRepeatEvent<BranchInfo | TagInfo>) {
    if (this._revisedRef && this._repo)
      this._setRepoHead(this._repo, this._revisedRef, e);
  }

  _setRepoHead(
    repo: RepoName,
    ref: GitRef,
    e: PolymerDomRepeatEvent<BranchInfo | TagInfo>
  ) {
    return this.restApiService.setRepoHead(repo, ref).then(res => {
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
          this.detailType!
        );
      }
    });
  }

  _computeItemName(detailType?: RepoDetailView) {
    if (detailType === undefined) return '';
    if (detailType === RepoDetailView.BRANCHES) {
      return 'Branch';
    } else if (detailType === RepoDetailView.TAGS) {
      return 'Tag';
    }
    throw new Error('unknown detailType');
  }

  _handleDeleteItemConfirm() {
    this.$.overlay.close();
    if (!this._repo || !this._refName) {
      return Promise.reject(new Error('undefined repo or refName'));
    }
    if (this.detailType === RepoDetailView.BRANCHES) {
      return this.restApiService
        .deleteRepoBranches(this._repo, this._refName)
        .then(itemDeleted => {
          if (itemDeleted.status === 204) {
            this._getItems(
              this._filter,
              this._repo,
              this._itemsPerPage,
              this._offset,
              this.detailType!
            );
          }
        });
    } else if (this.detailType === RepoDetailView.TAGS) {
      return this.restApiService
        .deleteRepoTags(this._repo, this._refName)
        .then(itemDeleted => {
          if (itemDeleted.status === 204) {
            this._getItems(
              this._filter,
              this._repo,
              this._itemsPerPage,
              this._offset,
              this.detailType!
            );
          }
        });
    }
    return Promise.reject(new Error('unknown detail type'));
  }

  _handleConfirmDialogCancel() {
    this.$.overlay.close();
  }

  _handleDeleteItem(e: PolymerDomRepeatEvent<BranchInfo | TagInfo>) {
    const name = this._stripRefs(
      e.model.get('item.ref'),
      this.detailType
    ) as GitRef;
    if (!name) {
      return;
    }
    this._refName = name;
    this.$.overlay.open();
  }

  _computeHideDeleteClass(owner?: boolean, canDelete?: boolean) {
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

  _hideIfBranch(type?: RepoDetailView) {
    if (type === RepoDetailView.BRANCHES) {
      return 'hideItem';
    }

    return '';
  }

  _computeHideTagger(tagger?: GitPersonInfo) {
    return tagger ? '' : 'hide';
  }

  computeLoadingClass(loading: boolean) {
    computeLoadingClass(loading);
  }

  computeShownItems(items: BranchInfo[] | TagInfo[]) {
    return computeShownItems(items);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-detail-list': GrRepoDetailList;
  }
}
