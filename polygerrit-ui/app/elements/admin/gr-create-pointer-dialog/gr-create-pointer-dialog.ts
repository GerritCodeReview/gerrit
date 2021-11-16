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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {page} from '../../../utils/page-wrapper-utils';
import {BranchName, RepoName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {fireEvent} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-pointer-dialog': GrCreatePointerDialog;
  }
}

@customElement('gr-create-pointer-dialog')
export class GrCreatePointerDialog extends LitElement {
  @property({type: String})
  detailType?: string;

  @property({type: String})
  repoName?: RepoName;

  @property({type: String})
  itemDetail?: RepoDetailView.BRANCHES | RepoDetailView.TAGS;

  /* private but used in test */
  @state() itemName?: BranchName;

  /* private but used in test */
  @state() itemRevision?: string;

  /* private but used in test */
  @state() itemAnnotation?: string;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        input {
          width: 20em;
        }
        /* Add css selector with #id to increase priority
          (otherwise ".gr-form-styles section" rule wins) */
        .hideItem,
        #itemAnnotationSection.hideItem {
          display: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="gr-form-styles">
        <div id="form">
          <section id="itemNameSection">
            <span class="title">${this.detailType} name</span>
            <iron-input
              .bindValue=${this.itemName}
              @bind-value-changed=${this.handleItemNameBindValueChanged}
            >
              <input placeholder="${this.detailType} Name" />
            </iron-input>
          </section>
          <section id="itemRevisionSection">
            <span class="title">Initial Revision</span>
            <iron-input
              .bindValue=${this.itemRevision}
              @bind-value-changed=${this.handleItemRevisionBindValueChanged}
            >
              <input placeholder="Revision (Branch or SHA-1)" />
            </iron-input>
          </section>
          <section
            id="itemAnnotationSection"
            class=${this.itemDetail === RepoDetailView.BRANCHES
              ? 'hideItem'
              : ''}
          >
            <span class="title">Annotation</span>
            <iron-input
              .bindValue=${this.itemAnnotation}
              @bind-value-changed=${this.handleItemAnnotationBindValueChanged}
            >
              <input placeholder="Annotation (Optional)" />
            </iron-input>
          </section>
        </div>
      </div>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('itemName')) {
      this.updateItemName();
    }
  }

  private updateItemName() {
    fireEvent(this, 'update-item-name');
  }

  handleCreateItem() {
    if (!this.repoName) {
      throw new Error('repoName name is not set');
    }
    if (!this.itemName) {
      throw new Error('itemName name is not set');
    }
    const USE_HEAD = this.itemRevision ? this.itemRevision : 'HEAD';
    const url = `${getBaseUrl()}/admin/repos/${encodeURL(this.repoName, true)}`;
    if (this.itemDetail === RepoDetailView.BRANCHES) {
      return this.restApiService
        .createRepoBranch(this.repoName, this.itemName, {revision: USE_HEAD})
        .then(itemRegistered => {
          if (itemRegistered.status === 201) {
            page.show(`${url},branches`);
          }
        });
    } else if (this.itemDetail === RepoDetailView.TAGS) {
      return this.restApiService
        .createRepoTag(this.repoName, this.itemName, {
          revision: USE_HEAD,
          message: this.itemAnnotation || undefined,
        })
        .then(itemRegistered => {
          if (itemRegistered.status === 201) {
            page.show(`${url},tags`);
          }
        });
    }
    throw new Error(`Invalid itemDetail: ${this.itemDetail}`);
  }

  private handleItemNameBindValueChanged(e: BindValueChangeEvent) {
    this.itemName = e.detail.value as BranchName;
  }

  private handleItemRevisionBindValueChanged(e: BindValueChangeEvent) {
    this.itemRevision = e.detail.value;
  }

  private handleItemAnnotationBindValueChanged(e: BindValueChangeEvent) {
    this.itemAnnotation = e.detail.value;
  }
}
