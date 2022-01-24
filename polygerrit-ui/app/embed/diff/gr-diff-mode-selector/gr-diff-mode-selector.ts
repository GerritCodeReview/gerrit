/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import {Subscription} from 'rxjs';
import '@polymer/iron-icon/iron-icon';
import '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import '../../../styles/shared-styles';
import '../../../elements/shared/gr-button/gr-button';
import {DiffViewMode} from '../../../constants/constants';
import {htmlTemplate} from './gr-diff-mode-selector_html';
import {customElement, property} from '@polymer/decorators';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {FixIronA11yAnnouncer} from '../../../types/types';
import {getAppContext} from '../../../services/app-context';
import {fireIronAnnounce} from '../../../utils/event-util';
import {browserModelToken} from '../../../models/browser/browser-model';
import {resolve, DIPolymerElement} from '../../../models/dependency';

@customElement('gr-diff-mode-selector')
export class GrDiffModeSelector extends DIPolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, notify: true})
  mode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE;

  /**
   * If set to true, the user's preference will be updated every time a
   * button is tapped. Don't set to true if there is no user.
   */
  @property({type: Boolean})
  saveOnChange = false;

  @property({type: Boolean})
  showTooltipBelow = false;

  // Private but accessed by tests.
  readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly userModel = getAppContext().userModel;

  private subscriptions: Subscription[] = [];

  constructor() {
    super();
  }

  override connectedCallback() {
    super.connectedCallback();
    (
      IronA11yAnnouncer as unknown as FixIronA11yAnnouncer
    ).requestAvailability();
    this.subscriptions.push(
      this.getBrowserModel().diffViewMode$.subscribe(
        diffView => (this.mode = diffView)
      )
    );
  }

  override disconnectedCallback() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    super.disconnectedCallback();
  }

  /**
   * Set the mode. If save on change is enabled also update the preference.
   */
  setMode(newMode: DiffViewMode) {
    if (this.saveOnChange && this.mode && this.mode !== newMode) {
      this.userModel.updatePreferences({diff_view: newMode});
    }
    this.mode = newMode;
    let announcement;
    if (this.isUnifiedSelected(newMode)) {
      announcement = 'Changed diff view to unified';
    } else if (this.isSideBySideSelected(newMode)) {
      announcement = 'Changed diff view to side by side';
    }
    if (announcement) {
      fireIronAnnounce(this, announcement);
    }
  }

  _computeSideBySideSelected(mode?: DiffViewMode) {
    return mode === DiffViewMode.SIDE_BY_SIDE ? 'selected' : '';
  }

  _computeUnifiedSelected(mode?: DiffViewMode) {
    return mode === DiffViewMode.UNIFIED ? 'selected' : '';
  }

  isSideBySideSelected(mode?: DiffViewMode) {
    return mode === DiffViewMode.SIDE_BY_SIDE;
  }

  isUnifiedSelected(mode?: DiffViewMode) {
    return mode === DiffViewMode.UNIFIED;
  }

  _handleSideBySideTap() {
    this.setMode(DiffViewMode.SIDE_BY_SIDE);
  }

  _handleUnifiedTap() {
    this.setMode(DiffViewMode.UNIFIED);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-mode-selector': GrDiffModeSelector;
  }
}
