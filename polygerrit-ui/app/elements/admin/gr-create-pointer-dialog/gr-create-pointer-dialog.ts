/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {BranchName, RepoName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {fireAlert, fireEvent, fireReload} from '../../../utils/event-util';
import {RepoDetailView} from '../../../models/views/repo';

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
    if (this.itemDetail === RepoDetailView.BRANCHES) {
      return this.restApiService
        .createRepoBranch(this.repoName, this.itemName, {revision: USE_HEAD})
        .then(itemRegistered => {
          if (itemRegistered.status === 201) {
            fireAlert(this, 'Branch created successfully. Reloading...');
            fireReload(this);
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
            fireAlert(this, 'Tag created successfully. Reloading...');
            fireReload(this);
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
