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
import '../../../styles/gr-font-styles';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../gr-repo-plugin-config/gr-repo-plugin-config';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  ConfigInfo,
  RepoName,
  InheritedBooleanInfo,
  SchemesInfoMap,
  ConfigInput,
  PluginParameterToConfigParameterInfoMap,
  PluginNameToPluginParametersMap,
} from '../../../types/common';
import {PluginData} from '../gr-repo-plugin-config/gr-repo-plugin-config';
import {ProjectState} from '../../../constants/constants';
import {hasOwnProperty} from '../../../utils/common-util';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {WebLinkInfo} from '../../../types/diff';
import {ErrorCallback} from '../../../api/rest';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';

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
  @property({type: String})
  repo?: RepoName;

  @property({type: Boolean})
  _configChanged = false;

  @property({type: Boolean})
  _loading = true;

  @property({type: Boolean, observer: '_loggedInChanged'})
  _loggedIn = false;

  @property({type: Object})
  _repoConfig?: ConfigInfo;

  @property({
    type: Array,
    computed: '_computePluginData(_repoConfig.plugin_config.*)',
  })
  _pluginData?: PluginData[];

  @property({type: Boolean})
  _readOnly = true;

  @property({type: Array})
  _states = Object.values(STATES);

  @property({
    type: Array,
    computed: '_computeSchemes(_schemesDefault, _schemesObj)',
    observer: '_schemesChanged',
  })
  _schemes: string[] = [];

  // This is workaround to have _schemes with default value [],
  // because assignment doesn't work when property has a computed attribute.
  @property({type: Array})
  _schemesDefault: string[] = [];

  @property({type: String})
  _selectedCommand = 'Clone';

  @property({type: String})
  _selectedScheme?: string;

  @property({type: Object})
  _schemesObj?: SchemesInfoMap;

  @property({type: Array})
  weblinks: WebLinkInfo[] = [];

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this._loadRepo();

    fireTitleChange(this, `${this.repo}`);
  }

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
      css`
        .genericList tr td:last-of-type {
          text-align: left;
        }
        .genericList tr th:last-of-type {
          text-align: left;
        }
        .readOnly {
          text-align: center;
        }
        .changesLink,
        .name,
        .repositoryBrowser,
        .readOnly {
          white-space: nowrap;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="main gr-form-styles read-only">
        <div class="info">
          <h1 id="Title" class="heading-1">${this.repo}</h1>
          <hr />
          <div>
            <a href=${this._computeBrowseUrl(this.weblinks)}
              ><gr-button link ?disabled=${!this._computeBrowseUrl(this.weblinks)}
                >Browse</gr-button
              ></a
            ><a href=${this._computeChangesUrl(this.repo)}
              ><gr-button link>View Changes</gr-button></a
            >
          </div>
        </div>
        <div id="loading" class=${this._computeLoadingClass(this._loading)}>
          Loading...
        </div>
        <div id="loadedContent" class=${this._computeLoadingClass(this._loading)}>
          ${this.renderDownloadCommands()}
          <h2
            id="configurations"
            class="heading-2 ${this._computeHeaderClass(this._configChanged)}"
          >
            Configurations
          </h2>
          <div id="form">
            <fieldset>
              ${this.renderDescription()}
              ${this.renderOptions()}
              <div
                class$="pluginConfig [[_computeHideClass(_pluginData)]]"
                on-plugin-config-changed="_handlePluginConfigChanged"
              >
                <h3 class="heading-3">Plugins</h3>
                <template is="dom-repeat" items="[[_pluginData]]" as="data">
                  <gr-repo-plugin-config
                    plugin-data="[[data]]"
                  ></gr-repo-plugin-config>
                </template>
              </div>
              <gr-button
                on-click="_handleSaveRepoConfig"
                disabled$="[[_computeButtonDisabled(_readOnly, _configChanged)]]"
                >Save changes</gr-button
              >
            </fieldset>
            <gr-endpoint-decorator name="repo-config">
              <gr-endpoint-param
                name="repoName"
                value="[[repo]]"
              ></gr-endpoint-param>
              <gr-endpoint-param
                name="readOnly"
                value="[[_readOnly]]"
              ></gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
        </div>
      </div>
    `;
  }

  private renderDownloadCommands() {
    return html`
      <div id="downloadContent" class=${this._computeHideClass(this._schemes)}>
        <h2 id="download" class="heading-2">Download</h2>
        <fieldset>
          <gr-download-commands
            id="downloadCommands"
            .commands=${this._computeCommands(this.repo, this._schemesObj, this._selectedScheme)}
            .schemes=${this._schemes}
            .selectedScheme${this._selectedScheme}
            @selected-scheme-changed=${this.handleSelectedSchemBindValueChanged}
          ></gr-download-commands>
        </fieldset>
      </div>
    `;
  }

  private renderDescription() {
    return html`
      <h3 id="Description" class="heading-3">Description</h3>
      <fieldset>
        <iron-autogrow-textarea
          id="descriptionInput"
          class="description"
          autocomplete="on"
          placeholder="<Insert repo description here>"
          .bindValue=${this._repoConfig?.description}
          ?disabled=${this._readOnly}
          @bind-value-changed=${this.handleDescriptionBindValueChanged}
        ></iron-autogrow-textarea>
      </fieldset>
    `;
  }

  private renderOptions() {
    return html`
      <h3 id="Options" class="heading-3">Repository Options</h3>
      <fieldset id="options">
        <section>
          <span class="title">State</span>
          <span class="value">
            <gr-select id="stateSelect" bind-value=${this._repoConfig?.state} @bind-value-changed=${this.handleStateSelectBindValueChanged}>
              <select ?disabled=${this._readOnly}>
                ${this._states.map(
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
              bind-value=${this._repoConfig?.submit_type}
              @bind-value-changed=${this.handleSubmitTypeSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatSubmitTypeSelect(this._repoConfig).map(
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
              bind-value=${this.repoConfig?.use_content_merge.configured_value}
              @bind-value-changed=${this.handleContentMergeSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.use_content_merge).map(
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
              bind-value=${this._repoConfig?.create_new_change_for_all_not_in_target.configured_value}
              @bind-value-changed=${this.handleNewChangeSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.create_new_change_for_all_not_in_target).map(
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
              bind-value=${this._repoConfig?.require_change_id.configured_value}
              @bind-value-changed=${this.handleRequireChangeIdSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.require_change_id).map(
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
          class="repositorySettings ${this._computeRepositoriesClass(this._repoConfig?.enable_signed_push)}"
        >
          <span class="title">Enable signed push</span>
          <span class="value">
            <gr-select
              id="enableSignedPush"
              bind-value=${this._repoConfig?.enable_signed_push.configured_value}
              @bind-value-changed=${this.handleEnableSignedPushBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.enable_signed_push).map(
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
          class="repositorySettings ${this._computeRepositoriesClass(this._repoConfig?.require_signed_push)}"
        >
          <span class="title">Require signed push</span>
          <span class="value">
            <gr-select
              id="requireSignedPush"
              bind-value=${this._repoConfig.require_signed_push.configured_value}
              @bind-value-changed=${this.handleRequireSignedPushBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.require_signed_push).map(
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
              bind-value=${this._repoConfig?.reject_implicit_merges.configured_value}
              @bind-value-changed=${this.handleRejectImplicitMergeSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.reject_implicit_merges).map(
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
              bind-value=${this._repoConfig?.enable_reviewer_by_email.configured_value}
              @bind-value-changed=${this.handleUnRegisteredCcSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.enable_reviewer_by_email).map(
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
              bind-value=${this._repoConfig?.private_by_default.configured_value}
              @bind-value-changed=${this.handleSetAllNewChangesPrivateByDefaultSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.private_by_default).map(
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
              bind-value=${this._repoConfig?.work_in_progress_by_default.configured_value}
              @bind-value-changed=${this.handleSetAllNewChangesWorkInProgressByDefaultSelectBindValueChanged}
            >
              <select ?disabled=${this._readOnly}>
                ${this._formatBooleanSelect(this._repoConfig?.work_in_progress_by_default).map(
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
              bind-value="{{_repoConfig.max_object_size_limit.configured_value}}"
              type="text"
              disabled$="[[_readOnly]]"
            >
              <input
                id="maxGitObjSizeInput"
                bind-value="{{_repoConfig.max_object_size_limit.configured_value}}"
                is="iron-input"
                type="text"
                disabled$="[[_readOnly]]"
              />
            </iron-input>
            <template
              is="dom-if"
              if="[[_repoConfig.max_object_size_limit.value]]"
            >
              effective: [[_repoConfig.max_object_size_limit.value]] bytes
            </template>
          </span>
        </section>
        <section>
          <span class="title"
            >Match authored date with committer date upon submit</span
          >
          <span class="value">
            <gr-select
              id="matchAuthoredDateWithCommitterDateSelect"
              bind-value="{{_repoConfig.match_author_to_committer_date.configured_value}}"
            >
              <select disabled$="[[_readOnly]]">
                <template
                  is="dom-repeat"
                  items="[[_formatBooleanSelect(_repoConfig.match_author_to_committer_date)]]"
                >
                  <option value="[[item.value]]">[[item.label]]</option>
                </template>
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Reject empty commit upon submit</span>
          <span class="value">
            <gr-select
              id="rejectEmptyCommitSelect"
              bind-value="{{_repoConfig.reject_empty_commit.configured_value}}"
            >
              <select disabled$="[[_readOnly]]">
                <template
                  is="dom-repeat"
                  items="[[_formatBooleanSelect(_repoConfig.reject_empty_commit)]]"
                >
                  <option value="[[item.value]]">[[item.label]]</option>
                </template>
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
              bind-value="{{_repoConfig.use_contributor_agreements.configured_value}}"
            >
              <select disabled$="[[_readOnly]]">
                <template
                  is="dom-repeat"
                  items="[[_formatBooleanSelect(_repoConfig.use_contributor_agreements)]]"
                >
                  <option value="[[item.value]]">[[item.label]]</option>
                </template>
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Require Signed-off-by in commit message</span>
          <span class="value">
            <gr-select
              id="useSignedOffBySelect"
              bind-value="{{_repoConfig.use_signed_off_by.configured_value}}"
            >
              <select disabled$="[[_readOnly]]">
                <template
                  is="dom-repeat"
                  items="[[_formatBooleanSelect(_repoConfig.use_signed_off_by)]]"
                >
                  <option value="[[item.value]]">[[item.label]]</option>
                </template>
              </select>
            </gr-select>
          </span>
        </section>
      </fieldset>
    `;
  }

  _computePluginData(
    configRecord?: PolymerDeepPropertyChange<
      PluginNameToPluginParametersMap,
      PluginNameToPluginParametersMap
    >
  ) {
    if (!configRecord || !configRecord.base) {
      return [];
    }

    const pluginConfig = configRecord.base;
    return Object.keys(pluginConfig).map(name => {
      return {name, config: pluginConfig[name]};
    });
  }

  _loadRepo() {
    if (!this.repo) {
      return Promise.resolve();
    }

    const promises = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    promises.push(
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
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
            this._readOnly = !access[repo]?.is_owner;
          });
        }
      })
    );

    promises.push(
      this.restApiService.getProjectConfig(this.repo, errFn).then(config => {
        if (!config) {
          return;
        }

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
        this._repoConfig = config;
        this._loading = false;
      })
    );

    promises.push(
      this.restApiService.getConfig().then(config => {
        if (!config) {
          return;
        }

        this._schemesObj = config.download.schemes;
      })
    );

    return Promise.all(promises);
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _computeHideClass(arr?: PluginData[] | string[]) {
    return !arr || !arr.length ? 'hide' : '';
  }

  _loggedInChanged(_loggedIn?: boolean) {
    if (!_loggedIn) {
      return;
    }
    this.restApiService.getPreferences().then(prefs => {
      if (prefs?.download_scheme) {
        // Note (issue 5180): normalize the download scheme with lower-case.
        this._selectedScheme = prefs.download_scheme.toLowerCase();
      }
    });
  }

  _formatBooleanSelect(item?: InheritedBooleanInfo) {
    if (!item) return;
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

  _formatSubmitTypeSelect(projectConfig?: ConfigInfo) {
    if (!projectConfig) return;
    const allValues = Object.values(SUBMIT_TYPES);
    const type = projectConfig.default_submit_type;
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

  _isLoading() {
    return this._loading || this._loading === undefined;
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _formatRepoConfigForSave(repoConfig?: ConfigInfo): ConfigInput {
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

  _handleSaveRepoConfig() {
    if (!this._repoConfig || !this.repo)
      return Promise.reject(new Error('undefined repoConfig or repo'));
    return this.restApiService
      .saveRepoConfig(
        this.repo,
        this._formatRepoConfigForSave(this._repoConfig)
      )
      .then(() => {
        this._configChanged = false;
      });
  }

  @observe('_repoConfig.*')
  _handleConfigChanged() {
    if (this._isLoading()) {
      return;
    }
    this._configChanged = true;
  }

  _computeButtonDisabled(readOnly: boolean, configChanged: boolean) {
    return readOnly || !configChanged;
  }

  _computeHeaderClass(configChanged: boolean) {
    return configChanged ? 'edited' : '';
  }

  _computeSchemes(schemesDefault: string[], schemesObj?: SchemesInfoMap) {
    return !schemesObj ? schemesDefault : Object.keys(schemesObj);
  }

  _schemesChanged(schemes: string[]) {
    if (schemes.length === 0) {
      return;
    }
    if (!this._selectedScheme || !schemes.includes(this._selectedScheme)) {
      this._selectedScheme = schemes.sort()[0];
    }
  }

  _computeCommands(
    repo?: RepoName,
    schemesObj?: SchemesInfoMap,
    _selectedScheme?: string
  ) {
    if (!schemesObj || !repo || !_selectedScheme) return [];
    if (!hasOwnProperty(schemesObj, _selectedScheme)) return [];
    const commandObj = schemesObj[_selectedScheme].clone_commands;
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

  _computeRepositoriesClass(config?: InheritedBooleanInfo) {
    return config ? 'showConfig' : '';
  }

  _computeChangesUrl(name: RepoName) {
    return GerritNav.getUrlForProjectChanges(name);
  }

  _computeBrowseUrl(weblinks: WebLinkInfo[]) {
    return weblinks?.[0]?.url;
  }

  _handlePluginConfigChanged({
    detail: {name, config, notifyPath},
  }: {
    detail: {
      name: string;
      config: PluginParameterToConfigParameterInfoMap;
      notifyPath: string;
    };
  }) {
    if (this._repoConfig?.plugin_config) {
      this._repoConfig.plugin_config[name] = config;
      this.notifyPath('_repoConfig.plugin_config.' + notifyPath);
    }
  }

  private handleSelectedSchemBindValueChanged(e: CustomEvent) {
    this._selectedScheme = e.detail.value;
  }

  private handleDescriptionBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.description = e.detail.value;
    this.requestUpdate();
  }

  private handleStateSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.state = e.detail.value;
    this.requestUpdate();
  }

  private handleSubmitTypeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.submit_type = e.detail.value;
    this.requestUpdate();
  }

  private handleContentMergeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.use_content_merge.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleNewChangeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.create_new_change_for_all_not_in_target.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleRequireChangeIdSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.require_change_id.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleEnableSignedPushBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.enable_signed_push.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleRequireSignedPushBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.require_signed_push.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleRejectImplicitMergeSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.reject_implicit_merges.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleUnRegisteredCcSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.enable_reviewer_by_email.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleSetAllNewChangesPrivateByDefaultSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.private_by_default.configured_value = e.detail.value;
    this.requestUpdate();
  }

  private handleSetAllNewChangesWorkInProgressByDefaultSelectBindValueChanged(e: BindValueChangeEvent) {
    if (!this._repoConfig) return;
    this._repoConfig.work_in_progress_by_default.configured_value = e.detail.value;
    this.requestUpdate();
    
    this.;
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
