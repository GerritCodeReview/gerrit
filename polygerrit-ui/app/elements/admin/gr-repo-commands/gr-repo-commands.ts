/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../gr-create-change-dialog/gr-create-change-dialog';
import '../gr-create-change-dialog/gr-create-file-edit-dialog';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  BranchName,
  ConfigInfo,
  RevisionPatchSetNum,
  RepoName,
} from '../../../types/common';
import {GrCreateChangeDialog} from '../gr-create-change-dialog/gr-create-change-dialog';
import {
  fireAlert,
  firePageError,
  fireTitleChange,
} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, query, property, state} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {createEditUrl} from '../../../models/views/edit';
import {resolve} from '../../../models/dependency';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {GrCreateFileEditDialog} from '../gr-create-change-dialog/gr-create-file-edit-dialog';

const GC_MESSAGE = 'Garbage collection completed successfully.';
const CONFIG_BRANCH = 'refs/meta/config' as BranchName;
const CONFIG_PATH = 'project.config';
const EDIT_CONFIG_SUBJECT = 'Edit Repo Config';
const INITIAL_PATCHSET = 1 as RevisionPatchSetNum;
const CREATE_CHANGE_FAILED_MESSAGE = 'Failed to create change.';
const CREATE_CHANGE_SUCCEEDED_MESSAGE = 'Navigating to change';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-commands': GrRepoCommands;
  }
}

@customElement('gr-repo-commands')
export class GrRepoCommands extends LitElement {
  @query('#createChangeModal')
  private readonly createChangeModal?: HTMLDialogElement;

  @query('#createNewChangeModal')
  private readonly createNewChangeModal?: GrCreateChangeDialog;

  @query('#createFileEditDialog')
  private readonly createFileEditDialog?: GrCreateFileEditDialog;

  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  createEdit?: {
    branch: BranchName;
    path: string;
  };

  @state() private loading = true;

  @state() private repoConfig?: ConfigInfo;

  @state() private canCreateChange = false;

  @state() private creatingChange = false;

  @state() private editingConfig = false;

  @state() private runningGC = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  /** Make sure that this dialog is only activated once. */
  private createFileEditDialogWasActivated = false;

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange(this, 'Repo Commands');
  }

  static override get styles() {
    return [
      fontStyles,
      formStyles,
      subpageStyles,
      sharedStyles,
      modalStyles,
      css`
        #form h2,
        h3 {
          margin-top: var(--spacing-xxl);
        }
        p {
          padding: var(--spacing-m) 0;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="main gr-form-styles read-only">
        <h1 id="Title" class="heading-1">Repository Commands</h1>
        <div id="loading" class=${this.loading ? 'loading' : ''}>
          Loading...
        </div>
        <div id="loadedContent" class=${this.loading ? 'loading' : ''}>
          <div id="form">
            <h2 class="heading-2">Create change</h2>
            <div>
              <p>
                Creates an empty work-in-progress change that can be used to
                edit files online and send the modifications for review.
              </p>
            </div>
            <div>
              <gr-button
                ?loading=${this.creatingChange}
                @click=${() => {
                  this.createNewChange();
                }}
              >
                Create change
              </gr-button>
            </div>
            <h2 class="heading-2">Edit repo config</h2>
            <div>
              <p>
                Creates a work-in-progress change that allows to edit the
                <code>project.config</code> file in the
                <code>refs/meta/config</code> branch and send the modifications
                for review.
              </p>
            </div>
            <div>
              <gr-button
                id="editRepoConfig"
                ?loading=${this.editingConfig}
                @click=${() => {
                  this.handleEditRepoConfig();
                }}
              >
                Edit repo config
              </gr-button>
            </div>
            ${this.renderRepoGarbageCollector()}
            <gr-endpoint-decorator name="repo-command">
              <gr-endpoint-param name="config" .value=${this.repoConfig}>
              </gr-endpoint-param>
              <gr-endpoint-param name="repoName" .value=${this.repo}>
              </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
        </div>
      </div>
      <dialog id="createChangeModal" tabindex="-1">
        <gr-dialog
          id="createChangeDialog"
          confirm-label="Create"
          ?disabled=${!this.canCreateChange}
          @confirm=${() => {
            this.handleCreateChange();
          }}
          @cancel=${() => {
            this.handleCloseCreateChange();
          }}
        >
          <div class="header" slot="header">Create Change</div>
          <div class="main" slot="main">
            <gr-create-change-dialog
              id="createNewChangeModal"
              .repoName=${this.repo}
              .privateByDefault=${this.repoConfig?.private_by_default}
              @can-create-change=${() => {
                this.handleCanCreateChange();
              }}
            ></gr-create-change-dialog>
          </div>
        </gr-dialog>
      </dialog>
      <gr-create-file-edit-dialog
        id="createFileEditDialog"
        .repo=${this.repo}
        .branch=${this.createEdit?.branch}
        .path=${this.createEdit?.path}
      ></gr-create-file-edit-dialog>
    `;
  }

  private renderRepoGarbageCollector() {
    if (!this.repoConfig?.actions || !this.repoConfig?.actions['gc']?.enabled)
      return;

    return html`
      <h3 class="heading-3">${this.repoConfig?.actions['gc']?.label}</h3>
      <gr-button
        title=${this.repoConfig?.actions['gc']?.title || ''}
        ?loading=${this.runningGC}
        @click=${() => this.handleRunningGC()}
      >
        ${this.repoConfig?.actions['gc']?.label}
      </gr-button>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('createEdit')) {
      if (!this.createFileEditDialogWasActivated) {
        this.createFileEditDialog?.activate();
        this.createFileEditDialogWasActivated = true;
      }
    }
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.loadRepo();
    }
  }

  // private but used in test
  loadRepo() {
    if (!this.repo) return;

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    this.restApiService
      .getProjectConfig(this.repo, errFn)
      .then(config => {
        if (!config) return;
        this.repoConfig = config;
      })
      .finally(() => {
        this.loading = false;
      });
  }

  private handleRunningGC() {
    if (!this.repo) return;
    this.runningGC = true;
    return this.restApiService
      .runRepoGC(this.repo)
      .then(response => {
        if (response?.status === 200) {
          fireAlert(this, GC_MESSAGE);
        }
      })
      .finally(() => {
        this.runningGC = false;
      });
  }

  // private but used in test
  createNewChange() {
    assertIsDefined(this.createChangeModal, 'createChangeModal');
    this.createChangeModal.showModal();
  }

  // private but used in test
  handleCreateChange() {
    assertIsDefined(this.createNewChangeModal, 'createNewChangeModal');
    this.creatingChange = true;
    this.createNewChangeModal.handleCreateChange().finally(() => {
      this.creatingChange = false;
    });
    this.handleCloseCreateChange();
  }

  // private but used in test
  handleCloseCreateChange() {
    assertIsDefined(this.createChangeModal, 'createChangeModal');
    this.createChangeModal.close();
  }

  /**
   * Returns a Promise for testing.
   *
   * private but used in test
   */
  handleEditRepoConfig() {
    if (!this.repo) return;
    this.editingConfig = true;
    return this.restApiService
      .createChange(
        this.repo,
        CONFIG_BRANCH,
        EDIT_CONFIG_SUBJECT,
        undefined,
        false,
        true
      )
      .then(change => {
        const message = change
          ? CREATE_CHANGE_SUCCEEDED_MESSAGE
          : CREATE_CHANGE_FAILED_MESSAGE;
        fireAlert(this, message);
        if (!change) {
          return;
        }

        this.getNavigation().setUrl(
          createEditUrl({
            changeNum: change._number,
            repo: change.project,
            patchNum: INITIAL_PATCHSET,
            editView: {path: CONFIG_PATH},
          })
        );
      })
      .finally(() => {
        this.editingConfig = false;
      });
  }

  private handleCanCreateChange() {
    assertIsDefined(this.createNewChangeModal, 'createNewChangeModal');
    this.canCreateChange =
      !!this.createNewChangeModal.branch && !!this.createNewChangeModal.subject;
  }
}
