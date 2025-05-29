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
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {ValueChangedEvent} from '../../../types/events';
import {fire, fireAlert, fireReload} from '../../../utils/event-util';
import {RepoDetailView} from '../../../models/views/repo';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-pointer-dialog': GrCreatePointerDialog;
  }
  interface HTMLElementEventMap {
    'update-item-name': CustomEvent<{}>;
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

  @state() createEmptyCommit?: boolean;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      grFormStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        input {
          width: 20em;
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
          <section
            id="createEmptyCommitSection"
            ?hidden=${this.itemDetail === RepoDetailView.TAGS}
          >
            <div>
              <span class="title">Point to</span>
            </div>
            <div>
              <span class="value">
                <gr-select
                  id="initialCommit"
                  .bindValue=${this.createEmptyCommit}
                  @bind-value-changed=${this
                    .handleCreateEmptyCommitBindValueChanged}
                >
                  <select>
                    <option value="false">Existing Revision</option>
                    <option value="true">Initial empty commit</option>
                  </select>
                </gr-select>
              </span>
            </div>
          </section>
          <section id="itemRevisionSection" ?hidden=${!!this.createEmptyCommit}>
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
            ?hidden=${this.itemDetail === RepoDetailView.BRANCHES}
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
    fire(this, 'update-item-name', {});
  }

  handleCreateItem() {
    if (!this.repoName) {
      throw new Error('repoName name is not set');
    }
    if (!this.itemName) {
      throw new Error('itemName name is not set');
    }
    const useHead = this.itemRevision ? this.itemRevision : 'HEAD';
    const createEmptyCommit = !!this.createEmptyCommit;
    if (this.itemDetail === RepoDetailView.BRANCHES) {
      const createBranchInput = createEmptyCommit
        ? {create_empty_commit: true}
        : {revision: useHead};
      return this.restApiService
        .createRepoBranch(this.repoName, this.itemName, createBranchInput)
        .then(itemRegistered => {
          if (itemRegistered.status === 201) {
            fireAlert(this, 'Branch created successfully. Reloading...');
            fireReload(this);
          }
        });
    } else if (this.itemDetail === RepoDetailView.TAGS) {
      return this.restApiService
        .createRepoTag(this.repoName, this.itemName, {
          revision: useHead,
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

  private handleCreateEmptyCommitBindValueChanged(
    e: ValueChangedEvent<string>
  ) {
    this.createEmptyCommit = e.detail.value === 'true';
  }
}
