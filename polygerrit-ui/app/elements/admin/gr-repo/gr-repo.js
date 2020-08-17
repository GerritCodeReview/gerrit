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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '@polymer/iron-input/iron-input.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-download-commands/gr-download-commands.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';
import '../gr-repo-plugin-config/gr-repo-plugin-config.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-repo_html.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const STATES = {
  active: {value: 'ACTIVE', label: 'Active'},
  readOnly: {value: 'READ_ONLY', label: 'Read Only'},
  hidden: {value: 'HIDDEN', label: 'Hidden'},
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

/**
 * @extends PolymerElement
 */
class GrRepo extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  // Notes for future TS conversion:
  // _repoConfig: ConfigInfo
  // _pluginData: PluginData[], can't be null, PluginData from gr-repo-plugin-config.ts

  static get template() { return htmlTemplate; }

  static get is() { return 'gr-repo'; }

  static get properties() {
    return {
      params: Object,
      repo: String,

      _configChanged: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _loggedIn: {
        type: Boolean,
        value: false,
        observer: '_loggedInChanged',
      },
      /** @type {?} */
      _repoConfig: Object,
      /** @type {?} */
      _pluginData: {
        type: Array,
        computed: '_computePluginData(_repoConfig.plugin_config.*)',
      },
      _readOnly: {
        type: Boolean,
        value: true,
      },
      _states: {
        type: Array,
        value() {
          return Object.values(STATES);
        },
      },
      _submitTypes: {
        type: Array,
        value() {
          return Object.values(SUBMIT_TYPES);
        },
      },
      _schemes: {
        type: Array,
        value() { return []; },
        computed: '_computeSchemes(_schemesObj)',
        observer: '_schemesChanged',
      },
      _selectedCommand: {
        type: String,
        value: 'Clone',
      },
      _selectedScheme: String,
      _schemesObj: Object,
    };
  }

  static get observers() {
    return [
      '_handleConfigChanged(_repoConfig.*)',
    ];
  }

  /** @override */
  attached() {
    super.attached();
    this._loadRepo();

    this.dispatchEvent(new CustomEvent('title-change', {
      detail: {title: this.repo},
      composed: true, bubbles: true,
    }));
  }

  _computePluginData(configRecord) {
    if (!configRecord ||
        !configRecord.base) { return []; }

    const pluginConfig = configRecord.base;
    return Object.keys(pluginConfig)
        .map(name => { return {name, config: pluginConfig[name]}; });
  }

  _loadRepo() {
    if (!this.repo) { return Promise.resolve(); }

    const promises = [];

    const errFn = response => {
      this.dispatchEvent(new CustomEvent('page-error', {
        detail: {response},
        composed: true, bubbles: true,
      }));
    };

    promises.push(this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
      if (loggedIn) {
        this.$.restAPI.getRepoAccess(this.repo).then(access => {
          if (!access) { return Promise.resolve(); }

          // If the user is not an owner, is_owner is not a property.
          this._readOnly = !access[this.repo].is_owner;
        });
      }
    }));

    promises.push(this.$.restAPI.getProjectConfig(this.repo, errFn)
        .then(config => {
          if (!config) { return Promise.resolve(); }

          if (config.default_submit_type) {
            // The gr-select is bound to submit_type, which needs to be the
            // *configured* submit type. When default_submit_type is
            // present, the server reports the *effective* submit type in
            // submit_type, so we need to overwrite it before storing the
            // config in this.
            config.submit_type =
                config.default_submit_type.configured_value;
          }
          if (!config.state) {
            config.state = STATES.active.value;
          }
          this._repoConfig = config;
          this._loading = false;
        }));

    promises.push(this.$.restAPI.getConfig().then(config => {
      if (!config) { return Promise.resolve(); }

      this._schemesObj = config.download.schemes;
    }));

    return Promise.all(promises);
  }

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  }

  _computeHideClass(arr) {
    return !arr || !arr.length ? 'hide' : '';
  }

  _loggedInChanged(_loggedIn) {
    if (!_loggedIn) { return; }
    this.$.restAPI.getPreferences().then(prefs => {
      if (prefs.download_scheme) {
        // Note (issue 5180): normalize the download scheme with lower-case.
        this._selectedScheme = prefs.download_scheme.toLowerCase();
      }
    });
  }

  _formatBooleanSelect(item) {
    if (!item) { return; }
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
      }, {
        label: 'False',
        value: 'FALSE',
      },
    ];
  }

  _formatSubmitTypeSelect(projectConfig) {
    if (!projectConfig) { return; }
    const allValues = Object.values(SUBMIT_TYPES);
    const type = projectConfig.default_submit_type;
    if (!type) {
      // Server is too old to report default_submit_type, so assume INHERIT
      // is not a valid value.
      return allValues;
    }

    let inheritLabel = 'Inherit';
    if (type.inherited_value) {
      let inherited = type.inherited_value;
      for (const val of allValues) {
        if (val.value === type.inherited_value) {
          inherited = val.label;
          break;
        }
      }
      inheritLabel = `Inherit (${inherited})`;
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
    return this.$.restAPI.getLoggedIn();
  }

  _formatRepoConfigForSave(repoConfig) {
    const configInputObj = {};
    for (const key in repoConfig) {
      if (repoConfig.hasOwnProperty(key)) {
        if (key === 'default_submit_type') {
          // default_submit_type is not in the input type, and the
          // configured value was already copied to submit_type by
          // _loadProject. Omit this property when saving.
          continue;
        }
        if (key === 'plugin_config') {
          configInputObj.plugin_config_values = repoConfig[key];
        } else if (typeof repoConfig[key] === 'object') {
          configInputObj[key] = repoConfig[key].configured_value;
        } else {
          configInputObj[key] = repoConfig[key];
        }
      }
    }
    return configInputObj;
  }

  _handleSaveRepoConfig() {
    return this.$.restAPI.saveRepoConfig(this.repo,
        this._formatRepoConfigForSave(this._repoConfig)).then(() => {
      this._configChanged = false;
    });
  }

  _handleConfigChanged() {
    if (this._isLoading()) { return; }
    this._configChanged = true;
  }

  _computeButtonDisabled(readOnly, configChanged) {
    return readOnly || !configChanged;
  }

  _computeHeaderClass(configChanged) {
    return configChanged ? 'edited' : '';
  }

  _computeSchemes(schemesObj) {
    return Object.keys(schemesObj);
  }

  _schemesChanged(schemes) {
    if (schemes.length === 0) { return; }
    if (!schemes.includes(this._selectedScheme)) {
      this._selectedScheme = schemes.sort()[0];
    }
  }

  _computeCommands(repo, schemesObj, _selectedScheme) {
    if (!schemesObj || !repo || !_selectedScheme) {
      return [];
    }
    const commands = [];
    let commandObj;
    if (schemesObj.hasOwnProperty(_selectedScheme)) {
      commandObj = schemesObj[_selectedScheme].clone_commands;
    }
    for (const title in commandObj) {
      if (!commandObj.hasOwnProperty(title)) { continue; }
      commands.push({
        title,
        command: commandObj[title]
            .replace(/\${project}/gi, encodeURI(repo))
            .replace(/\${project-base-name}/gi,
                encodeURI(repo.substring(repo.lastIndexOf('/') + 1))),
      });
    }
    return commands;
  }

  _computeRepositoriesClass(config) {
    return config ? 'showConfig': '';
  }

  _computeChangesUrl(name) {
    return GerritNav.getUrlForProjectChanges(name);
  }

  _handlePluginConfigChanged({detail: {name, config, notifyPath}}) {
    this._repoConfig.plugin_config[name] = config;
    this.notifyPath('_repoConfig.plugin_config.' + notifyPath);
  }
}

customElements.define(GrRepo.is, GrRepo);
