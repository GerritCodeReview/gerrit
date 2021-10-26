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
import '@polymer/paper-tabs/paper-tab';
import '@polymer/paper-tabs/paper-tabs';
import '../gr-shell-command/gr-shell-command';
import '../../../styles/gr-paper-styles';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-download-commands_html';
import {customElement, property} from '@polymer/decorators';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';
import {appContext} from '../../../services/app-context';
import {queryAndAssert} from '../../../utils/common-util';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {preferences$} from '../../../services/user/user-model';
import {takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';

declare global {
  interface HTMLElementEventMap {
    'selected-changed': CustomEvent<{value: number}>;
  }
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

  // TODO(TS): maybe default to [] as only used in dom-repeat
  @property({type: Array})
  commands?: Command[];

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Array})
  schemes: string[] = [];

  @property({type: String, notify: true})
  selectedScheme?: string;

  @property({type: Boolean})
  showKeyboardShortcutTooltips = false;

  private readonly restApiService = appContext.restApiService;

  private readonly userService = appContext.userService;

  disconnected$ = new Subject();

  override connectedCallback() {
    super.connectedCallback();
    this._getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    preferences$.pipe(takeUntil(this.disconnected$)).subscribe(prefs => {
      if (prefs?.download_scheme) {
        // Note (issue 5180): normalize the download scheme with lower-case.
        this.selectedScheme = prefs.download_scheme.toLowerCase();
      }
    });
  }

  override disconnectedCallback() {
    this.disconnected$.next();
    super.disconnectedCallback();
  }

  focusOnCopy() {
    queryAndAssert<GrShellCommand>(this, 'gr-shell-command').focusOnCopy();
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _handleTabChange(e: CustomEvent<{value: number}>) {
    const scheme = this.schemes[e.detail.value];
    if (scheme && scheme !== this.selectedScheme) {
      this.set('selectedScheme', scheme);
      if (this._loggedIn) {
        this.userService.updatePreferences({
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

  _computeTooltip(showKeyboardShortcutTooltips: boolean, index: number) {
    return index <= 4 && showKeyboardShortcutTooltips
      ? `Keyboard shortcut: ${index + 1}`
      : '';
  }

  // TODO: maybe unify with strToClassName from dom-util
  _computeClass(title: string) {
    // Only retain [a-z] chars, so "Cherry Pick" becomes "cherrypick".
    return '_label_' + title.replace(/[^a-z]+/gi, '').toLowerCase();
  }
}
