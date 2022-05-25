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
import '../../../elements/shared/gr-button/gr-button';
import {DiffViewMode} from '../../../constants/constants';
import {customElement, property, state} from 'lit/decorators';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {FixIronA11yAnnouncer} from '../../../types/types';
import {getAppContext} from '../../../services/app-context';
import {fireIronAnnounce} from '../../../utils/event-util';
import {browserModelToken} from '../../../models/browser/browser-model';
import {resolve} from '../../../models/dependency';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';

@customElement('gr-diff-mode-selector')
export class GrDiffModeSelector extends LitElement {
  /**
   * If set to true, the user's preference will be updated every time a
   * button is tapped. Don't set to true if there is no user.
   */
  @property({type: Boolean}) saveOnChange = false;

  @property({type: Boolean}) showTooltipBelow = false;

  // visible for testing
  @state() mode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE;

  private readonly getBrowserModel = resolve(this, browserModelToken);

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

  static override styles = [
    sharedStyles,
    css`
      :host {
        /* Used to remove horizontal whitespace between the icons. */
        display: flex;
      }
      gr-button.selected iron-icon {
        color: var(--link-color);
      }
      iron-icon {
        height: 1.3rem;
        width: 1.3rem;
      }
    `,
  ];

  override render() {
    return html`
      <gr-tooltip-content
        has-tooltip
        title="Side-by-side diff"
        ?position-below=${this.showTooltipBelow}
      >
        <gr-button
          id="sideBySideBtn"
          link
          class=${this.computeSideBySideSelected()}
          aria-pressed=${this.isSideBySideSelected()}
          @click=${this.handleSideBySideTap}
        >
          <iron-icon icon="gr-icons:side-by-side"></iron-icon>
        </gr-button>
      </gr-tooltip-content>
      <gr-tooltip-content
        has-tooltip
        ?position-below=${this.showTooltipBelow}
        title="Unified diff"
      >
        <gr-button
          id="unifiedBtn"
          link
          class=${this.computeUnifiedSelected()}
          aria-pressed=${this.isUnifiedSelected()}
          @click=${this.handleUnifiedTap}
        >
          <iron-icon icon="gr-icons:unified"></iron-icon>
        </gr-button>
      </gr-tooltip-content>
    `;
  }

  /**
   * Set the mode. If save on change is enabled also update the preference.
   */
  private setMode(newMode: DiffViewMode) {
    if (this.saveOnChange && this.mode && this.mode !== newMode) {
      this.userModel.updatePreferences({diff_view: newMode});
    }
    this.mode = newMode;
    let announcement;
    if (this.isUnifiedSelected()) {
      announcement = 'Changed diff view to unified';
    } else if (this.isSideBySideSelected()) {
      announcement = 'Changed diff view to side by side';
    }
    if (announcement) {
      fireIronAnnounce(this, announcement);
    }
  }

  private computeSideBySideSelected() {
    return this.mode === DiffViewMode.SIDE_BY_SIDE ? 'selected' : '';
  }

  private computeUnifiedSelected() {
    return this.mode === DiffViewMode.UNIFIED ? 'selected' : '';
  }

  private isSideBySideSelected() {
    return this.mode === DiffViewMode.SIDE_BY_SIDE;
  }

  private isUnifiedSelected() {
    return this.mode === DiffViewMode.UNIFIED;
  }

  private handleSideBySideTap() {
    this.setMode(DiffViewMode.SIDE_BY_SIDE);
  }

  private handleUnifiedTap() {
    this.setMode(DiffViewMode.UNIFIED);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-mode-selector': GrDiffModeSelector;
  }
}
