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
import '../../../styles/gr-form-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../shared/gr-select/gr-select';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-group_html';
import {customElement, property, observe} from '@polymer/decorators';
import {
  AutocompleteSuggestion,
  AutocompleteQuery,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {
  ErrorCallback,
  RestApiService,
} from '../../../services/services/gr-rest-api/gr-rest-api';
import {hasOwnProperty} from '../../../utils/common-util';

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
    restAPI: RestApiService & Element;
    loading: HTMLDivElement;
  };
}

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
export class GrGroup extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
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
  _groupName?: string;

  @property({type: Boolean})
  _groupOwner = false;

  @property({type: Array})
  _submitTypes = Object.values(OPTIONS);

  @property({type: Object})
  _query: AutocompleteQuery;

  @property({type: Boolean})
  _isAdmin = false;

  constructor() {
    super();
    this._query = (input: string) => this._getGroupSuggestions(input);
  }

  /** @override */
  attached() {
    super.attached();
    this._loadGroup();
  }

  _loadGroup() {
    if (!this.groupId) {
      return;
    }

    const promises: Promise<unknown>[] = [];

    const errFn: ErrorCallback = response => {
      this.dispatchEvent(
        new CustomEvent('page-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    };

    return this.$.restAPI.getGroupConfig(this.groupId, errFn).then(config => {
      if (!config || !config.name) {
        return Promise.resolve();
      }

      this._groupName = config.name;
      this._groupIsInternal = !!config.id.match(INTERNAL_GROUP_REGEX);

      promises.push(
        this.$.restAPI.getIsAdmin().then(isAdmin => {
          this._isAdmin = !!isAdmin;
        })
      );

      promises.push(
        this.$.restAPI.getIsGroupOwner(config.name).then(isOwner => {
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

      this.dispatchEvent(
        new CustomEvent('title-change', {
          detail: {title: config.name},
          composed: true,
          bubbles: true,
        })
      );

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
    return this.$.restAPI
      .saveGroupName(this.groupId, groupName)
      .then(config => {
        if (config.status === 200) {
          this._groupName = groupName;
          const detail: GroupNameChangedDetail = {
            name: groupName,
            external: !this._groupIsInternal,
          };
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
    return this.$.restAPI.saveGroupOwner(this.groupId, owner).then(() => {
      this._owner = false;
    });
  }

  _handleSaveDescription() {
    if (!this.groupId || !this._groupConfig || !this._groupConfig.description)
      return;
    return this.$.restAPI
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

    return this.$.restAPI.saveGroupOptions(this.groupId, options).then(() => {
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
    return this.$.restAPI.getSuggestedGroups(input).then(response => {
      const groups: AutocompleteSuggestion[] = [];
      for (const key in response) {
        if (!hasOwnProperty(response, key)) {
          continue;
        }
        groups.push({
          name: key,
          value: decodeURIComponent(response[key].id),
        });
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
}
