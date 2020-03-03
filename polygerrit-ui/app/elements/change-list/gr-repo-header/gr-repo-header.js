/**
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import "../../../scripts/bundled-polymer.js";

import '../../../styles/dashboard-header-styles.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import { GestureEventListeners } from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import { LegacyElementMixin } from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import { PolymerElement } from '@polymer/polymer/polymer-element.js';
import { htmlTemplate } from './gr-repo-header_html.js';

/** @extends Polymer.Element */
class GrRepoHeader extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-repo-header'; }

  static get properties() {
    return {
    /** @type {?string} */
      repo: {
        type: String,
        observer: '_repoChanged',
      },
      /** @type {string|null} */
      _repoUrl: String,
    };
  }

  _repoChanged(repoName) {
    if (!repoName) {
      this._repoUrl = null;
      return;
    }
    this._repoUrl = Gerrit.Nav.getUrlForRepo(repoName);
  }
}

customElements.define(GrRepoHeader.is, GrRepoHeader);
