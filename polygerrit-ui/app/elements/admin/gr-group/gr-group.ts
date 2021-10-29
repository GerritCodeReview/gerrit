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
import {LitElement, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';

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

  @state() protected rename = false;

  @state() protected description = false;

  @state() protected owner = false;

  @state() protected options = false;

  @state() protected groupOwnerName?: string;

  @state() protected groupDescriptionName?: string;

  @state() protected groupOptionsVisibleToAll?: boolean;

  @state() protected submitTypes = Object.values(OPTIONS);

  @state() protected query: AutocompleteQuery;

  @state() protected isAdmin = false;

  @state() groupOwner = false;

  @state() groupIsInternal = false;

  @state() loading = true;

  @state() groupConfig?: GroupInfo;

  @state() groupConfigOwner?: string;

  @state() groupName?: GroupName;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this.query = (input: string) => this._getGroupSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadGroup();
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
      this.groupOwner,
      this.isAdmin,
      this.groupIsInternal
    );
    return html`
      <div class="main gr-form-styles read-only">
        <div id="loading" class="${this._computeLoadingClass(this.loading)}">
          Loading...
        </div>
        <div
          id="loadedContent"
          class="${this._computeLoadingClass(this.loading)}"
        >
          <h1 id="Title" class="heading-1">
            ${convertToString(this.groupName)}
          </h1>
          <h2 id="configurations" class="heading-2">General</h2>
          <div id="form">
            <fieldset>
              <h3 id="groupUUID" class="heading-3">Group UUID</h3>
              <fieldset>
                <gr-copy-clipboard
                  id="uuid"
                  .text=${this.getGroupUUID(this.groupConfig?.id)}
                ></gr-copy-clipboard>
              </fieldset>
              <h3
                id="groupName"
                class="heading-3 ${this._computeHeaderClass(this.rename)}"
              >
                Group Name
              </h3>
              <fieldset>
                <span class="value">
                  <gr-autocomplete
                    id="groupNameInput"
                    .text=${convertToString(this.groupConfig?.name)}
                    ?disabled=${groupDisable}
                    @text-changed=${this.handleNameTextChanged}
                  ></gr-autocomplete>
                </span>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    id="inputUpdateNameBtn"
                    ?disabled=${!this.rename}
                    @click=${this.handleSaveName}
                  >
                    Rename Group</gr-button
                  >
                </span>
              </fieldset>
              <h3
                id="groupOwner"
                class="heading-3 ${this._computeHeaderClass(this.owner)}"
              >
                Owners
              </h3>
              <fieldset>
                <span class="value">
                  <gr-autocomplete
                    id="groupOwnerInput"
                    .text=${convertToString(this.groupConfig?.owner)}
                    .value=${convertToString(this.groupConfigOwner)}
                    .query=${this.query}
                    ?disabled=${groupDisable}
                    @text-changed=${this.handleOwnerTextChanged}
                    @value-changed=${this.handleOwnerValueChanged}
                  >
                  </gr-autocomplete>
                </span>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    id="inputUpdateOwnerBtn"
                    ?disabled=${!this.owner}
                    @click=${this._handleSaveOwner}
                  >
                    Change Owners</gr-button
                  >
                </span>
              </fieldset>
              <h3
                class="heading-3 ${this._computeHeaderClass(this.description)}"
              >
                Description
              </h3>
              <fieldset>
                <div>
                  <iron-autogrow-textarea
                    class="description"
                    autocomplete="on"
                    ?disabled=${groupDisable}
                    .bindValue=${convertToString(this.groupConfig?.description)}
                    @bind-value-changed=${this
                      .handleDescriptionBindValueChanged}
                  ></iron-autogrow-textarea>
                </div>
                <span class="value" disabled=${groupDisable}>
                  <gr-button
                    ?disabled=${!this.description}
                    @click=${this._handleSaveDescription}
                  >
                    Save Description
                  </gr-button>
                </span>
              </fieldset>
              <h3
                id="options"
                class="heading-3 ${this._computeHeaderClass(this.options)}"
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
                      .bindValue="${this.groupConfig?.options?.visible_to_all}"
                      @bind-value-changed=${this.handleOptionsBindValueChanged}
                    >
                      <select ?disabled=${groupDisable}>
                        ${this.submitTypes.map(
                          item => html`
                            <option value=${item.value}>${item.label}</option>
                          `
                        )}
                      </select>
                    </gr-select>
                  </span>
                </section>
                <span class="value" ?disabled=${groupDisable}>
                  <gr-button
                    ?disabled=${!this.options}
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

  async loadGroup() {
    if (!this.groupId) {
      return;
    }

    const promises: Promise<unknown>[] = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return await this.restApiService
      .getGroupConfig(this.groupId, errFn)
      .then(config => {
        if (!config || !config.name) {
          return Promise.resolve();
        }

        this.groupName = config.name;
        this.groupOwnerName = config.owner;
        this.groupDescriptionName = config.description;
        this.groupIsInternal = !!config.id.match(INTERNAL_GROUP_REGEX);

        promises.push(
          this.restApiService.getIsAdmin().then(isAdmin => {
            this.isAdmin = !!isAdmin;
          })
        );

        promises.push(
          this.restApiService.getIsGroupOwner(config.name).then(isOwner => {
            this.groupOwner = !!isOwner;
          })
        );

        // If visible to all is undefined, set to false. If it is defined
        // as false, setting to false is fine. If any optional values
        // are added with a default of true, then this would need to be an
        // undefined check and not a truthy/falsy check.
        if (config.options && !config.options.visible_to_all) {
          config.options.visible_to_all = false;
        }
        this.groupConfig = config;
        this.groupOptionsVisibleToAll = config?.options?.visible_to_all;

        fireTitleChange(this, config.name);

        return Promise.all(promises).then(() => {
          this.loading = false;
        });
      });
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _isLoading() {
    return this.loading || this.loading === undefined;
  }

  handleSaveName() {
    const groupConfig = this.groupConfig;
    if (!this.groupId || !groupConfig || !groupConfig.name) {
      return Promise.reject(new Error('invalid groupId or config name'));
    }
    const groupName = groupConfig.name;
    return this.restApiService
      .saveGroupName(this.groupId, groupName)
      .then(config => {
        if (config.status === 200) {
          this.groupName = groupName;
          this.groupOwnerName = groupConfig.owner;
          this.groupDescriptionName = groupConfig.description;
          this.groupOptionsVisibleToAll = groupConfig?.options?.visible_to_all;
          const detail: GroupNameChangedDetail = {
            name: groupName,
            external: !this.groupIsInternal,
          };
          fireEvent(this, 'name-changed');
          this.dispatchEvent(
            new CustomEvent('name-changed', {
              detail,
              composed: true,
              bubbles: true,
            })
          );
          this.rename = false;
        }
      });
  }

  _handleSaveOwner() {
    if (!this.groupId || !this.groupConfig) return;
    let owner = this.groupConfig.owner;
    if (this.groupConfigOwner) {
      owner = decodeURIComponent(this.groupConfigOwner);
    }
    if (!owner) return;
    return this.restApiService.saveGroupOwner(this.groupId, owner).then(() => {
      this.owner = false;
    });
  }

  _handleSaveDescription() {
    if (
      !this.groupId ||
      !this.groupConfig ||
      this.groupConfig.description === undefined
    )
      return;
    return this.restApiService
      .saveGroupDescription(this.groupId, this.groupConfig.description)
      .then(() => {
        this.description = false;
      });
  }

  _handleSaveOptions() {
    if (!this.groupId || !this.groupConfig || !this.groupConfig.options) return;
    const visible = this.groupConfig.options.visible_to_all;
    const options = {visible_to_all: visible};

    return this.restApiService
      .saveGroupOptions(this.groupId, options)
      .then(() => {
        this.options = false;
      });
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
    if (!this.groupConfig || this._isLoading()) return;

    this.rename = this.groupName !== e.detail.value;
    this.groupConfig.name = e.detail.value as GroupName;
  }

  private handleOwnerTextChanged(e: CustomEvent) {
    if (!this.groupConfig || this._isLoading()) return;

    this.owner = this.groupOwnerName !== e.detail.value;
    this.groupConfig.owner = e.detail.value;
  }

  private handleOwnerValueChanged(e: CustomEvent) {
    if (this._isLoading()) return;

    this.owner = this.groupOwnerName !== e.detail.value;
    this.groupConfigOwner = e.detail.value;
  }

  private handleDescriptionBindValueChanged(e: BindValueChangeEvent) {
    if (!this.groupConfig || this._isLoading()) return;

    this.description = this.groupDescriptionName !== e.detail.value;
    this.groupConfig.description = e.detail.value;
  }

  private handleOptionsBindValueChanged(e: BindValueChangeEvent) {
    if (!this.groupConfig || !this.groupConfig.options || this._isLoading())
      return;

    const value = e.detail.value as unknown as boolean;
    this.options = this.groupOptionsVisibleToAll !== value;
    this.groupConfig.options.visible_to_all = value;
  }
}
