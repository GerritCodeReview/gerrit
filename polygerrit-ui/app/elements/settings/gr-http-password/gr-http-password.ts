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
import '../../../styles/gr-form-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-http-password_html';
import {property, customElement} from '@polymer/decorators';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrRestApiInterface} from '../../shared/gr-rest-api-interface/gr-rest-api-interface';

declare global {
  interface HTMLElementTagNameMap {
    'gr-http-password': GrHttpPassword;
  }
}

export interface GrHttpPassword {
  $: {
    restAPI: GrRestApiInterface;
    generatedPasswordOverlay: GrOverlay;
  };
}

@customElement('gr-http-password')
export class GrHttpPassword extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  _username?: string;

  @property({type: String})
  _generatedPassword?: string;

  @property({type: String})
  _passwordUrl: string | null = null;

  /** @override */
  attached() {
    super.attached();
    this.loadData();
  }

  loadData() {
    const promises = [];

    promises.push(
      this.$.restAPI.getAccount().then(account => {
        if (account) {
          this._username = account.username;
        }
      })
    );

    promises.push(
      this.$.restAPI.getConfig().then(info => {
        if (info) {
          this._passwordUrl = info.auth.http_password_url || null;
        } else {
          this._passwordUrl = null;
        }
      })
    );

    return Promise.all(promises);
  }

  _handleGenerateTap() {
    this._generatedPassword = 'Generating...';
    this.$.generatedPasswordOverlay.open();
    this.$.restAPI.generateAccountHttpPassword().then(newPassword => {
      this._generatedPassword = newPassword;
    });
  }

  _closeOverlay() {
    this.$.generatedPasswordOverlay.close();
  }

  _generatedPasswordOverlayClosed() {
    this._generatedPassword = '';
  }
}
