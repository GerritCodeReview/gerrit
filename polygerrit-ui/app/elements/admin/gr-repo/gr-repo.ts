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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, observe} from '@polymer/decorators';
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
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {hasOwnProperty} from '../../../utils/common-util';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {WebLinkInfo} from '../../../types/diff';
import {ErrorCallback} from '../../../api/rest';

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
export class GrRepo extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

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

  _formatBooleanSelect(item: InheritedBooleanInfo) {
    if (!item) {
      return;
    }
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

  _formatSubmitTypeSelect(projectConfig: ConfigInfo) {
    if (!projectConfig) {
      return;
    }
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

  _computeRepositoriesClass(config: InheritedBooleanInfo) {
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo': GrRepo;
  }
}
