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
import '@polymer/paper-tabs/paper-tabs';
import '../gr-shell-command/gr-shell-command';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-download-commands_html';
import {customElement, property, observe} from '@polymer/decorators';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {appContext} from '../../../services/app-context';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {strToClassName} from '../../../utils/dom-util';
import {queryAndAssert} from '../../../test/test-utils';

declare global {
  interface HTMLElementTagNameMap {
    'gr-download-commands': GrDownloadCommands;
  }
}

export interface GrDownloadCommands {
  $: {
    downloadTabs: PaperTabsElement;
  };
}

export interface Command {
  title: string;
  command: string;
}

@customElement('gr-download-commands')
export class GrDownloadCommands extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  commands: Command[] = [];

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Array})
  schemes: string[] = [];

  @property({type: String, notify: true})
  selectedScheme?: string;

  private readonly restApiService = appContext.restApiService;

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
  }

  focusOnCopy() {
    queryAndAssert<GrShellCommand>(this, 'gr-shell-command').focusOnCopy();
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  @observe('_loggedIn')
  _loggedInChanged(loggedIn: boolean) {
    if (!loggedIn) {
      return;
    }
    return this.restApiService.getPreferences().then(prefs => {
      if (prefs?.download_scheme) {
        // Note (issue 5180): normalize the download scheme with lower-case.
        this.selectedScheme = prefs.download_scheme.toLowerCase();
      }
    });
  }

  _handleTabChange(e: CustomEvent<{value: number}>) {
    const scheme = this.schemes[e.detail.value];
    if (scheme && scheme !== this.selectedScheme) {
      this.set('selectedScheme', scheme);
      if (this._loggedIn) {
        this.restApiService.savePreferences({
          download_scheme: this.selectedScheme,
        });
      }
    }
  }

  _computeSelected(schemes: string[], selectedScheme?: string) {
    return `${schemes.findIndex(scheme => scheme === selectedScheme) || 0}`;
  }

  _computeShowTabs(schemes: string[]) {
    return schemes.length > 1 ? '' : 'hidden';
  }

  _computeClass(title: string) {
    // Only retain [a-z] chars, so "Cherry Pick" becomes "cherrypick".
    return strToClassName(title, '_label_', '');
  }
}
