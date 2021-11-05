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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '@polymer/iron-input/iron-input';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-download-commands/gr-download-commands';
import '../../shared/gr-select/gr-select';
import '../gr-repo-plugin-config/gr-repo-plugin-config';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  ConfigInfo,
  RepoName,
  InheritedBooleanInfo,
  SchemesInfoMap,
  ConfigInput,
  PluginParameterToConfigParameterInfoMap,
} from '../../../types/common';
import {
  InheritedBooleanInfoConfiguredValue,
  ProjectState,
  SubmitType,
} from '../../../constants/constants';
import {hasOwnProperty} from '../../../utils/common-util';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {WebLinkInfo} from '../../../types/diff';
import {ErrorCallback} from '../../../api/rest';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {deepClone} from '../../../utils/object-util';

const STATES = {
  active: {value: ProjectState.ACTIVE, label: 'Active'},
  readOnly: {value: ProjectState.READ_ONLY, label: 'Read Only'},
  hidden: {value: ProjectState.HIDDEN, label: 'Hidden'},
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

@customElement('gr-repo')
export class GrRepo extends LitElement {
  private schemes: string[] = [];

  @property({type: String})
  repo?: RepoName;

  /* private but used in test */
  @state() loading = true;

  /* private but used in test */
  @state() repoConfig?: ConfigInfo;

  /* private but used in test */
  @state() readOnly = true;

  @state() private states = Object.values(STATES);

  @state() private originalConfig?: ConfigInfo;

  /* private but used in test */
  // This is workaround to have _schemes with default value [],
  // because assignment doesn't work when property has a computed attribute.
  @state() schemesDefault: string[] = [];

  @state() private selectedScheme?: string;

  /* private but used in test */
  @state() schemesObj?: SchemesInfoMap;

  @state() private weblinks: WebLinkInfo[] = [];

  @state() private pluginConfigChanged = false;

  private readonly restApiService = appContext.restApiService;

  override async connectedCallback() {
    super.connectedCallback();
    await this.loadRepo();

    fireTitleChange(this, `${this.repo}`);
  }

  static override get styles() {
    return [
      fontStyles,
      formStyles,
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
        .loading,
        .hide {
          display: none;
        }
        #loading.loading {
          display: block;
        }
        #loading:not(.loading) {
          display: none;
        }
        #options .repositorySettings {
          display: none;
        }
        #options .repositorySettings.showConfig {
          display: block;
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
            <a href=${this.weblinks?.[0]?.url}
              ><gr-button link ?disabled=${!this.weblinks?.[0]?.url}
                >Browse</gr-button
              ></a
            ><a href=${this.computeChangesUrl(this.repo)}
              ><gr-button link>View Changes</gr-button></a
            >
          </div>
        </div>
        <div id="loading" class=${this.loading ? 'loading' : ''}>
          Loading...
        </div>
        <div id="loadedContent" class=${this.loading ? 'loading' : ''}>
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
                ?disabled=${this.readOnly || !configChanged}
                @click=${this.handleSaveRepoConfig}
                >Save changes</gr-button
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
        </div>
      </div>
    `;
  }

  private renderDownloadCommands() {
    return html`
      <div
        id="downloadContent"
        class=${!this.schemes || !this.schemes.length ? 'hide' : ''}
      >
        <h2 id="download" class="heading-2">Download</h2>
        <fieldset>
          <gr-download-commands
            id="downloadCommands"
            .commands=${this.computeCommands(
              this.repo,
              this.schemesObj,
              this.selectedScheme
            )}
            .schemes=${this.schemes}
            .selectedScheme${this.selectedScheme}
            @selected-scheme-changed=${this.handleSelectedSchemBindValueChanged}
          ></gr-download-commands>
        </fieldset>
      </div>
    `;
  }

  private renderDescription() {
    if (!this.repoConfig) return;
    return html`
      <h3 id="Description" class="heading-3">Description</h3>
      <fieldset>
        <iron-autogrow-textarea
          id="descriptionInput"
          class="description"
          autocomplete="on"
          placeholder="&lt;Insert repo description here&gt;"
          .bindValue=${this.repoConfig.description}
          ?disabled=${this.readOnly}
          @bind-value-changed=${this.handleDescriptionBindValueChanged}
        ></iron-autogrow-textarea>
      </fieldset>
    `;
  }

  private renderRepoOptions() {
    if (!this.repoConfig) return;
    return html`
      <h3 id="Options" class="heading-3">Repository Options</h3>
      <fieldset id="options">
        <section>
          <span class="title">State</span>
          <span class="value">
            <gr-select
              id="stateSelect"
              .bindValue=${this.repoConfig.state}
              @bind-value-changed=${this.handleStateSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.states.map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Submit type</span>
          <span class="value">
            <gr-select
              id="submitTypeSelect"
              .bindValue=${this.repoConfig.submit_type}
              @bind-value-changed=${this.handleSubmitTypeSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatSubmitTypeSelect(this.repoConfig).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Allow content merges</span>
          <span class="value">
            <gr-select
              id="contentMergeSelect"
              .bindValue=${this.repoConfig.use_content_merge?.configured_value}
              @bind-value-changed=${this
                .handleContentMergeSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.use_content_merge
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">
            Create a new change for every commit not in the target branch
          </span>
          <span class="value">
            <gr-select
              id="newChangeSelect"
              .bindValue=${this.repoConfig
                .create_new_change_for_all_not_in_target?.configured_value}
              @bind-value-changed=${this.handleNewChangeSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.create_new_change_for_all_not_in_target
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Require Change-Id in commit message</span>
          <span class="value">
            <gr-select
              id="requireChangeIdSelect"
              .bindValue=${this.repoConfig.require_change_id?.configured_value}
              @bind-value-changed=${this
                .handleRequireChangeIdSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.require_change_id
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section
          id="enableSignedPushSettings"
          class="repositorySettings ${this.repoConfig.enable_signed_push
            ? 'showConfig'
            : ''}"
        >
          <span class="title">Enable signed push</span>
          <span class="value">
            <gr-select
              id="enableSignedPush"
              .bindValue=${this.repoConfig.enable_signed_push?.configured_value}
              @bind-value-changed=${this.handleEnableSignedPushBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.enable_signed_push
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section
          id="requireSignedPushSettings"
          class="repositorySettings ${this.repoConfig.require_signed_push
            ? 'showConfig'
            : ''}"
        >
          <span class="title">Require signed push</span>
          <span class="value">
            <gr-select
              id="requireSignedPush"
              .bindValue=${this.repoConfig.require_signed_push
                ?.configured_value}
              @bind-value-changed=${this
                .handleRequireSignedPushBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.require_signed_push
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">
            Reject implicit merges when changes are pushed for review</span
          >
          <span class="value">
            <gr-select
              id="rejectImplicitMergesSelect"
              .bindValue=${this.repoConfig.reject_implicit_merges
                ?.configured_value}
              @bind-value-changed=${this
                .handleRejectImplicitMergeSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.reject_implicit_merges
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">
            Enable adding unregistered users as reviewers and CCs on
            changes</span
          >
          <span class="value">
            <gr-select
              id="unRegisteredCcSelect"
              .bindValue=${this.repoConfig.enable_reviewer_by_email
                ?.configured_value}
              @bind-value-changed=${this
                .handleUnRegisteredCcSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.enable_reviewer_by_email
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title"> Set all new changes private by default</span>
          <span class="value">
            <gr-select
              id="setAllnewChangesPrivateByDefaultSelect"
              .bindValue=${this.repoConfig.private_by_default?.configured_value}
              @bind-value-changed=${this
                .handleSetAllNewChangesPrivateByDefaultSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.private_by_default
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">
            Set new changes to "work in progress" by default</span
          >
          <span class="value">
            <gr-select
              id="setAllNewChangesWorkInProgressByDefaultSelect"
              .bindValue=${this.repoConfig.work_in_progress_by_default
                ?.configured_value}
              @bind-value-changed=${this
                .handleSetAllNewChangesWorkInProgressByDefaultSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.work_in_progress_by_default
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Maximum Git object size limit</span>
          <span class="value">
            <iron-input
              id="maxGitObjSizeIronInput"
              .bindValue=${this.repoConfig.max_object_size_limit
                ?.configured_value}
              .type="text"
              ?disabled=${this.readOnly}
              @bind-value-changed=${this.handleMaxGitObjSizeBindValueChanged}
            >
              <input
                id="maxGitObjSizeInput"
                type="text"
                ?disabled=${this.readOnly}
              />
            </iron-input>
            ${this.repoConfig.max_object_size_limit.value
              ? `effective: ${this.repoConfig.max_object_size_limit.value} bytes`
              : ''}
          </span>
        </section>
        <section>
          <span class="title"
            >Match authored date with committer date upon submit</span
          >
          <span class="value">
            <gr-select
              id="matchAuthoredDateWithCommitterDateSelect"
              .bindValue=${this.repoConfig.match_author_to_committer_date
                ?.configured_value}
              @bind-value-changed=${this
                .handleMatchAuthoredDateWithCommitterDateSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.match_author_to_committer_date
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Reject empty commit upon submit</span>
          <span class="value">
            <gr-select
              id="rejectEmptyCommitSelect"
              .bindValue=${this.repoConfig.reject_empty_commit
                ?.configured_value}
              @bind-value-changed=${this
                .handleRejectEmptyCommitSelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.reject_empty_commit
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
      </fieldset>
      <h3 id="Options" class="heading-3">Contributor Agreements</h3>
      <fieldset id="agreements">
        <section>
          <span class="title">
            Require a valid contributor agreement to upload</span
          >
          <span class="value">
            <gr-select
              id="contributorAgreementSelect"
              .bindValue=${this.repoConfig.use_contributor_agreements
                ?.configured_value}
              @bind-value-changed=${this
                .handleUseContributorAgreementsBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.use_contributor_agreements
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Require Signed-off-by in commit message</span>
          <span class="value">
            <gr-select
              id="useSignedOffBySelect"
              .bindValue=${this.repoConfig.use_signed_off_by?.configured_value}
              @bind-value-changed=${this
                .handleUseSignedOffBySelectBindValueChanged}
            >
              <select ?disabled=${this.readOnly}>
                ${this.formatBooleanSelect(
                  this.repoConfig.use_signed_off_by
                ).map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
      </fieldset>
    `;
  }

  private renderPluginConfig() {
    const pluginData = this.computePluginData();
    return html` <div
        class="pluginConfig ${!pluginData || !pluginData.length ? 'hide' : ''}"
        @plugin-config-changed=${this.handlePluginConfigChanged}
      >
        <h3 class="heading-3">Plugins</h3>
        ${pluginData.map(
          item => html`
            <gr-repo-plugin-config .pluginData=${item}></gr-repo-plugin-config>
          `
        )}
      </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('schemesDefault') ||
      changedProperties.has('schemesObj')
    ) {
      this.computeSchemesAndDefault();
    }
  }

  /* private but used in test */
  computePluginData() {
    if (!this.repoConfig || !this.repoConfig.plugin_config) return [];
    const pluginConfig = this.repoConfig.plugin_config;
    return Object.keys(pluginConfig).map(name => {
      return {name, config: pluginConfig[name]};
    });
  }

  /* private but used in test */
  async loadRepo() {
    if (!this.repo) return Promise.resolve();

    const promises = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const loggedInHelper = async () => {
      const loggedIn = await this.restApiService.getLoggedIn();
      if (loggedIn) {
        const repoName = this.repo;
        if (!repoName) throw new Error('undefined repo');
        const prefs = await this.restApiService.getPreferences();
        if (prefs?.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this.selectedScheme = prefs.download_scheme.toLowerCase();
        }
        const repo = await this.restApiService.getRepo(repoName);
        if (repo?.web_links) {
          this.weblinks = repo.web_links;
        }
        const access = await this.restApiService.getRepoAccess(repoName);
        if (!access || this.repo !== repoName) {
          return;
        }

        // If the user is not an owner, is_owner is not a property.
        this.readOnly = !access[repoName]?.is_owner;
      }
    };
    promises.push(loggedInHelper());

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
        config.state = STATES.active.value as ProjectState;
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
      this.originalConfig = deepClone(config);
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

  /* private but used in test */
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

  /* private but used in test */
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
        if (repoConfigObj.configured_value) {
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

  /* private but used in test */
  async handleSaveRepoConfig() {
    if (!this.repoConfig || !this.repo)
      return Promise.reject(new Error('undefined repoConfig or repo'));
    await this.restApiService.saveRepoConfig(
      this.repo,
      this.formatRepoConfigForSave(this.repoConfig)
    );
    this.originalConfig = deepClone(this.repoConfig);
    this.pluginConfigChanged = false;
    return;
  }

  private hasConfigChanged() {
    if (!this.originalConfig || !this.repoConfig) return false;

    const repoDescriptionEdited =
      this.originalConfig.description !== this.repoConfig.description;
    const repoStateEdited = this.originalConfig.state !== this.repoConfig.state;
    const repoSubmitTypeEdited =
      this.originalConfig.submit_type !== this.repoConfig.submit_type;
    const repoUseContentMergeEdited =
      this.originalConfig.use_content_merge?.configured_value !==
      this.repoConfig.use_content_merge?.configured_value;
    const repoCreateNewChangeForAllNotInTargetEdited =
      this.originalConfig.create_new_change_for_all_not_in_target
        ?.configured_value !==
      this.repoConfig.create_new_change_for_all_not_in_target?.configured_value;
    const repoRequireChangeIdEdited =
      this.originalConfig.require_change_id?.configured_value !==
      this.repoConfig.require_change_id?.configured_value;
    const repoEnableSignedPushEdited =
      this.originalConfig.enable_signed_push?.configured_value !==
      this.repoConfig.enable_signed_push?.configured_value;
    const repoRequireSignedPushEdited =
      this.originalConfig.require_signed_push?.configured_value !==
      this.repoConfig.require_signed_push?.configured_value;
    const repoRejectImplicitMergesEdited =
      this.originalConfig.reject_implicit_merges?.configured_value !==
      this.repoConfig.reject_implicit_merges?.configured_value;
    const repoEnableReviewerByEmailEdited =
      this.originalConfig.enable_reviewer_by_email?.configured_value !==
      this.repoConfig.enable_reviewer_by_email?.configured_value;
    const repoPrivateByDefaultEdited =
      this.originalConfig.private_by_default?.configured_value !==
      this.repoConfig.private_by_default?.configured_value;
    const repoWorkInProgressByDefaultEdited =
      this.originalConfig.work_in_progress_by_default?.configured_value !==
      this.repoConfig.work_in_progress_by_default?.configured_value;
    const repoMaxGitObjSizeEdited =
      this.originalConfig.max_object_size_limit?.configured_value !==
      this.repoConfig.max_object_size_limit?.configured_value;
    const repoMatchAuthoredDateWithCommitterDateSelectEdited =
      this.originalConfig.match_author_to_committer_date?.configured_value !==
      this.repoConfig.match_author_to_committer_date?.configured_value;
    const repoRejectEmptyCommitSelectEdited =
      this.originalConfig.reject_empty_commit?.configured_value !==
      this.repoConfig.reject_empty_commit?.configured_value;
    const repoUseContributorAgreementsEdited =
      this.originalConfig.use_contributor_agreements?.configured_value !==
      this.repoConfig.use_contributor_agreements?.configured_value;
    const repoUseSignedOffBySelectEdited =
      this.originalConfig.use_signed_off_by?.configured_value !==
      this.repoConfig.use_signed_off_by?.configured_value;
    const repoPluginConfigEdited = this.pluginConfigChanged;

    return (
      repoDescriptionEdited ||
      repoStateEdited ||
      repoSubmitTypeEdited ||
      repoUseContentMergeEdited ||
      repoCreateNewChangeForAllNotInTargetEdited ||
      repoRequireChangeIdEdited ||
      repoEnableSignedPushEdited ||
      repoRequireSignedPushEdited ||
      repoRejectImplicitMergesEdited ||
      repoEnableReviewerByEmailEdited ||
      repoPrivateByDefaultEdited ||
      repoWorkInProgressByDefaultEdited ||
      repoMaxGitObjSizeEdited ||
      repoMatchAuthoredDateWithCommitterDateSelectEdited ||
      repoRejectEmptyCommitSelectEdited ||
      repoUseContributorAgreementsEdited ||
      repoUseSignedOffBySelectEdited ||
      repoPluginConfigEdited
    );
  }

  private computeSchemesAndDefault() {
    this.schemes = !this.schemesObj
      ? this.schemesDefault
      : Object.keys(this.schemesObj);
    if (this.schemes.length > 0) {
      if (!this.selectedScheme || !this.schemes.includes(this.selectedScheme)) {
        this.selectedScheme = this.schemes.sort()[0];
      }
    }
  }

  private computeCommands(
    repo?: RepoName,
    schemesObj?: SchemesInfoMap,
    selectedScheme?: string
  ) {
    if (!schemesObj || !repo || !selectedScheme) return [];
    if (!hasOwnProperty(schemesObj, selectedScheme)) return [];
    const commandObj = schemesObj[selectedScheme].clone_commands;
    const commands = [];
    for (const [title, command] of Object.entries(commandObj)) {
      commands.push({
        title,
        command: command
          .replace(/\${project}/gi, encodeURI(repo))
          .replace(
            /\${project-base-name}/gi,
            encodeURI(repo.substring(repo.lastIndexOf('/') + 1))
          ),
      });
    }
    return commands;
  }

  private computeChangesUrl(name?: RepoName) {
    if (!name) return '';
    return GerritNav.getUrlForProjectChanges(name as RepoName);
  }

  /* private but used in test */
  handlePluginConfigChanged({
    detail: {name, config, notifyPath},
  }: {
    detail: {
      name: string;
      config: PluginParameterToConfigParameterInfoMap;
      notifyPath: string;
    };
  }) {
    if (this.repoConfig?.plugin_config) {
      this.repoConfig.plugin_config[name] = config;
      this.pluginConfigChanged = true;
      console.log(notifyPath);
      // this.notifyPath('repoConfig.plugin_config.' + notifyPath);
      this.requestUpdate();
    }
  }

  private handleSelectedSchemBindValueChanged(e: CustomEvent) {
    this.selectedScheme = e.detail.value;
  }

  private handleDescriptionBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || this.loading) return;
    this.repoConfig.description = e.detail.value;
    this.requestUpdate();
  }

  private handleStateSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || this.loading) return;
    this.repoConfig.state = e.detail.value as ProjectState;
    this.requestUpdate();
  }

  private handleSubmitTypeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || this.loading) return;
    this.repoConfig.submit_type = e.detail.value as SubmitType;
    this.requestUpdate();
  }

  private handleContentMergeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || !this.repoConfig.use_content_merge || this.loading)
      return;
    this.repoConfig.use_content_merge.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleNewChangeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.repoConfig ||
      !this.repoConfig.create_new_change_for_all_not_in_target ||
      this.loading
    )
      return;
    this.repoConfig.create_new_change_for_all_not_in_target.configured_value = e
      .detail.value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRequireChangeIdSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || !this.repoConfig.require_change_id || this.loading)
      return;
    this.repoConfig.require_change_id.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleEnableSignedPushBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || !this.repoConfig.enable_signed_push || this.loading)
      return;
    this.repoConfig.enable_signed_push.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRequireSignedPushBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.repoConfig ||
      !this.repoConfig.require_signed_push ||
      this.loading
    )
      return;
    this.repoConfig.require_signed_push.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRejectImplicitMergeSelectBindValueChanged(
    e: BindValueChangeEvent
  ) {
    if (
      !this.repoConfig ||
      !this.repoConfig.reject_implicit_merges ||
      this.loading
    )
      return;
    this.repoConfig.reject_implicit_merges.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleUnRegisteredCcSelectBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.repoConfig ||
      !this.repoConfig.enable_reviewer_by_email ||
      this.loading
    )
      return;
    this.repoConfig.enable_reviewer_by_email.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleSetAllNewChangesPrivateByDefaultSelectBindValueChanged(
    e: BindValueChangeEvent
  ) {
    if (!this.repoConfig || !this.repoConfig.private_by_default || this.loading)
      return;
    this.repoConfig.private_by_default.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleSetAllNewChangesWorkInProgressByDefaultSelectBindValueChanged(
    e: BindValueChangeEvent
  ) {
    if (
      !this.repoConfig ||
      !this.repoConfig.work_in_progress_by_default ||
      this.loading
    )
      return;
    this.repoConfig.work_in_progress_by_default.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleMaxGitObjSizeBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.repoConfig ||
      !this.repoConfig.max_object_size_limit ||
      this.loading
    )
      return;
    this.repoConfig.max_object_size_limit.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleMatchAuthoredDateWithCommitterDateSelectBindValueChanged(
    e: BindValueChangeEvent
  ) {
    if (
      !this.repoConfig ||
      !this.repoConfig.match_author_to_committer_date ||
      this.loading
    )
      return;
    this.repoConfig.match_author_to_committer_date.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleRejectEmptyCommitSelectBindValueChanged(
    e: BindValueChangeEvent
  ) {
    if (
      !this.repoConfig ||
      !this.repoConfig.reject_empty_commit ||
      this.loading
    )
      return;
    this.repoConfig.reject_empty_commit.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleUseContributorAgreementsBindValueChanged(
    e: BindValueChangeEvent
  ) {
    if (
      !this.repoConfig ||
      !this.repoConfig.use_contributor_agreements ||
      this.loading
    )
      return;
    this.repoConfig.use_contributor_agreements.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }

  private handleUseSignedOffBySelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this.repoConfig || !this.repoConfig.use_signed_off_by || this.loading)
      return;
    this.repoConfig.use_signed_off_by.configured_value = e.detail
      .value as InheritedBooleanInfoConfiguredValue;
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementEventMap {
    'selected-scheme-changed': CustomEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-repo': GrRepo;
  }
}
