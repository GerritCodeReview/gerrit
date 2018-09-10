/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../shared/gr-download-commands/gr-download-commands.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';

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

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-subpage-styles">
      h2.edited:after {
        color: var(--deemphasized-text-color);
        content: ' *';
      }
      .loading,
      .hideDownload {
        display: none;
      }
      #loading.loading {
        display: block;
      }
      #loading:not(.loading) {
        display: none;
      }
      .repositorySettings {
        display: none;
      }
      .repositorySettings.showConfig {
        display: block;
      }
    </style>
    <style include="gr-form-styles"></style>
    <main class="gr-form-styles read-only">
      <h1 id="Title">[[repo]]</h1>
      <div id="loading" class\$="[[_computeLoadingClass(_loading)]]">Loading...</div>
      <div id="loadedContent" class\$="[[_computeLoadingClass(_loading)]]">
        <div id="downloadContent" class\$="[[_computeDownloadClass(_schemes)]]">
          <h2 id="download">Download</h2>
          <fieldset>
            <gr-download-commands id="downloadCommands" commands="[[_computeCommands(repo, _schemesObj, _selectedScheme)]]" schemes="[[_schemes]]" selected-scheme="{{_selectedScheme}}"></gr-download-commands>
          </fieldset>
        </div>
        <h2 id="configurations" class\$="[[_computeHeaderClass(_configChanged)]]">Configurations</h2>
        <div id="form">
          <fieldset>
            <h3 id="Description">Description</h3>
            <fieldset>
              <iron-autogrow-textarea id="descriptionInput" class="description" autocomplete="on" placeholder="<Insert repo description here>" bind-value="{{_repoConfig.description}}" disabled\$="[[_readOnly]]"></iron-autogrow-textarea>
            </fieldset>
            <h3 id="Options">Repository Options</h3>
            <fieldset id="options">
              <section>
                <span class="title">State</span>
                <span class="value">
                  <gr-select id="stateSelect" bind-value="{{_repoConfig.state}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_states]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">Submit type</span>
                <span class="value">
                  <gr-select id="submitTypeSelect" bind-value="{{_repoConfig.submit_type}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatSubmitTypeSelect(_repoConfig)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">Allow content merges</span>
                <span class="value">
                  <gr-select id="contentMergeSelect" bind-value="{{_repoConfig.use_content_merge.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.use_content_merge)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">
                  Create a new change for every commit not in the target branch
                </span>
                <span class="value">
                  <gr-select id="newChangeSelect" bind-value="{{_repoConfig.create_new_change_for_all_not_in_target.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.create_new_change_for_all_not_in_target)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">Require Change-Id in commit message</span>
                <span class="value">
                  <gr-select id="requireChangeIdSelect" bind-value="{{_repoConfig.require_change_id.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.require_change_id)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section id="enableSignedPushSettings" class\$="repositorySettings [[_computeRepositoriesClass(_repoConfig.enable_signed_push)]]">
                <span class="title">Enable signed push</span>
                <span class="value">
                  <gr-select id="enableSignedPush" bind-value="{{_repoConfig.enable_signed_push.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.enable_signed_push)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section id="requireSignedPushSettings" class\$="repositorySettings [[_computeRepositoriesClass(_repoConfig.require_signed_push)]]">
                <span class="title">Require signed push</span>
                <span class="value">
                  <gr-select id="requireSignedPush" bind-value="{{_repoConfig.require_signed_push.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.require_signed_push)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">
                  Reject implicit merges when changes are pushed for review</span>
                <span class="value">
                  <gr-select id="rejectImplicitMergesSelect" bind-value="{{_repoConfig.reject_implicit_merges.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.reject_implicit_merges)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section id="noteDbSettings" class\$="repositorySettings [[_computeRepositoriesClass(_noteDbEnabled)]]">
                <span class="title">
                  Enable adding unregistered users as reviewers and CCs on changes</span>
                <span class="value">
                  <gr-select id="unRegisteredCcSelect" bind-value="{{_repoConfig.enable_reviewer_by_email.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.enable_reviewer_by_email)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                  </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">
                  Set all new changes private by default</span>
                <span class="value">
                  <gr-select id="setAllnewChangesPrivateByDefaultSelect" bind-value="{{_repoConfig.private_by_default.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.private_by_default)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">
                  Set new changes to "work in progress" by default</span>
                <span class="value">
                  <gr-select id="setAllNewChangesWorkInProgressByDefaultSelect" bind-value="{{_repoConfig.work_in_progress_by_default.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.work_in_progress_by_default)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">Maximum Git object size limit</span>
                <span class="value">
                  <input id="maxGitObjSizeInput" bind-value="{{_repoConfig.max_object_size_limit.configured_value}}" is="iron-input" type="text" disabled\$="[[_readOnly]]">
                </span>
              </section>
              <section>
                <span class="title">Match authored date with committer date upon submit</span>
                <span class="value">
                  <gr-select id="matchAuthoredDateWithCommitterDateSelect" bind-value="{{_repoConfig.match_author_to_committer_date.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.match_author_to_committer_date)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                  </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">Reject empty commit upon submit</span>
                <span class="value">
                  <gr-select id="rejectEmptyCommitSelect" bind-value="{{_repoConfig.reject_empty_commit.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.reject_empty_commit)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                  </select>
                  </gr-select>
                </span>
              </section>
            </fieldset>
            <h3 id="Options">Contributor Agreements</h3>
            <fieldset id="agreements">
              <section>
                <span class="title">
                  Require a valid contributor agreement to upload</span>
                <span class="value">
                  <gr-select id="contributorAgreementSelect" bind-value="{{_repoConfig.use_contributor_agreements.configured_value}}">
                  <select disabled\$="[[_readOnly]]">
                    <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.use_contributor_agreements)]]">
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <section>
                <span class="title">Require Signed-off-by in commit message</span>
                <span class="value">
                  <gr-select id="useSignedOffBySelect" bind-value="{{_repoConfig.use_signed_off_by.configured_value}}">
                    <select disabled\$="[[_readOnly]]">
                      <template is="dom-repeat" items="[[_formatBooleanSelect(_repoConfig.use_signed_off_by)]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
            </fieldset>
            <!-- TODO @beckysiegel add plugin config widgets -->
            <gr-button on-tap="_handleSaveRepoConfig" disabled\$="[[_computeButtonDisabled(_readOnly, _configChanged)]]">Save changes</gr-button>
          </fieldset>
          <gr-endpoint-decorator name="repo-config">
            <gr-endpoint-param name="repoName" value="[[repo]]"></gr-endpoint-param>
          </gr-endpoint-decorator>
        </div>
      </div>
    </main>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-repo',

  properties: {
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
    _noteDbEnabled: {
      type: Boolean,
      value: false,
    },
  },

  observers: [
    '_handleConfigChanged(_repoConfig.*)',
  ],

  attached() {
    this._loadRepo();

    this.fire('title-change', {title: this.repo});
  },

  _loadRepo() {
    if (!this.repo) { return Promise.resolve(); }

    const promises = [];

    const errFn = response => {
      this.fire('page-error', {response});
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
      this._noteDbEnabled = !!config.note_db_enabled;
    }));

    return Promise.all(promises);
  },

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  },

  _computeDownloadClass(schemes) {
    return !schemes || !schemes.length ? 'hideDownload' : '';
  },

  _loggedInChanged(_loggedIn) {
    if (!_loggedIn) { return; }
    this.$.restAPI.getPreferences().then(prefs => {
      if (prefs.download_scheme) {
        // Note (issue 5180): normalize the download scheme with lower-case.
        this._selectedScheme = prefs.download_scheme.toLowerCase();
      }
    });
  },

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
  },

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
  },

  _isLoading() {
    return this._loading || this._loading === undefined;
  },

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  },

  _formatRepoConfigForSave(p) {
    const configInputObj = {};
    for (const key in p) {
      if (p.hasOwnProperty(key)) {
        if (key === 'default_submit_type') {
          // default_submit_type is not in the input type, and the
          // configured value was already copied to submit_type by
          // _loadProject. Omit this property when saving.
          continue;
        }
        if (typeof p[key] === 'object') {
          configInputObj[key] = p[key].configured_value;
        } else {
          configInputObj[key] = p[key];
        }
      }
    }
    return configInputObj;
  },

  _handleSaveRepoConfig() {
    return this.$.restAPI.saveRepoConfig(this.repo,
        this._formatRepoConfigForSave(this._repoConfig)).then(() => {
          this._configChanged = false;
        });
  },

  _handleConfigChanged() {
    if (this._isLoading()) { return; }
    this._configChanged = true;
  },

  _computeButtonDisabled(readOnly, configChanged) {
    return readOnly || !configChanged;
  },

  _computeHeaderClass(configChanged) {
    return configChanged ? 'edited' : '';
  },

  _computeSchemes(schemesObj) {
    return Object.keys(schemesObj);
  },

  _schemesChanged(schemes) {
    if (schemes.length === 0) { return; }
    if (!schemes.includes(this._selectedScheme)) {
      this._selectedScheme = schemes.sort()[0];
    }
  },

  _computeCommands(repo, schemesObj, _selectedScheme) {
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
            .replace('${project}', repo)
            .replace('${project-base-name}',
            repo.substring(repo.lastIndexOf('/') + 1)),
      });
    }
    return commands;
  },

  _computeRepositoriesClass(config) {
    return config ? 'showConfig': '';
  }
});
