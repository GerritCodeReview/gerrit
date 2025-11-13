/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {BranchName, RepoName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {fire, fireAlert, fireReload} from '../../../utils/event-util';
import {RepoDetailView} from '../../../models/views/repo';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';
import {convertToString} from '../../../utils/string-util';

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
      materialStyles,
      grFormStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        input {
          width: 20em;
        }
        md-outlined-text-field {
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
            <md-outlined-text-field
              class="showBlueFocusBorder"
              placeholder="${this.detailType} Name"
              .value=${this.itemName ?? ''}
              @input=${(e: InputEvent) => {
                const target = e.target as HTMLInputElement;
                this.itemName = target.value as BranchName;
              }}
            >
            </md-outlined-text-field>
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
                <md-outlined-select
                  id="initialCommit"
                  value=${convertToString(this.createEmptyCommit ?? false)}
                  @change=${(e: Event) => {
                    const select = e.target as HTMLSelectElement;
                    this.createEmptyCommit = select.value === 'true';
                  }}
                >
                  <md-select-option value="false">
                    <div slot="headline">Existing Revision</div>
                  </md-select-option>
                  <md-select-option value="true">
                    <div slot="headline">Initial empty commit</div>
                  </md-select-option>
                </md-outlined-select>
              </span>
            </div>
          </section>
          <section id="itemRevisionSection" ?hidden=${!!this.createEmptyCommit}>
            <span class="title">Initial Revision</span>
            <md-outlined-text-field
              class="showBlueFocusBorder"
              placeholder="Revision (Branch or SHA-1)"
              .value=${this.itemRevision ?? ''}
              @input=${(e: InputEvent) => {
                const target = e.target as HTMLInputElement;
                this.itemRevision = target.value;
              }}
            >
            </md-outlined-text-field>
          </section>
          <section
            id="itemAnnotationSection"
            ?hidden=${this.itemDetail === RepoDetailView.BRANCHES}
          >
            <span class="title">Annotation</span>
            <md-outlined-text-field
              class="showBlueFocusBorder"
              placeholder="Annotation (Optional)"
              .value=${this.itemAnnotation ?? ''}
              @input=${(e: InputEvent) => {
                const target = e.target as HTMLInputElement;
                this.itemAnnotation = target.value;
              }}
            >
            </md-outlined-text-field>
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
}
