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

import { htmlTemplate } from './ac-drt_html.js';
import { RelatedChanges } from './findGerritChanges.js';

class AcSubmitWithSuperDialog extends Polymer.Element {
  static get is() { return 'ac-submit-with-super-dialog'; }

  static get template() { return htmlTemplate; }

  static get properties() {
    return {
      change: Object,
      action: Object,
    };
  }

  constructor() {
    super();
    this.related = new RelatedChanges;
  }

  connectedCallback() {
    super.connectedCallback();
    this.plugin.custom_popup = this;
  }

  resetFocus(e) {
    this.$.dialog.resetFocus();
  }

  doSubmit(changeNum) {
    return this.plugin.restApi().post(`/changes/${changeNum}/revisions/current/submit`);
  }

  async doSubmitToSubModule(e) {
    this.$.dialog.disabled = true;
    e.preventDefault();
    try {
      await this.plugin.restApi().post(this.get('action').__url, {});
      this.$.dialog.disabled = false;
      this.plugin.custom_popup_promise.close();
      this.plugin.custom_popup_promise = null;
      window.location.reload(true);
    } catch (errText) {
      this.fire('show-error', {
        message: `Could not perform action: ${errText}`,
      });
      this.$.dialog.disabled = false;
    }
  }

  _handleConfirmTap(e) {
    this.related.find(this, this.change, 'Submit', '', '').then(() => {
      for (const submodule of this.SubModules)
        if (this.related.superModules.includes(submodule.name)) {
          const changeNum = submodule.branches[0].value;
          if (this.plugin.otherChange != changeNum) {
            alert('An error was detected, please refresh your Gerrit page');
            console.log(`Error, user need refresh page, found chage ${changeNum} instead of change: ${this.plugin.otherChange}`);
            return;
          }
          this.doSubmit(changeNum).then(() => {
            console.log('Change ' + changeNum + ' was Submited');
            this.doSubmitToSubModule(e);
          }, alert);
          return;
        }

      this.doSubmitToSubModule(e);
    });
  }

  _handleCancelTap(e) {
    e.preventDefault();
    this.plugin.custom_popup_promise.close();
    this.plugin.custom_popup_promise = null;
  }
}

customElements.define(AcSubmitWithSuperDialog.is, AcSubmitWithSuperDialog);