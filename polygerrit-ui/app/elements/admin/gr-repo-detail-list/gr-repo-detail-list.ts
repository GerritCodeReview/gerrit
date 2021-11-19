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
import '../../shared/gr-account-link/gr-account-link';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-pointer-dialog/gr-create-pointer-dialog';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import {encodeURL} from '../../../utils/url-util';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrCreatePointerDialog} from '../gr-create-pointer-dialog/gr-create-pointer-dialog';
import {GrButton} from '../../shared/gr-button/gr-button';
import {
  BranchInfo,
  GitRef,
  GitPersonInfo,
  ProjectInfo,
  RepoName,
  TagInfo,
  WebLinkInfo,
} from '../../../types/common';
import {AppElementRepoParams} from '../../gr-app-types';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation';
import {firePageError} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';
import {formStyles} from '../../../styles/gr-form-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, query, property /* , state*/} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {assertIsDefined} from '../../../utils/common-util';

const PGP_START = '-----BEGIN PGP SIGNATURE-----';

@customElement('gr-repo-detail-list')
export class GrRepoDetailList extends LitElement {
  @query('#overlay') private overlay?: GrOverlay;

  @query('#createOverlay') private createOverlay?: GrOverlay;

  @query('#createNewModal') private createNewModal?: GrCreatePointerDialog;

  @property({type: Object})
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
  _offset = 0;

  @property({type: String})
  _repo?: RepoName;

  @property({type: Array})
  _items?: BranchInfo[] | TagInfo[];

  @property({type: Number})
  _itemsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter?: string;

  @property({type: String})
  _refName?: GitRef;

  @property({type: Boolean})
  _newItemName = false;

  @property({type: Boolean})
  _isEditing = false;

  @property({type: String})
  _revisedRef?: GitRef;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      formStyles,
      tableStyles,
      sharedStyles,
      css`
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
          margin-left: var(--spacing-m);
        }
        .editBtn {
          margin-left: var(--spacing-l);
        }
        .canEdit .revisionEdit {
          align-items: center;
          display: flex;
        }
        .deleteButton:not(.show) {
          display: none;
        }
        .tagger.hide {
          display: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-list-view
        .createNew=${this._loggedIn}
        .filter=${this._filter}
        .itemsPerPage=${this._itemsPerPage}
        .items=${this._items}
        .loading=${this._loading}
        .offset=${this._offset}
        .path=${this._getPath(this._repo, this.detailType)}
        @create-clicked=${() => this._handleCreateClicked()}
      >
        <table id="list" class="genericList gr-form-styles">
          <tbody>
            <tr class="headerRow">
              <th class="name topHeader">Name</th>
              <th class="revision topHeader">Revision</th>
              <th
                class="message topHeader ${this.detailType ===
                RepoDetailView.BRANCHES
                  ? 'hideItem'
                  : ''}"
              >
                Message
              </th>
              <th
                class="tagger topHeader ${this.detailType ===
                RepoDetailView.BRANCHES
                  ? 'hideItem'
                  : ''}"
              >
                Tagger
              </th>
              <th class="repositoryBrowser topHeader">Repository Browser</th>
              <th class="delete topHeader"></th>
            </tr>
            <tr
              id="loading"
              class="loadingMsg ${this.computeLoadingClass(this._loading)}"
            >
              <td>Loading...</td>
            </tr>
          </tbody>
          <tbody class=${this.computeLoadingClass(this._loading)}>
            ${this._items?.slice(0, SHOWN_ITEMS_COUNT)
              .map((item, index) => this.renderItemList(item, index))}
          </tbody>
        </table>
        <gr-overlay id="overlay" withBackdrop>
          <gr-confirm-delete-item-dialog
            class="confirmDialog"
            .item=${this._refName}
            .itemTypeName=${this._computeItemName(this.detailType)}
            @confirm=${() => this._handleDeleteItemConfirm()}
            @cancel=${() => this._handleConfirmDialogCancel()}
          ></gr-confirm-delete-item-dialog>
        </gr-overlay>
      </gr-list-view>
      <gr-overlay id="createOverlay" withBackdrop>
        <gr-dialog
          id="createDialog"
          ?disabled=${!this._newItemName}
          confirmLabel="Create"
          @confirm=${() => this._handleCreateItem()}
          @cancel=${() => this._handleCloseCreate()}
        >
          <div class="header" slot="header">
            Create ${this._computeItemName(this.detailType)}
          </div>
          <div class="main" slot="main">
            <gr-create-pointer-dialog
              id="createNewModal"
              .detailType=${this._computeItemName(this.detailType)}
              .itemDetail="[[detailType]]"
              .repoName="[[_repo]]"
              @update-item-name=${() => this._handleUpdateItemName()}
            ></gr-create-pointer-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renderItemList(item: BranchInfo | TagInfo, index: number) {
    return html`
      <tr class="table">
        <td class="${this.detailType} name">
          <a href=${this._computeFirstWebLink(item)}>
            ${this._stripRefs(item.ref, this.detailType)}
          </a>
        </td>
        <td
          class="${this.detailType} revision ${this._computeCanEditClass(
            item.ref,
            this.detailType,
            this._isOwner
          )}"
        >
          <span class="revisionNoEditing"> ${item.revision} </span>
          <span
            class="revisionEdit ${this._computeEditingClass(this._isEditing)}"
          >
            <span class="revisionWithEditing"> ${item.revision} </span>
            <gr-button
              class="editBtn"
              link
              data-index=${index}
              @click=${this._handleEditRevision}
            >
              edit
            </gr-button>
            <iron-input
              class="editItem"
              .bindValue=${this._revisedRef}
              @bind-value-changed=${this.handleRevisedRefBindValueChanged}
            >
              <input />
            </iron-input>
            <gr-button
              class="cancelBtn editItem"
              link
              @click=${() => this._handleCancelRevision()}
            >
              Cancel
            </gr-button>
            <gr-button
              class="saveBtn editItem"
              link
              data-index=${index}
              ?disabled=${!this._revisedRef}
              @click=${this._handleSaveRevision}
            >
              Save
            </gr-button>
          </span>
        </td>
        <td
          class="message ${this.detailType === RepoDetailView.BRANCHES
            ? 'hideItem'
            : ''}"
        >
          ${this._computeMessage((item as TagInfo).message)}
        </td>
        <td
          class="tagger ${this.detailType === RepoDetailView.BRANCHES
            ? 'hideItem'
            : ''}"
        >
          <div class="tagger ${this._computeHideTagger((item as TagInfo).tagger)}">
            <gr-account-link .account=${(item as TagInfo).tagger}> </gr-account-link>
            (<gr-date-formatter withTooltip .dateStr=${(item as TagInfo).tagger?.date}>
            </gr-date-formatter
            >)
          </div>
        </td>
        <td class="repositoryBrowser">
          ${this._computeWeblink(item).map(link => this.renderWeblink(link))}
        </td>
        <td class="delete">
          <gr-button
            class="deleteButton ${item.can_delete || this._isOwner ? 'show' : ''}"
            link
            data-index=${index}
            @click="_handleDeleteItem"
          >
            Delete
          </gr-button>
        </td>
      </tr>
    `;
  }

  private renderWeblink(link: WebLinkInfo) {
    return html`
      <a href=${link.url} class="webLink" rel="noopener" target="_blank">
        (${link.name})
      </a>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  _determineIfOwner(repo: RepoName) {
    return this.restApiService
      .getRepoAccess(repo)
      .then(access => (this._isOwner = !!access?.[repo]?.is_owner));
  }

  // private but used in test
  paramsChanged() {
    if (!this.params?.repo) {
      return Promise.reject(new Error('undefined repo'));
    }

    // paramsChanged is called before gr-admin-view can set _showRepoDetailList
    // to false and polymer removes this component, hence check for params
    if (
      !(
        this.params?.detail === RepoDetailView.BRANCHES ||
        this.params?.detail === RepoDetailView.TAGS
      )
    ) {
      return;
    }

    this._repo = this.params.repo;

    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
      if (loggedIn && this._repo) {
        this._determineIfOwner(this._repo);
      }
    });

    this.detailType = this.params.detail;

    this._filter = this.params?.filter ?? '';
    this._offset = Number(this.params?.offset ?? 0);
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
    if (!repo.web_links) return [];
    const webLinks = repo.web_links;
    return webLinks.length ? webLinks : [];
  }

  _computeFirstWebLink(repo: ProjectInfo | BranchInfo | TagInfo) {
    const webLinks = this._computeWeblink(repo);
    return webLinks.length > 0 ? webLinks[0].url : null;
  }

  _computeMessage(message?: string) {
    if (!message) return;
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

  _handleEditRevision(e: Event) {
    if (!this._items) return;

    const el = e.target as GrButton;
    const index = Number(el.getAttribute('data-index')!);
    this._revisedRef = this._items[index].revision as GitRef;
    this._isEditing = true;
  }

  _handleCancelRevision() {
    this._isEditing = false;
  }

  _handleSaveRevision(e: Event) {
    if (this._revisedRef && this._repo)
      this._setRepoHead(this._repo, this._revisedRef, e);
  }

  _setRepoHead(repo: RepoName, ref: GitRef, e: Event) {
    if (!this._items) return;
    return this.restApiService.setRepoHead(repo, ref).then(res => {
      if (res.status < 400) {
        this._isEditing = false;
        const el = e.target as GrButton;
        const index = Number(el.getAttribute('data-index')!);
        this._items![index].revision = ref as GitRef;
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
    assertIsDefined(this.overlay, 'overlay');
    this.overlay.close();
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
    assertIsDefined(this.overlay, 'overlay');
    this.overlay.close();
  }

  _handleDeleteItem(e: Event) {
    if (!this._items) return;
    assertIsDefined(this.overlay, 'overlay');
    const el = e.target as GrButton;
    const index = Number(el.getAttribute('data-index')!);
    const name = this._stripRefs(
      this._items[index].ref,
      this.detailType
    ) as GitRef;
    if (!name) return;
    this._refName = name;
    this.overlay.open();
  }

  _handleCreateItem() {
    assertIsDefined(this.createNewModal, 'createNewModal');
    this.createNewModal.handleCreateItem();
    this._handleCloseCreate();
  }

  _handleCloseCreate() {
    assertIsDefined(this.createOverlay, 'createOverlay');
    this.createOverlay.close();
  }

  _handleCreateClicked() {
    assertIsDefined(this.createOverlay, 'createOverlay');
    this.createOverlay.open();
  }

  _computeHideTagger(tagger?: GitPersonInfo) {
    return tagger ? '' : 'hide';
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _handleUpdateItemName() {
    assertIsDefined(this.createNewModal, 'createNewModal');
    this._newItemName = !!this.createNewModal.itemName;
  }

  handleRevisedRefBindValueChanged(e: BindValueChangeEvent) {
    this._revisedRef = e.detail.value as GitRef;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-detail-list': GrRepoDetailList;
  }
}
