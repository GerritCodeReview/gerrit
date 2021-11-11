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
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-change-dialog_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, observe} from '@polymer/decorators';
import {
  RepoName,
  BranchName,
  ChangeId,
  ConfigInfo,
  InheritedBooleanInfo,
} from '../../../types/common';
import {InheritedBooleanInfoConfiguredValue} from '../../../constants/constants';
import {GrTypedAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {appContext} from '../../../services/app-context';
import {Subject} from 'rxjs';
import {
  repoConfig$,
  serverConfig$,
} from '../../../services/config/config-model';
import {takeUntil} from 'rxjs/operators';
import {IronInputElement} from '@polymer/iron-input/iron-input';

const SUGGESTIONS_LIMIT = 15;
const REF_PREFIX = 'refs/heads/';

export interface GrCreateChangeDialog {
  $: {
    privateChangeCheckBox: HTMLInputElement;
    branchInput: GrTypedAutocomplete<BranchName>;
    tagNameInput: IronInputElement;
    messageInput: IronAutogrowTextareaElement;
  };
}
@customElement('gr-create-change-dialog')
export class GrCreateChangeDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  repoName?: RepoName;

  @property({type: String})
  branch = '' as BranchName;

  @property({type: Object})
  _repoConfig?: ConfigInfo;

  @property({type: String})
  subject = '';

  @property({type: String})
  topic?: string;

  @property({type: Object})
  _query?: (input: string) => Promise<{name: BranchName}[]>;

  @property({type: String})
  baseChange?: ChangeId;

  @property({type: String})
  baseCommit?: string;

  @property({type: Object})
  privateByDefault?: InheritedBooleanInfo;

  @property({type: Boolean, notify: true})
  canCreate = false;

  @property({type: Boolean})
  _privateChangesEnabled = false;

  restApiService = appContext.restApiService;

  disconnected$ = new Subject();

  constructor() {
    super();
    this._query = (input: string) => this._getRepoBranchesSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    if (!this.repoName) return;

    repoConfig$.pipe(takeUntil(this.disconnected$)).subscribe(config => {
      this.privateByDefault = config?.private_by_default;
    });

    serverConfig$.pipe(takeUntil(this.disconnected$)).subscribe(config => {
      this._privateChangesEnabled =
        config?.change?.disable_private_changes ?? false;
    });
  }

  override disconnectedCallback() {
    this.disconnected$.next();
    super.disconnectedCallback();
  }

  _computeBranchClass(baseChange?: ChangeId) {
    return baseChange ? 'hide' : '';
  }

  @observe('branch', 'subject')
  _allowCreate(branch: BranchName, subject: string) {
    this.canCreate = !!branch && !!subject;
  }

  handleCreateChange(): Promise<void> {
    if (!this.repoName || !this.branch || !this.subject) {
      return Promise.resolve();
    }
    const isPrivate = this.$.privateChangeCheckBox.checked;
    const isWip = true;
    return this.restApiService
      .createChange(
        this.repoName,
        this.branch,
        this.subject,
        this.topic,
        isPrivate,
        isWip,
        this.baseChange,
        this.baseCommit || undefined
      )
      .then(changeCreated => {
        if (!changeCreated) {
          return;
        }
        GerritNav.navigateToChange(changeCreated, {});
      });
  }

  _getRepoBranchesSuggestions(input: string) {
    if (!this.repoName) {
      return Promise.reject(new Error('missing repo name'));
    }
    if (input.startsWith(REF_PREFIX)) {
      input = input.substring(REF_PREFIX.length);
    }
    return this.restApiService
      .getRepoBranches(input, this.repoName, SUGGESTIONS_LIMIT)
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          let name: string = branchInfo.ref;
          if (name.startsWith('refs/heads/')) {
            name = name.substring('refs/heads/'.length);
          }
          branches.push({name: name as BranchName});
        }
        return branches;
      });
  }

  _formatBooleanString(config?: InheritedBooleanInfo) {
    if (
      config &&
      config.configured_value === InheritedBooleanInfoConfiguredValue.TRUE
    ) {
      return true;
    } else if (
      config &&
      config.configured_value === InheritedBooleanInfoConfiguredValue.FALSE
    ) {
      return false;
    } else if (
      config &&
      config.configured_value === InheritedBooleanInfoConfiguredValue.INHERITED
    ) {
      return !!(config && config.inherited_value);
    } else {
      return false;
    }
  }

  _computePrivateSectionClass(config: boolean) {
    return config ? 'hide' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-change-dialog': GrCreateChangeDialog;
  }
}
