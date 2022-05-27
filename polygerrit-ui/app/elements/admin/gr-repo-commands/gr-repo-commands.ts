/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-change-dialog/gr-create-change-dialog';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  BranchName,
  ConfigInfo,
  RevisionPatchSetNum,
  RepoName,
} from '../../../types/common';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
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
import {customElement, query, property, state} from 'lit/decorators';
import {assertIsDefined} from '../../../utils/common-util';

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
  @query('#createChangeOverlay')
  private readonly createChangeOverlay?: GrOverlay;

  @query('#createNewChangeModal')
  private readonly createNewChangeModal?: GrCreateChangeDialog;

  @property({type: String})
  repo?: RepoName;

  @state() private loading = true;

  @state() private repoConfig?: ConfigInfo;

  @state() private canCreateChange = false;

  @state() private creatingChange = false;

  @state() private editingConfig = false;

  @state() private runningGC = false;

  private readonly restApiService = getAppContext().restApiService;

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
      css`
        #form gr-button {
          margin-bottom: var(--spacing-xxl);
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
          <h2 id="options" class="heading-2">Command</h2>
          <div id="form">
            <h3 class="heading-3">Create change</h3>
            <gr-button
              ?loading=${this.creatingChange}
              @click=${() => {
                this.createNewChange();
              }}
            >
              Create change
            </gr-button>
            <h3 class="heading-3">Edit repo config</h3>
            <gr-button
              id="editRepoConfig"
              ?loading=${this.editingConfig}
              @click=${() => {
                this.handleEditRepoConfig();
              }}
            >
              Edit repo config
            </gr-button>
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
      <gr-overlay id="createChangeOverlay" with-backdrop>
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
      </gr-overlay>
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
    assertIsDefined(this.createChangeOverlay, 'createChangeOverlay');
    this.createChangeOverlay.open();
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
    assertIsDefined(this.createChangeOverlay, 'createChangeOverlay');
    this.createChangeOverlay.close();
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

        GerritNav.navigateToRelativeUrl(
          GerritNav.getEditUrlForDiff(change, CONFIG_PATH, INITIAL_PATCHSET)
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
