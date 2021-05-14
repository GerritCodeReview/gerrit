/**
 * @license
 * Copyright (C) 2021 AudioCodes Ltd.
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

import { htmlTemplate } from './ac-confirm-submit_html.js';

class AcConfirmSubmit extends Polymer.Element {
  static get is() { return 'submit-for-another'; }

  static get template() { return htmlTemplate; }

  static get properties() {
    return {
      otherUser: String,
      otherChange: String,
      change: Object,
    };
  }

  attached() {
    this.otherChange = this.plugin.otherChange;
    Gerrit.get('/accounts/self', u => {
      const accountID = u._account_id;
      if (accountID != this.change.owner._account_id)
        this.otherUser = this.change.owner.name;
      else
        this.otherUser = null;
    });
  }
}

customElements.define(AcConfirmSubmit.is, AcConfirmSubmit);