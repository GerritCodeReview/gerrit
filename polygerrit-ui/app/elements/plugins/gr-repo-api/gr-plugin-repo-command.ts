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
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-plugin-repo-command_html.js';

class GrPluginRepoCommand extends PolymerElement {
  static get is() {
    return 'gr-plugin-repo-command';
  }

  static get properties() {
    return {
      title: String,
      repoName: String,
      config: Object,
    };
  }

  static get template() {
    return htmlTemplate;
  }

  _handleClick() {
    this.dispatchEvent(
        new CustomEvent('command-tap', {composed: true, bubbles: true})
    );
  }
}

customElements.define(GrPluginRepoCommand.is, GrPluginRepoCommand);