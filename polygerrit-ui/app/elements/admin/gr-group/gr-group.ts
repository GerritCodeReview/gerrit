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
import '../../../styles/gr-font-styles';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-select/gr-select';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrCopyClipboard} from '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-group_html';
import {customElement, property, observe} from '@polymer/decorators';
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

export interface GrGroup {
  $: {
    loading: HTMLDivElement;
    loadedContent: HTMLDivElement;
    visibleToAll: GrSelect;
    inputUpdateNameBtn: GrButton;
    Title: HTMLHeadingElement;
    groupNameInput: GrAutocomplete;
    groupName: HTMLHeadingElement;
    groupOwnerInput: GrAutocomplete;
    groupOwner: HTMLHeadingElement;
    inputUpdateOwnerBtn: GrButton;
    uuid: GrCopyClipboard;
  };
}

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
export class GrGroup extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

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

  @observe('_groupConfig.name')
  _handleConfigName() {
    if (this._isLoading()) {
      return;
    }
    this._rename = true;
  }

  @observe('_groupConfig.owner', '_groupConfigOwner')
  _handleConfigOwner() {
    if (this._isLoading()) {
      return;
    }
    this._owner = true;
  }

  @observe('_groupConfig.description')
  _handleConfigDescription() {
    if (this._isLoading()) {
      return;
    }
    this._description = true;
  }

  @observe('_groupConfig.options.visible_to_all')
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

  _getGroupUUID(id: GroupId) {
    if (!id) return;

    return id.match(INTERNAL_GROUP_REGEX) ? id : decodeURIComponent(id);
  }

  handleNameTextChanged(e: CustomEvent) {
    this.set('_groupConfig.name', e.detail.value as GroupName);
  }

  handleOwnerTextChanged(e: CustomEvent) {
    this.set('_groupConfig.owner', e.detail.value);
  }

  handleOwnerValueChanged(e: CustomEvent) {
    this._groupConfigOwner = e.detail.value;
  }

  handleDescriptionBindValueChanged(e: BindValueChangeEvent) {
    this.set('_groupConfig.description', e.detail.value);
  }

  convertToString(value?: unknown) {
    return convertToString(value);
  }
}
