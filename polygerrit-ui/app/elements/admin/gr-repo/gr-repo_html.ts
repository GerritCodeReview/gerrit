/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-subpage-styles">
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
  </style>
  <style include="gr-form-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <div class="main gr-form-styles read-only">
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="info">
      <h1 id="Title" class="heading-1">[[repo]]</h1>
      <hr />
      <div>
        <a href$="[[_computeBrowseUrl(weblinks)]]"
          ><gr-button link disabled="[[!_computeBrowseUrl(weblinks)]]"
            >Browse</gr-button
          ></a
        ><a href$="[[_computeChangesUrl(repo)]]"
          ><gr-button link>View Changes</gr-button></a
        >
      </div>
    </div>
    <div id="loading" class$="[[_computeLoadingClass(_loading)]]">
      Loading...
    </div>
    <div id="loadedContent" class$="[[_computeLoadingClass(_loading)]]">
      <div id="downloadContent" class$="[[_computeHideClass(_schemes)]]">
        <h2 id="download" class="heading-2">Download</h2>
        <fieldset>
          <gr-download-commands
            id="downloadCommands"
            commands="[[_computeCommands(repo, _schemesObj, _selectedScheme)]]"
            schemes="[[_schemes]]"
            selected-scheme="{{_selectedScheme}}"
          ></gr-download-commands>
        </fieldset>
      </div>
      <h2
        id="configurations"
        class$="heading-2 [[_computeHeaderClass(_configChanged)]]"
      >
        Configurations
      </h2>
      <div id="form">
        <fieldset>
          <h3 id="Description" class="heading-3">Description</h3>
          <fieldset>
            <gr-textarea
              id="descriptionInput"
              class="description"
              autocomplete="on"
              placeholder="<Insert repo description here>"
              rows="4"
              text="{{_repoConfig.description}}"
              disabled$="[[_readOnly]]"
            ></gr-textarea>
          </fieldset>
          <h3 id="Options" class="heading-3">Repository Options</h3>
          <fieldset id="options">
            <section>
              <span class="title">State</span>
              <span class="value">
                <gr-select id="stateSelect" bind-value="{{_repoConfig.state}}">
                  <select disabled$="[[_readOnly]]">
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
                <gr-select
                  id="submitTypeSelect"
                  bind-value="{{_repoConfig.submit_type}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatSubmitTypeSelect(_repoConfig)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <span class="title">Allow content merges</span>
              <span class="value">
                <gr-select
                  id="contentMergeSelect"
                  bind-value="{{_repoConfig.use_content_merge.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.use_content_merge)]]"
                    >
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
                <gr-select
                  id="newChangeSelect"
                  bind-value="{{_repoConfig.create_new_change_for_all_not_in_target.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.create_new_change_for_all_not_in_target)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <span class="title">Require Change-Id in commit message</span>
              <span class="value">
                <gr-select
                  id="requireChangeIdSelect"
                  bind-value="{{_repoConfig.require_change_id.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.require_change_id)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
                  </select>
                </gr-select>
              </span>
            </section>
            <section
              id="enableSignedPushSettings"
              class$="repositorySettings [[_computeRepositoriesClass(_repoConfig.enable_signed_push)]]"
            >
              <span class="title">Enable signed push</span>
              <span class="value">
                <gr-select
                  id="enableSignedPush"
                  bind-value="{{_repoConfig.enable_signed_push.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.enable_signed_push)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
                  </select>
                </gr-select>
              </span>
            </section>
            <section
              id="requireSignedPushSettings"
              class$="repositorySettings [[_computeRepositoriesClass(_repoConfig.require_signed_push)]]"
            >
              <span class="title">Require signed push</span>
              <span class="value">
                <gr-select
                  id="requireSignedPush"
                  bind-value="{{_repoConfig.require_signed_push.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.require_signed_push)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
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
                  bind-value="{{_repoConfig.reject_implicit_merges.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.reject_implicit_merges)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
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
                  bind-value="{{_repoConfig.enable_reviewer_by_email.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.enable_reviewer_by_email)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <span class="title"> Set all new changes private by default</span>
              <span class="value">
                <gr-select
                  id="setAllnewChangesPrivateByDefaultSelect"
                  bind-value="{{_repoConfig.private_by_default.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.private_by_default)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
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
                  bind-value="{{_repoConfig.work_in_progress_by_default.configured_value}}"
                >
                  <select disabled$="[[_readOnly]]">
                    <template
                      is="dom-repeat"
                      items="[[_formatBooleanSelect(_repoConfig.work_in_progress_by_default)]]"
                    >
                      <option value="[[item.value]]">[[item.label]]</option>
                    </template>
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
          <div
            class$="pluginConfig [[_computeHideClass(_pluginData)]]"
            on-plugin-config-changed="_handlePluginConfigChanged"
          >
            <h3 class="heading-3">Plugins</h3>
            <template is="dom-repeat" items="[[_pluginData]]" as="data">
              <gr-repo-plugin-config
                plugin-data="[[data]]"
                disabled$="[[_readOnly]]"
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
