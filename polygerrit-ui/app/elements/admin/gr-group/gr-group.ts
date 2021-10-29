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
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-select/gr-select';
import {
  AutocompleteSuggestion,
  AutocompleteQuery,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {
  fireEvent,
  firePageError,
  fireTitleChange,
} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {convertToString} from '../../../utils/string-util';
import {BindValueChangeEvent} from '../../../types/events';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

const OPTIONS = {
  submitFalse: {
    value: false,
    label: 'False',
  },
  submitTrue: {
    value: true,
    label: 'True',
  },
};

export interface GroupNameChangedDetail {
  name: GroupName;
  external: boolean;
}

declare global {
  interface HTMLElementEventMap {
    'text-changed': CustomEvent;
    'value-changed': CustomEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-group': GrGroup;
  }
}

@customElement('gr-group')
export class GrGroup extends LitElement {
  /**
   * Fired when the group name changes.
   *
   * @event name-changed
   */

  @property({type: String})
  groupId?: GroupId;

  @property({type: Boolean})
  _rename = false;

  @property({type: Boolean})
  _groupIsInternal = false;

  @property({type: Boolean})
  _description = false;

  @property({type: Boolean})
  _owner = false;

  @property({type: Boolean})
  _options = false;

  @property({type: Boolean})
  _loading = true;

  @property({type: Object})
  _groupConfig?: GroupInfo;

  @property({type: String})
  _groupConfigOwner?: string;

  @property({type: Object})
  _groupName?: GroupName;

  @property({type: Boolean})
  _groupOwner = false;

  @property({type: Array})
  _submitTypes = Object.values(OPTIONS);

  @property({type: Object})
  _query: AutocompleteQuery;

  @property({type: Boolean})
  _isAdmin = false;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = (input: string) => this._getGroupSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    this._loadGroup();
  }

  static override get styles() {
    return [
      fontStyles,
      formStyles,
      sharedStyles,
      subpageStyles,
      css`
        h3.edited:after {
          color: var(--deemphasized-text-color);
          content: ' *';
        }
      `,
    ];
  }

  override render() {
    const groupDisable = this._computeGroupDisabled(
      this._groupOwner,
      this._isAdmin,
      this._groupIsInternal
    );
    return html`
      <div class="main gr-form-styles read-only">
        <div id="loading" class="${this._computeLoadingClass(this._loading)}">
          Loading...
        </div>
        <div
          id="loadedContent"
          class="${this._computeLoadingClass(this._loading)}"
        >
          <h1 id="Title" class="heading-1">
            ${convertToString(this._groupName)}
          </h1>
          <h2 id="configurations" class="heading-2">General</h2>
          <div id="form">
            <fieldset>
              <h3 id="groupUUID" class="heading-3">Group UUID</h3>
              <fieldset>
                <gr-copy-clipboard
                  id="uuid"
                  .text="${this.getGroupUUID(this._groupConfig?.id)}"
                ></gr-copy-clipboard>
              </fieldset>
              <h3
                id="groupName"
                class="heading-3 ${this._computeHeaderClass(this._rename)}"
              >
                Group Name
              </h3>
              <fieldset>
                <span class="value">
                  <gr-autocomplete
                    id="groupNameInput"
                    .text=${convertToString(this._groupConfig?.name)}
                    .disabled=${groupDisable}
                    @text-changed=${this.handleNameTextChanged}
                  ></gr-autocomplete>
                </span>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    id="inputUpdateNameBtn"
                    disabled=${!this._rename}
                    @click=${this._handleSaveName}
                  >
                    Rename Group</gr-button
                  >
                </span>
              </fieldset>
              <h3
                id="groupOwner"
                class="heading-3 ${this._computeHeaderClass(this._owner)}"
              >
                Owners
              </h3>
              <fieldset>
                <span class="value">
                  <gr-autocomplete
                    id="groupOwnerInput"
                    .text=${convertToString(this._groupConfig?.owner)}
                    .value=${convertToString(this._groupConfigOwner)}
                    .query=${this._query}
                    .disabled=${groupDisable}
                    @text-changed=${this.handleOwnerTextChanged}
                    @value-changed=${this.handleOwnerValueChanged}
                  >
                  </gr-autocomplete>
                </span>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    id="inputUpdateOwnerBtn"
                    disabled=${!this._owner}
                    @click=${this._handleSaveOwner}
                  >
                    Change Owners</gr-button
                  >
                </span>
              </fieldset>
              <h3
                class="heading-3 ${this._computeHeaderClass(this._description)}"
              >
                Description
              </h3>
              <fieldset>
                <div>
                  <iron-autogrow-textarea
                    class="description"
                    autocomplete="on"
                    disabled=${groupDisable}
                    .bind-value=${convertToString(
                      this._groupConfig?.description
                    )}
                    @bind-value-changed=${this
                      .handleDescriptionBindValueChanged}
                  ></iron-autogrow-textarea>
                </div>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    disabled=${!this._description}
                    @click=${this._handleSaveDescription}
                  >
                    Save Description
                  </gr-button>
                </span>
              </fieldset>
              <h3
                id="options"
                class="heading-3 ${this._computeHeaderClass(this._options)}"
              >
                Group Options
              </h3>
              <fieldset>
                <section>
                  <span class="title">
                    Make group visible to all registered users
                  </span>
                  <span class="value">
                    <gr-select
                      id="visibleToAll"
                      .bind-value=${this._groupConfig?.options?.visible_to_all}
                      @bind-value-changed=${this.handleOptionsBindValueChanged}
                    >
                      <select disabled=${groupDisable}>
                        ${this._submitTypes.map(
                          item => html`
                            <option value=${convertToString(item.value)}>
                              ${item.label}
                            </option>
                          `
                        )}
                      </select>
                    </gr-select>
                  </span>
                </section>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    disabled=${!this._options}
                    @click=${this._handleSaveOptions}
                  >
                    Save Group Options
                  </gr-button>
                </span>
              </fieldset>
            </fieldset>
          </div>
        </div>
      </div>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('_groupConfig.name')) {
      this._handleConfigName();
    }

    if (
      changedProperties.has('_groupConfig.owner') ||
      changedProperties.has('_groupConfigOwner')
    ) {
      this._handleConfigOwner();
    }

    if (changedProperties.has('_groupConfig.description')) {
      this._handleConfigDescription();
    }

    if (changedProperties.has('_groupConfig.options.visible_to_all')) {
      this._handleConfigOptions();
    }
  }

  _loadGroup() {
    if (!this.groupId) {
      return;
    }

    const promises: Promise<unknown>[] = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getGroupConfig(this.groupId, errFn)
      .then(config => {
        if (!config || !config.name) {
          return Promise.resolve();
        }

        this._groupName = config.name;
        this._groupIsInternal = !!config.id.match(INTERNAL_GROUP_REGEX);

        promises.push(
          this.restApiService.getIsAdmin().then(isAdmin => {
            this._isAdmin = !!isAdmin;
          })
        );

        promises.push(
          this.restApiService.getIsGroupOwner(config.name).then(isOwner => {
            this._groupOwner = !!isOwner;
          })
        );

        // If visible to all is undefined, set to false. If it is defined
        // as false, setting to false is fine. If any optional values
        // are added with a default of true, then this would need to be an
        // undefined check and not a truthy/falsy check.
        if (config.options && !config.options.visible_to_all) {
          config.options.visible_to_all = false;
        }
        this._groupConfig = config;

        fireTitleChange(this, config.name);

        return Promise.all(promises).then(() => {
          this._loading = false;
        });
      });
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _isLoading() {
    return this._loading || this._loading === undefined;
  }

  _handleSaveName() {
    const groupConfig = this._groupConfig;
    if (!this.groupId || !groupConfig || !groupConfig.name) {
      return Promise.reject(new Error('invalid groupId or config name'));
    }
    const groupName = groupConfig.name;
    return this.restApiService
      .saveGroupName(this.groupId, groupName)
      .then(config => {
        if (config.status === 200) {
          this._groupName = groupName;
          const detail: GroupNameChangedDetail = {
            name: groupName,
            external: !this._groupIsInternal,
          };
          fireEvent(this, 'name-changed');
          this.dispatchEvent(
            new CustomEvent('name-changed', {
              detail,
              composed: true,
              bubbles: true,
            })
          );
          this._rename = false;
        }
      });
  }

  _handleSaveOwner() {
    if (!this.groupId || !this._groupConfig) return;
    let owner = this._groupConfig.owner;
    if (this._groupConfigOwner) {
      owner = decodeURIComponent(this._groupConfigOwner);
    }
    if (!owner) return;
    return this.restApiService.saveGroupOwner(this.groupId, owner).then(() => {
      this._owner = false;
    });
  }

  _handleSaveDescription() {
    if (!this.groupId || !this._groupConfig || !this._groupConfig.description)
      return;
    return this.restApiService
      .saveGroupDescription(this.groupId, this._groupConfig.description)
      .then(() => {
        this._description = false;
      });
  }

  _handleSaveOptions() {
    if (!this.groupId || !this._groupConfig || !this._groupConfig.options)
      return;
    const visible = this._groupConfig.options.visible_to_all;

    const options = {visible_to_all: visible};

    return this.restApiService
      .saveGroupOptions(this.groupId, options)
      .then(() => {
        this._options = false;
      });
  }

  _handleConfigName() {
    if (this._isLoading()) {
      return;
    }
    this._rename = true;
  }

  _handleConfigOwner() {
    if (this._isLoading()) {
      return;
    }
    this._owner = true;
  }

  _handleConfigDescription() {
    if (this._isLoading()) {
      return;
    }
    this._description = true;
  }

  _handleConfigOptions() {
    if (this._isLoading()) {
      return;
    }
    this._options = true;
  }

  _computeHeaderClass(configChanged: boolean) {
    return configChanged ? 'edited' : '';
  }

  _getGroupSuggestions(input: string) {
    return this.restApiService.getSuggestedGroups(input).then(response => {
      const groups: AutocompleteSuggestion[] = [];
      for (const [name, group] of Object.entries(response ?? {})) {
        groups.push({name, value: decodeURIComponent(group.id)});
      }
      return groups;
    });
  }

  _computeGroupDisabled(
    owner: boolean,
    admin: boolean,
    groupIsInternal: boolean
  ) {
    return !(groupIsInternal && (admin || owner));
  }

  private getGroupUUID(id?: GroupId) {
    if (!id) return;

    return id.match(INTERNAL_GROUP_REGEX) ? id : decodeURIComponent(id);
  }

  private handleNameTextChanged(e: CustomEvent) {
    if (!this._groupConfig) return;
    this._groupConfig.name = e.detail.value as GroupName;
    this.requestUpdate('_groupConfig');
  }

  private handleOwnerTextChanged(e: CustomEvent) {
    if (!this._groupConfig) return;
    this._groupConfig.owner = e.detail.value;
    this.requestUpdate('_groupConfig');
  }

  private handleOwnerValueChanged(e: CustomEvent) {
    this._groupConfigOwner = e.detail.value;
    this.requestUpdate('_groupConfigOwner');
  }

  private handleDescriptionBindValueChanged(e: BindValueChangeEvent) {
    if (!this._groupConfig) return;
    this._groupConfig.description = e.detail.value;
    this.requestUpdate('_groupConfig');
  }

  private handleOptionsBindValueChanged(e: BindValueChangeEvent) {
    if (!this._groupConfig || !this._groupConfig.options) return;
    this._groupConfig.options.visible_to_all = e.detail.value as unknown as boolean;
    this.requestUpdate('_groupConfig');
  }
}
