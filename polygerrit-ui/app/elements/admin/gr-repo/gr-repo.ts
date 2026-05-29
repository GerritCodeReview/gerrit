/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-download-commands/gr-download-commands';
import '../gr-repo-plugin-config/gr-repo-plugin-config';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  ConfigInfo,
  ConfigInput,
  DownloadSchemeInfo,
  InheritedBooleanInfo,
  MaxObjectSizeLimitInfo,
  PluginParameterToConfigParameterInfoMap,
  RepoName,
  SchemesInfoMap,
  ServerInfo,
} from '../../../types/common';
import {
  InheritedBooleanInfoConfiguredValue,
  RepoState,
  SubmitType,
} from '../../../constants/constants';
import {assertIsDefined, hasOwnProperty} from '../../../utils/common-util';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {WebLinkInfo} from '../../../types/diff';
import {ErrorCallback} from '../../../api/rest';
import {fontStyles} from '../../../styles/gr-font-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {BindValueChangeEvent} from '../../../types/events';
import {deepClone} from '../../../utils/deep-util';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {createChangeUrl} from '../../../models/views/change';
import {when} from 'lit/directives/when.js';
import {subscribe} from '../../lit/subscription-controller';
import {createSearchUrl} from '../../../models/views/search';
import {userModelToken} from '../../../models/user/user-model';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {GrButton} from '../../shared/gr-button/gr-button';
import {computeMainCodeBrowserWeblink} from '../../../utils/weblink-util';
import {Command} from '../../shared/gr-download-commands/gr-download-commands';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import {formStyles} from '../../../styles/form-styles';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';

const STATES = {
  active: {value: RepoState.ACTIVE, label: 'Active'},
  readOnly: {value: RepoState.READ_ONLY, label: 'Read Only'},
  hidden: {value: RepoState.HIDDEN, label: 'Hidden'},
};

const SUBMIT_TYPES = {
  // Exclude INHERIT, which is handled specially.
  mergeIfNecessary: {
    value: 'MERGE_IF_NECESSARY',
    label: 'Merge if necessary',
  },
  fastForwardOnly: {
    value: 'FAST_FORWARD_ONLY',
    label: 'Fast forward only',
  },
  rebaseAlways: {
    value: 'REBASE_ALWAYS',
    label: 'Rebase Always',
  },
  rebaseIfNecessary: {
    value: 'REBASE_IF_NECESSARY',
    label: 'Rebase if necessary',
  },
  mergeAlways: {
    value: 'MERGE_ALWAYS',
    label: 'Merge always',
  },
  cherryPick: {
    value: 'CHERRY_PICK',
    label: 'Cherry pick',
  },
};

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo': GrRepo;
  }
}

@customElement('gr-repo')
export class GrRepo extends LitElement {
  private schemes: string[] = [];

  @property({type: String})
  repo?: RepoName;

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() repoConfig?: ConfigInfo;

  // private but used in test
  @state() readOnly = true;

  // private but used in test
  @state() disableSaveWithoutReview = true;

  @state() private states = Object.values(STATES);

  @state() private originalConfig?: ConfigInfo;

  @state() private selectedScheme?: string;

  // private but used in test
  @state() schemesObj?: SchemesInfoMap;

  @state() serverConfig?: ServerInfo;

  @state() private weblinks: WebLinkInfo[] = [];

  @state() private pluginConfigChanged = false;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        if (prefs?.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this.selectedScheme = prefs.download_scheme.toLowerCase();
        }
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => (this.serverConfig = config)
    );
  }

  override connectedCallback() {
    super.connectedCallback();

    fireTitleChange(`${this.repo}`);
  }

  static override get styles() {
    return [
      materialStyles,
      fontStyles,
      formStyles,
      grFormStyles,
      subpageStyles,
      sharedStyles,
      css`
        .info {
          margin-bottom: var(--spacing-xl);
        }
        h2.edited:after {
          color: var(--deemphasized-text-color);
          content: ' *';
        }
        #options .repositorySettings {
          display: none;
        }
        #options .repositorySettings.showConfig {
          display: block;
        }
        gr-autogrow-textarea {
          background-color: var(--view-background-color);
          width: 100%;
        }
        gr-autogrow-textarea:focus {
          border: 2px solid var(--input-focus-border-color);
        }
      `,
    ];
  }

  override render() {
    const configChanged = this.hasConfigChanged();
    return html`
      <div class="main gr-form-styles read-only">
        <div class="info">
          <h1 id="Title" class="heading-1">${this.repo}</h1>
          <hr />
          <div>
            <a href=${this.getWebLink()?.url ?? ''}
              ><gr-button link ?disabled=${!this.getWebLink()?.url}
                >Browse</gr-button
              ></a
            ><a href=${this.computeChangesUrl(this.repo)}
              ><gr-button link>View Changes</gr-button></a
            >
          </div>
        </div>
        ${when(
          this.loading || !this.repoConfig,
          () => html`<div id="loading">Loading...</div>`,
          () => html`<div id="loadedContent">
            ${this.renderDownloadCommands()}
            <h2
              id="configurations"
              class="heading-2 ${configChanged ? 'edited' : ''}"
            >
              Configurations
            </h2>
            <div id="form">
              <fieldset>
                ${this.renderDescription()} ${this.renderRepoOptions()}
                ${this.renderPluginConfig()}
                <gr-button
                  id="saveBtn"
                  ?disabled=${this.readOnly ||
                  this.disableSaveWithoutReview ||
                  !configChanged}
                  @click=${this.handleSaveRepoConfig}
                  >Save Changes</gr-button
                >
                <gr-button
                  id="saveReviewBtn"
                  ?disabled=${this.readOnly || !configChanged}
                  @click=${this.handleSaveRepoConfigForReview}
                  >Save For Review</gr-button
                >
              </fieldset>
              <gr-endpoint-decorator name="repo-config">
                <gr-endpoint-param
                  name="repoName"
                  .value=${this.repo}
                ></gr-endpoint-param>
                <gr-endpoint-param
                  name="readOnly"
                  .value=${this.readOnly}
                ></gr-endpoint-param>
              </gr-endpoint-decorator>
            </div>
          </div>`
        )}
      </div>
    `;
  }

  private renderDownloadCommands() {
    if (!this.schemes.length) return nothing;
    return html`
      <div id="downloadContent">
        <h2 id="download" class="heading-2">Download</h2>
        <fieldset>
          <gr-download-commands
            id="downloadCommands"
            .commands=${this.computeCommands()}
            .schemes=${this.schemes}
            .selectedScheme=${this.selectedScheme}
            .description=${this.computeDescription()}
            @selected-scheme-changed=${(e: BindValueChangeEvent) => {
              if (this.loading) return;
              this.selectedScheme = e.detail.value;
            }}
          ></gr-download-commands>
        </fieldset>
      </div>
    `;
  }

  private renderDescription() {
    assertIsDefined(this.repoConfig, 'repoConfig');
    return html`
      <h3 id="Description" class="heading-3">Description</h3>
      <fieldset>
        <gr-autogrow-textarea
          id="descriptionInput"
          class="description"
          autocomplete="on"
          placeholder="&lt;Insert repo description here&gt;"
          .rows=${4}
          .maxRows=${10}
          .value=${this.repoConfig.description ?? ''}
          ?disabled=${this.readOnly}
          @input=${this.handleDescriptionInput}
        ></gr-autogrow-textarea>
      </fieldset>
    `;
  }

  private renderRepoOptions() {
    return html`
      <h3 id="Options" class="heading-3">Repository Options</h3>
      <fieldset id="options">
        ${this.renderState()} ${this.renderSubmitType()}
        ${this.renderContentMerges()} ${this.renderNewChange()}
        ${this.renderChangeId()} ${this.renderEnableSignedPush()}
        ${this.renderRequireSignedPush()} ${this.renderRejectImplicitMerges()}
        ${this.renderUnRegisteredCc()} ${this.renderPrivateByDefault()}
        ${this.renderWorkInProgressByDefault()} ${this.renderMaxGitObjectSize()}
        ${this.renderMatchAuthoredDateWithCommitterDate()}
        ${this.renderRejectEmptyCommit()}
      </fieldset>
      <h3 id="Options" class="heading-3">Contributor Agreements</h3>
      <fieldset id="agreements">
        ${this.renderContributorAgreement()} ${this.renderUseSignedOffBy()}
      </fieldset>
    `;
  }

  private renderState() {
    return html`
      <section>
        <span class="title">State</span>
        <span class="value">
          <md-outlined-select
            id="stateSelect"
            value=${this.repoConfig?.state ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleStateSelectChange}
          >
            ${this.states.map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderSubmitType() {
    return html`
      <section>
        <span class="title">Submit type</span>
        <span class="value">
          <md-outlined-select
            id="submitTypeSelect"
            value=${this.repoConfig?.submit_type ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleSubmitTypeSelectChange}
          >
            ${this.formatSubmitTypeSelect(this.repoConfig).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderContentMerges() {
    return html`
      <section>
        <span class="title">Allow content merges</span>
        <span class="value">
          <md-outlined-select
            id="contentMergeSelect"
            value=${this.repoConfig?.use_content_merge?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleContentMergeSelectChange}
          >
            ${this.formatBooleanSelect(this.repoConfig?.use_content_merge).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderNewChange() {
    return html`
      <section>
        <span class="title">
          Create a new change for every commit not in the target branch
        </span>
        <span class="value">
          <md-outlined-select
            id="newChangeSelect"
            value=${this.repoConfig?.create_new_change_for_all_not_in_target
              ?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleNewChangeSelectChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.create_new_change_for_all_not_in_target
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderChangeId() {
    return html`
      <section>
        <span class="title">Require Change-Id in commit message</span>
        <span class="value">
          <md-outlined-select
            id="requireChangeIdSelect"
            value=${this.repoConfig?.require_change_id?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleRequireChangeIdSelectChange}
          >
            ${this.formatBooleanSelect(this.repoConfig?.require_change_id).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderEnableSignedPush() {
    return html`
      <section
        id="enableSignedPushSettings"
        class="repositorySettings ${this.repoConfig?.enable_signed_push
          ? 'showConfig'
          : ''}"
      >
        <span class="title">Enable signed push</span>
        <span class="value">
          <md-outlined-select
            id="enableSignedPush"
            value=${this.repoConfig?.enable_signed_push?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleEnableSignedPushChange}
          >
            ${this.formatBooleanSelect(this.repoConfig?.enable_signed_push).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderRequireSignedPush() {
    return html`
      <section
        id="requireSignedPushSettings"
        class="repositorySettings ${this.repoConfig?.require_signed_push
          ? 'showConfig'
          : ''}"
      >
        <span class="title">Require signed push</span>
        <span class="value">
          <md-outlined-select
            id="requireSignedPush"
            value=${this.repoConfig?.require_signed_push?.configured_value ??
            ''}
            ?disabled=${this.readOnly}
            @change=${this.handleRequireSignedPushChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.require_signed_push
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderRejectImplicitMerges() {
    return html`
      <section>
        <span class="title">
          Reject implicit merges when changes are pushed for review</span
        >
        <span class="value">
          <md-outlined-select
            id="rejectImplicitMergesSelect"
            value=${this.repoConfig?.reject_implicit_merges?.configured_value ??
            ''}
            ?disabled=${this.readOnly}
            @change=${this.handleRejectImplicitMergeSelectChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.reject_implicit_merges
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderUnRegisteredCc() {
    return html`
      <section>
        <span class="title">
          Enable adding unregistered users as reviewers and CCs on changes</span
        >
        <span class="value">
          <md-outlined-select
            id="unRegisteredCcSelect"
            value=${this.repoConfig?.enable_reviewer_by_email
              ?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleUnRegisteredCcSelectChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.enable_reviewer_by_email
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderPrivateByDefault() {
    return html`
      <section>
        <span class="title"> Set all new changes private by default</span>
        <span class="value">
          <md-outlined-select
            id="setAllnewChangesPrivateByDefaultSelect"
            value=${this.repoConfig?.private_by_default?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleSetAllNewChangesPrivateByDefaultSelectChange}
          >
            ${this.formatBooleanSelect(this.repoConfig?.private_by_default).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderWorkInProgressByDefault() {
    return html`
      <section>
        <span class="title">
          Set new changes to "work in progress" by default</span
        >
        <span class="value">
          <md-outlined-select
            id="setAllNewChangesWorkInProgressByDefaultSelect"
            value=${this.repoConfig?.work_in_progress_by_default
              ?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this
              .handleSetAllNewChangesWorkInProgressByDefaultSelectChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.work_in_progress_by_default
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderMaxGitObjectSize() {
    return html`
      <section>
        <span class="title">Maximum Git object size limit</span>
        <span class="value">
          <md-outlined-text-field
            id="maxGitObjSizeInput"
            class="showBlueFocusBorder"
            type="number"
            min="0"
            ?disabled=${this.readOnly}
            .value=${this.repoConfig?.max_object_size_limit?.configured_value ??
            ''}
            @input=${this.handleMaxGitObjSizeInputChanged}
          >
          </md-outlined-text-field>
          ${this.repoConfig?.max_object_size_limit?.value
            ? `effective: ${this.repoConfig.max_object_size_limit.value} bytes`
            : ''}
        </span>
      </section>
    `;
  }

  private renderMatchAuthoredDateWithCommitterDate() {
    return html`
      <section>
        <span class="title"
          >Match authored date with committer date upon submit</span
        >
        <span class="value">
          <md-outlined-select
            id="matchAuthoredDateWithCommitterDateSelect"
            value=${this.repoConfig?.match_author_to_committer_date
              ?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleMatchAuthoredDateWithCommitterDateSelectChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.match_author_to_committer_date
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderRejectEmptyCommit() {
    return html`
      <section>
        <span class="title">Reject empty commit upon submit</span>
        <span class="value">
          <md-outlined-select
            id="rejectEmptyCommitSelect"
            value=${this.repoConfig?.reject_empty_commit?.configured_value ??
            ''}
            ?disabled=${this.readOnly}
            @change=${this.handleRejectEmptyCommitSelectChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.reject_empty_commit
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderContributorAgreement() {
    return html`
      <section>
        <span class="title">
          Require a valid contributor agreement to upload</span
        >
        <span class="value">
          <md-outlined-select
            id="contributorAgreementSelect"
            value=${this.repoConfig?.use_contributor_agreements
              ?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleUseContributorAgreementsChange}
          >
            ${this.formatBooleanSelect(
              this.repoConfig?.use_contributor_agreements
            ).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderUseSignedOffBy() {
    return html`
      <section>
        <span class="title">Require Signed-off-by in commit message</span>
        <span class="value">
          <md-outlined-select
            id="useSignedOffBySelect"
            value=${this.repoConfig?.use_signed_off_by?.configured_value ?? ''}
            ?disabled=${this.readOnly}
            @change=${this.handleUseSignedOffBySelectChange}
          >
            ${this.formatBooleanSelect(this.repoConfig?.use_signed_off_by).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.label}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
        </span>
      </section>
    `;
  }

  private renderPluginConfig() {
    const pluginData = this.computePluginData();
    if (!pluginData.length) return nothing;
    return html` <div
      class="pluginConfig"
      @plugin-config-changed=${this.handlePluginConfigChanged}
    >
      <h3 class="heading-3">Plugins</h3>
      ${pluginData.map(
        item => html`
          <gr-repo-plugin-config
            .pluginData=${item}
            ?disabled=${this.readOnly}
          ></gr-repo-plugin-config>
        `
      )}
    </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.loadRepo();
    }
    if (changedProperties.has('schemesObj')) {
      this.computeSchemesAndDefault();
    }
  }

  // private but used in test
  computePluginData() {
    if (!this.repoConfig || !this.repoConfig.plugin_config) return [];
    const pluginConfig = this.repoConfig.plugin_config;
    return Object.keys(pluginConfig).map(name => {
      return {name, config: pluginConfig[name]};
    });
  }

  // private but used in test
  async loadRepo() {
    if (!this.repo) return Promise.resolve();
    this.repoConfig = undefined;
    this.originalConfig = undefined;
    this.loading = true;
    this.weblinks = [];
    this.schemesObj = undefined;
    this.readOnly = true;

    const promises = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    promises.push(
      this.restApiService.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          const repo = this.repo;
          if (!repo) throw new Error('undefined repo');
          this.restApiService.getRepo(repo).then(repo => {
            if (!repo?.web_links) return;
            this.weblinks = repo.web_links;
          });
          this.restApiService.getRepoAccess(repo).then(access => {
            if (!access || this.repo !== repo) {
              return;
            }

            // If the user is not an owner, is_owner is not a property.
            this.readOnly = !access[repo]?.is_owner;
            this.disableSaveWithoutReview =
              !!access[repo]?.require_change_for_config_update;
          });
        }
      })
    );

    const repoConfigHelper = async () => {
      const config = await this.restApiService.getProjectConfig(
        this.repo as RepoName,
        errFn
      );
      if (!config) return;

      if (config.default_submit_type) {
        // The gr-select is bound to submit_type, which needs to be the
        // *configured* submit type. When default_submit_type is
        // present, the server reports the *effective* submit type in
        // submit_type, so we need to overwrite it before storing the
        // config in this.
        config.submit_type = config.default_submit_type.configured_value;
      }
      if (!config.state) {
        config.state = STATES.active.value;
      }
      // To properly check if the config has changed we need it to be a string
      // as it's converted to a string in the input.
      if (config.description === undefined) {
        config.description = '';
      }
      // To properly check if the config has changed we need it to be a string
      // as it's converted to a string in the input.
      if (config.max_object_size_limit.configured_value === undefined) {
        config.max_object_size_limit.configured_value = '';
      }
      this.repoConfig = config;
      this.originalConfig = deepClone<ConfigInfo>(config);
      this.loading = false;
    };
    promises.push(repoConfigHelper());

    const configHelper = async () => {
      const config = await this.restApiService.getConfig();
      if (!config) return;

      this.schemesObj = config.download.schemes;
    };
    promises.push(configHelper());

    await Promise.all(promises);
  }

  // private but used in test
  formatBooleanSelect(item?: InheritedBooleanInfo) {
    if (!item) return [];
    let inheritLabel = 'Inherit';
    if (!(item.inherited_value === undefined)) {
      inheritLabel = `Inherit (${item.inherited_value})`;
    }
    return [
      {
        label: inheritLabel,
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ];
  }

  private getWebLink() {
    return computeMainCodeBrowserWeblink(this.weblinks, this.serverConfig);
  }

  private formatSubmitTypeSelect(repoConfig?: ConfigInfo) {
    if (!repoConfig) return [];
    const allValues = Object.values(SUBMIT_TYPES);
    const type = repoConfig.default_submit_type;
    if (!type) {
      // Server is too old to report default_submit_type, so assume INHERIT
      // is not a valid value.
      return allValues;
    }

    let inheritLabel = 'Inherit';
    if (type.inherited_value) {
      inheritLabel = `Inherit (${type.inherited_value})`;
      for (const val of allValues) {
        if (val.value === type.inherited_value) {
          inheritLabel = `Inherit (${val.label})`;
          break;
        }
      }
    }
    return [
      {
        label: inheritLabel,
        value: 'INHERIT',
      },
      ...allValues,
    ];
  }

  // private but used in test
  formatRepoConfigForSave(repoConfig?: ConfigInfo): ConfigInput {
    if (!repoConfig) return {};
    const configInputObj: ConfigInput = {};
    for (const configKey of Object.keys(repoConfig)) {
      const key = configKey as keyof ConfigInfo;
      if (key === 'default_submit_type') {
        // default_submit_type is not in the input type, and the
        // configured value was already copied to submit_type by
        // _loadProject. Omit this property when saving.
        continue;
      }
      if (key === 'plugin_config') {
        configInputObj.plugin_config_values = repoConfig.plugin_config;
      } else if (typeof repoConfig[key] === 'object') {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const repoConfigObj: any = repoConfig[key];
        if (repoConfigObj.configured_value !== undefined) {
          configInputObj[key as keyof ConfigInput] =
            repoConfigObj.configured_value;
        }
      } else {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        configInputObj[key as keyof ConfigInput] = repoConfig[key] as any;
      }
    }
    return configInputObj;
  }

  // private but used in test
  async handleSaveRepoConfig() {
    if (!this.repoConfig || !this.repo)
      return Promise.reject(new Error('undefined repoConfig or repo'));
    await this.restApiService.saveRepoConfig(
      this.repo,
      this.formatRepoConfigForSave(this.repoConfig)
    );
    this.originalConfig = deepClone<ConfigInfo>(this.repoConfig);
    this.pluginConfigChanged = false;
    return;
  }

  async handleSaveRepoConfigForReview(e: Event) {
    if (!this.repoConfig || !this.repo)
      return Promise.reject(new Error('undefined repoConfig or repo'));
    const button = e && (e.target as GrButton);
    if (button) {
      button.loading = true;
    }

    return this.restApiService
      .saveRepoConfigForReview(
        this.repo,
        this.formatRepoConfigForSave(this.repoConfig)
      )
      .then(change => {
        // Don't navigate on server error.
        if (change) {
          this.getNavigation().setUrl(createChangeUrl({change}));
        }
      })
      .finally(() => {
        if (button) {
          button.loading = false;
        }
      });
  }

  private isEdited(
    original?: InheritedBooleanInfo | MaxObjectSizeLimitInfo,
    repo?: InheritedBooleanInfo | MaxObjectSizeLimitInfo
  ) {
    return original?.configured_value !== repo?.configured_value;
  }

  private hasConfigChanged() {
    const {repoConfig, originalConfig} = this;

    if (!repoConfig || !originalConfig) return false;

    if (originalConfig.description !== repoConfig.description) {
      return true;
    }
    if (originalConfig.state !== repoConfig.state) {
      return true;
    }
    if (originalConfig.submit_type !== repoConfig.submit_type) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.use_content_merge,
        repoConfig.use_content_merge
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.create_new_change_for_all_not_in_target,
        repoConfig.create_new_change_for_all_not_in_target
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.require_change_id,
        repoConfig.require_change_id
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.enable_signed_push,
        repoConfig.enable_signed_push
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.require_signed_push,
        repoConfig.require_signed_push
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.reject_implicit_merges,
        repoConfig.reject_implicit_merges
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.enable_reviewer_by_email,
        repoConfig.enable_reviewer_by_email
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.private_by_default,
        repoConfig.private_by_default
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.work_in_progress_by_default,
        repoConfig.work_in_progress_by_default
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.max_object_size_limit,
        repoConfig.max_object_size_limit
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.match_author_to_committer_date,
        repoConfig.match_author_to_committer_date
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.reject_empty_commit,
        repoConfig.reject_empty_commit
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.use_contributor_agreements,
        repoConfig.use_contributor_agreements
      )
    ) {
      return true;
    }
    if (
      this.isEdited(
        originalConfig.use_signed_off_by,
        repoConfig.use_signed_off_by
      )
    ) {
      return true;
    }

    return this.pluginConfigChanged;
  }

  private computeSchemesAndDefault() {
    this.schemes = !this.schemesObj ? [] : Object.keys(this.schemesObj).sort();
    if (this.schemes.length > 0) {
      if (!this.selectedScheme || !this.schemes.includes(this.selectedScheme)) {
        this.selectedScheme = this.schemes.sort()[0];
      }
    }
  }

  private getSchemeInfo(): DownloadSchemeInfo | undefined {
    if (!this.schemesObj || !this.repo || !this.selectedScheme) {
      return undefined;
    }
    if (!hasOwnProperty(this.schemesObj, this.selectedScheme)) return undefined;
    return this.schemesObj[this.selectedScheme];
  }

  private computeDescription() {
    const schemeInfo = this.getSchemeInfo();
    return schemeInfo?.description;
  }

  private computeCommands(): Command[] {
    const schemeInfo = this.getSchemeInfo();
    if (!this.repo || !schemeInfo) return [];

    const commandObj = schemeInfo.clone_commands ?? {};
    const commands = [];
    for (const [title, command] of Object.entries(commandObj)) {
      commands.push({
        title,
        command: command
          .replace(/\${project}/gi, encodeURI(this.repo))
          .replace(
            /\${project-base-name}/gi,
            encodeURI(this.repo.substring(this.repo.lastIndexOf('/') + 1))
          ),
      });
    }
    return commands;
  }

  private computeChangesUrl(name?: RepoName) {
    if (!name) return '';
    return createSearchUrl({repo: name});
  }

  // private but used in test
  handlePluginConfigChanged({
    detail: {name, config},
  }: {
    detail: {
      name: string;
      config: PluginParameterToConfigParameterInfoMap;
    };
  }) {
    if (this.repoConfig?.plugin_config) {
      this.repoConfig.plugin_config[name] = config;
      this.pluginConfigChanged = true;
      this.requestUpdate();
    }
  }

  private handleDescriptionInput(e: InputEvent) {
    if (!this.repoConfig || this.loading) return;
    const value = (e.target as GrAutogrowTextarea).value ?? '';
    if (this.repoConfig.description === value) return;
    this.repoConfig = {
      ...this.repoConfig,
      description: value,
    };
    this.requestUpdate();
  }

  private handleStateSelectChange(e: Event) {
    if (!this.repoConfig || this.loading) return;
    const select = e.target as HTMLSelectElement;
    if (this.repoConfig.state === select.value) return;
    this.repoConfig = {
      ...this.repoConfig,
      state: select.value as RepoState,
    };
    this.requestUpdate();
  }

  private handleSubmitTypeSelectChange(e: Event) {
    if (!this.repoConfig || this.loading) return;
    const select = e.target as HTMLSelectElement;
    if (this.repoConfig.submit_type === select.value) return;
    this.repoConfig = {
      ...this.repoConfig,
      submit_type: select.value as SubmitType,
    };
    this.requestUpdate();
  }

  private handleContentMergeSelectChange(e: Event) {
    if (!this.repoConfig?.use_content_merge || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.use_content_merge.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleNewChangeSelectChange(e: Event) {
    if (
      !this.repoConfig?.create_new_change_for_all_not_in_target ||
      this.loading
    )
      return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.create_new_change_for_all_not_in_target.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRequireChangeIdSelectChange(e: Event) {
    if (!this.repoConfig?.require_change_id || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.require_change_id.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleEnableSignedPushChange(e: Event) {
    if (!this.repoConfig?.enable_signed_push || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.enable_signed_push.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRequireSignedPushChange(e: Event) {
    if (!this.repoConfig?.require_signed_push || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.require_signed_push.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRejectImplicitMergeSelectChange(e: Event) {
    if (!this.repoConfig?.reject_implicit_merges || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.reject_implicit_merges.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleUnRegisteredCcSelectChange(e: Event) {
    if (!this.repoConfig?.enable_reviewer_by_email || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.enable_reviewer_by_email.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleSetAllNewChangesPrivateByDefaultSelectChange(e: Event) {
    if (!this.repoConfig?.private_by_default || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.private_by_default.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleSetAllNewChangesWorkInProgressByDefaultSelectChange(e: Event) {
    if (!this.repoConfig?.work_in_progress_by_default || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.work_in_progress_by_default.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleMaxGitObjSizeInputChanged(e: InputEvent) {
    if (!this.repoConfig?.max_object_size_limit || this.loading) return;
    const target = e.target as HTMLInputElement;
    this.repoConfig.max_object_size_limit.value = target.value;
    this.repoConfig.max_object_size_limit.configured_value = target.value;
    this.requestUpdate();
  }

  private handleMatchAuthoredDateWithCommitterDateSelectChange(e: Event) {
    if (!this.repoConfig?.match_author_to_committer_date || this.loading)
      return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.match_author_to_committer_date.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRejectEmptyCommitSelectChange(e: Event) {
    if (!this.repoConfig?.reject_empty_commit || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.reject_empty_commit.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleUseContributorAgreementsChange(e: Event) {
    if (!this.repoConfig?.use_contributor_agreements || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.use_contributor_agreements.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleUseSignedOffBySelectChange(e: Event) {
    if (!this.repoConfig?.use_signed_off_by || this.loading) return;
    const select = e.target as HTMLSelectElement;
    this.repoConfig.use_signed_off_by.configured_value =
      select.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }
}
