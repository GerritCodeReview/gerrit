/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-textarea/gr-textarea';
import {
  AutocompleteSuggestion,
  AutocompleteQuery,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {convertToString} from '../../../utils/string-util';
import {BindValueChangeEvent} from '../../../types/events';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
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

  private readonly query: AutocompleteQuery;

  @property({type: String})
  groupId?: GroupId;

  @state() private originalOwnerName?: string;

  @state() private originalDescriptionName?: string;

  @state() private originalOptionsVisibleToAll?: boolean;

  @state() private submitTypes = Object.values(OPTIONS);

  // private but used in test
  @state() isAdmin = false;

  // private but used in test
  @state() groupOwner = false;

  // private but used in test
  @state() groupIsInternal = false;

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() groupConfig?: GroupInfo;

  // private but used in test
  @state() groupConfigOwner?: string;

  // private but used in test
  @state() originalName?: GroupName;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.query = (input: string) => this.getGroupSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
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
    return html`
      <div class="main gr-form-styles read-only">
        <div id="loading" class=${this.computeLoadingClass()}>Loading...</div>
        <div id="loadedContent" class=${this.computeLoadingClass()}>
          <h1 id="Title" class="heading-1">${this.originalName}</h1>
          <h2 id="configurations" class="heading-2">General</h2>
          <div id="form">
            <fieldset>
              ${this.renderGroupUUID()} ${this.renderGroupName()}
              ${this.renderGroupOwner()} ${this.renderGroupDescription()}
              ${this.renderGroupOptions()}
            </fieldset>
          </div>
        </div>
      </div>
    `;
  }

  private renderGroupUUID() {
    return html`
      <h3 id="groupUUID" class="heading-3">Group UUID</h3>
      <fieldset>
        <gr-copy-clipboard
          id="uuid"
          .text=${this.getGroupUUID()}
        ></gr-copy-clipboard>
      </fieldset>
    `;
  }

  private renderGroupName() {
    const groupNameEdited = this.originalName !== this.groupConfig?.name;
    return html`
      <h3
        id="groupName"
        class="heading-3 ${this.computeHeaderClass(groupNameEdited)}"
      >
        Group Name
      </h3>
      <fieldset>
        <span class="value">
          <gr-autocomplete
            id="groupNameInput"
            .text=${this.groupConfig?.name}
            ?disabled=${this.computeGroupDisabled()}
            @text-changed=${this.handleNameTextChanged}
          ></gr-autocomplete>
        </span>
        <span class="value">
          <gr-button
            id="inputUpdateNameBtn"
            ?disabled=${!groupNameEdited}
            @click=${this.handleSaveName}
          >
            Rename Group</gr-button
          >
        </span>
      </fieldset>
    `;
  }

  private renderGroupOwner() {
    const groupOwnerNameEdited =
      this.originalOwnerName !== this.groupConfig?.owner;
    return html`
      <h3
        id="groupOwner"
        class="heading-3 ${this.computeHeaderClass(groupOwnerNameEdited)}"
      >
        Owners
      </h3>
      <fieldset>
        <span class="value">
          <gr-autocomplete
            id="groupOwnerInput"
            .text=${this.groupConfig?.owner}
            .value=${this.groupConfigOwner}
            .query=${this.query}
            ?disabled=${this.computeGroupDisabled()}
            @text-changed=${this.handleOwnerTextChanged}
            @value-changed=${this.handleOwnerValueChanged}
          >
          </gr-autocomplete>
        </span>
        <span class="value">
          <gr-button
            id="inputUpdateOwnerBtn"
            ?disabled=${!groupOwnerNameEdited}
            @click=${this.handleSaveOwner}
          >
            Change Owners</gr-button
          >
        </span>
      </fieldset>
    `;
  }

  private renderGroupDescription() {
    const groupDescriptionEdited =
      this.originalDescriptionName !== this.groupConfig?.description;
    return html`
      <h3 class="heading-3 ${this.computeHeaderClass(groupDescriptionEdited)}">
        Description
      </h3>
      <fieldset>
        <div>
          <gr-textarea
            class="description"
            autocomplete="on"
            rows="4"
            monospace
            ?disabled=${this.computeGroupDisabled()}
            .text=${this.groupConfig?.description}
            @text-changed=${this.handleDescriptionTextChanged}
          ></gr-textarea>
        </div>
        <span class="value">
          <gr-button
            ?disabled=${!groupDescriptionEdited}
            @click=${this.handleSaveDescription}
          >
            Save Description
          </gr-button>
        </span>
      </fieldset>
    `;
  }

  private renderGroupOptions() {
    // We make sure the value is a boolean
    // this is done so undefined is converted to false.
    const groupOptionsEdited =
      Boolean(this.originalOptionsVisibleToAll) !==
      Boolean(this.groupConfig?.options?.visible_to_all);

    // We have to convert boolean to string in order
    // for the selection to work correctly.
    // We also convert undefined to false using boolean.
    return html`
      <h3
        id="options"
        class="heading-3 ${this.computeHeaderClass(groupOptionsEdited)}"
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
              .bindValue=${convertToString(
                Boolean(this.groupConfig?.options?.visible_to_all)
              )}
              @bind-value-changed=${this.handleOptionsBindValueChanged}
            >
              <select ?disabled=${this.computeGroupDisabled()}>
                ${this.submitTypes.map(
                  item => html`
                    <option value=${item.value}>${item.label}</option>
                  `
                )}
              </select>
            </gr-select>
          </span>
        </section>
        <span class="value">
          <gr-button
            ?disabled=${!groupOptionsEdited}
            @click=${this.handleSaveOptions}
          >
            Save Group Options
          </gr-button>
        </span>
      </fieldset>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('groupId')) {
      this.loadGroup();
    }
  }

  // private but used in test
  async loadGroup() {
    if (!this.groupId) return;

    const promises: Promise<unknown>[] = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const config = await this.restApiService.getGroupConfig(
      this.groupId,
      errFn
    );
    if (!config || !config.name) return;

    if (config.description === undefined) {
      config.description = '';
    }

    this.originalName = config.name;
    this.originalOwnerName = config.owner;
    this.originalDescriptionName = config.description;
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

    this.groupConfig = config;
    this.originalOptionsVisibleToAll = config?.options?.visible_to_all;

    fireTitleChange(this, config.name);

    await Promise.all(promises);
    this.loading = false;
  }

  // private but used in test
  computeLoadingClass() {
    return this.loading ? 'loading' : '';
  }

  // private but used in test
  async handleSaveName() {
    const groupConfig = this.groupConfig;
    if (!this.groupId || !groupConfig || !groupConfig.name) {
      return Promise.reject(new Error('invalid groupId or config name'));
    }
    const groupName = groupConfig.name;
    const config = await this.restApiService.saveGroupName(
      this.groupId,
      groupName
    );
    if (config.status === 200) {
      this.originalName = groupName;
      const detail: GroupNameChangedDetail = {
        name: groupName,
        external: !this.groupIsInternal,
      };
      this.dispatchEvent(
        new CustomEvent('name-changed', {
          detail,
          composed: true,
          bubbles: true,
        })
      );
      this.requestUpdate();
    }

    return;
  }

  // private but used in test
  async handleSaveOwner() {
    if (!this.groupId || !this.groupConfig) return;
    let owner = this.groupConfig.owner;
    if (this.groupConfigOwner) {
      owner = decodeURIComponent(this.groupConfigOwner);
    }
    if (!owner) return;
    await this.restApiService.saveGroupOwner(this.groupId, owner);
    this.originalOwnerName = this.groupConfig?.owner;
    this.groupConfigOwner = undefined;
  }

  // private but used in test
  async handleSaveDescription() {
    if (
      !this.groupId ||
      !this.groupConfig ||
      this.groupConfig.description === undefined
    )
      return;
    await this.restApiService.saveGroupDescription(
      this.groupId,
      this.groupConfig.description
    );
    this.originalDescriptionName = this.groupConfig.description;
  }

  // private but used in test
  async handleSaveOptions() {
    if (!this.groupId || !this.groupConfig || !this.groupConfig.options) return;
    const visible = this.groupConfig.options.visible_to_all;
    const options = {visible_to_all: visible};
    await this.restApiService.saveGroupOptions(this.groupId, options);
    this.originalOptionsVisibleToAll = visible;
  }

  private computeHeaderClass(configChanged: boolean) {
    return configChanged ? 'edited' : '';
  }

  private getGroupSuggestions(input: string) {
    return this.restApiService.getSuggestedGroups(input).then(response => {
      const groups: AutocompleteSuggestion[] = [];
      for (const [name, group] of Object.entries(response ?? {})) {
        groups.push({name, value: decodeURIComponent(group.id)});
      }
      return groups;
    });
  }

  // private but used in test
  computeGroupDisabled() {
    return !(this.groupIsInternal && (this.isAdmin || this.groupOwner));
  }

  private getGroupUUID() {
    const id = this.groupConfig?.id;
    if (!id) return;
    return id.match(INTERNAL_GROUP_REGEX) ? id : decodeURIComponent(id);
  }

  private handleNameTextChanged(e: CustomEvent) {
    if (!this.groupConfig || this.loading) return;
    this.groupConfig.name = e.detail.value as GroupName;
    this.requestUpdate();
  }

  private handleOwnerTextChanged(e: CustomEvent) {
    if (!this.groupConfig || this.loading) return;
    this.groupConfig.owner = e.detail.value;
    this.requestUpdate();
  }

  private handleOwnerValueChanged(e: CustomEvent) {
    if (this.loading) return;
    this.groupConfigOwner = e.detail.value;
    this.requestUpdate();
  }

  private handleDescriptionTextChanged(e: CustomEvent) {
    if (!this.groupConfig || this.loading) return;
    this.groupConfig.description = e.detail.value;
    this.requestUpdate();
  }

  private handleOptionsBindValueChanged(e: BindValueChangeEvent) {
    if (!this.groupConfig || this.loading) return;

    // Because the value for e.detail.value is a string
    // we convert the value to a boolean.
    const value = e.detail.value === 'true' ? true : false;
    this.groupConfig.options!.visible_to_all = value;
    this.requestUpdate();
  }
}
