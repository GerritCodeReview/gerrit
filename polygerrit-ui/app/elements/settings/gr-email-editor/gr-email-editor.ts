/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-email-editor_html';
import {customElement, property} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {EmailInfo} from '../../../types/common';

export interface GrEmailEditor {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-email-editor')
export class GrEmailEditor extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasUnsavedChanges = false;

  @property({type: Array})
  _emails: EmailInfo[] = [];

  @property({type: Array})
  _emailsToRemove: EmailInfo[] = [];

  @property({type: String})
  _newPreferred: string | null = null;

  loadData() {
    return this.$.restAPI.getAccountEmails().then(emails => {
      this._emails = emails ?? [];
    });
  }

  save() {
    const promises: Promise<unknown>[] = [];

    for (const emailObj of this._emailsToRemove) {
      promises.push(this.$.restAPI.deleteAccountEmail(emailObj.email));
    }

    if (this._newPreferred) {
      promises.push(
        this.$.restAPI.setPreferredAccountEmail(this._newPreferred)
      );
    }

    return Promise.all(promises).then(() => {
      this._emailsToRemove = [];
      this._newPreferred = null;
      this.hasUnsavedChanges = false;
    });
  }

  _handleDeleteButton(e: Event) {
    const target = (dom(e) as EventApi).localTarget;
    if (!(target instanceof Element)) return;
    const indexStr = target.getAttribute('data-index');
    if (indexStr === null) return;
    const index = Number(indexStr);
    const email = this._emails[index];
    this.push('_emailsToRemove', email);
    this.splice('_emails', index, 1);
    this.hasUnsavedChanges = true;
  }

  _handlePreferredControlClick(e: Event) {
    if (
      e.target instanceof HTMLElement &&
      e.target.classList.contains('preferredControl') &&
      e.target.firstElementChild instanceof HTMLInputElement
    ) {
      e.target.firstElementChild.click();
    }
  }

  _handlePreferredChange(e: Event) {
    if (!(e.target instanceof HTMLInputElement)) return;
    const preferred = e.target.value;
    for (let i = 0; i < this._emails.length; i++) {
      if (preferred === this._emails[i].email) {
        this.set(['_emails', i, 'preferred'], true);
        this._newPreferred = preferred;
        this.hasUnsavedChanges = true;
      } else if (this._emails[i].preferred) {
        this.set(['_emails', i, 'preferred'], false);
      }
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-email-editor': GrEmailEditor;
  }
}
