/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-icon/gr-icon';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {combineLatest} from 'rxjs';
import {flowsModelToken} from '../../../models/flows/flows-model';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/form-styles';
import {ChangeInfo} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {fire} from '../../../utils/event-util';

export interface AutosubmitCheckedChangedEventDetail {
  checked: boolean;
}
export type AutosubmitCheckedChangedEvent =
  CustomEvent<AutosubmitCheckedChangedEventDetail>;

declare global {
  interface HTMLElementTagNameMap {
    'gr-autosubmit-checkbox': GrAutosubmitCheckbox;
  }
  interface HTMLElementEventMap {
    'autosubmit-checked-changed': AutosubmitCheckedChangedEvent;
  }
}

@customElement('gr-autosubmit-checkbox')
export class GrAutosubmitCheckbox extends LitElement {
  @state()
  change?: ParsedChangeInfo | ChangeInfo;

  @state()
  isAutosubmitEnabled = false;

  @state()
  showAutosubmitInfoMessage = false;

  @state()
  autosubmitChecked = false;

  readonly getFlowsModel = resolve(this, flowsModelToken);

  private flowsDocumentationLink?: string;

  readonly getChangeModel = resolve(this, changeModelToken);

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        .autosubmit-label {
          display: flex;
          align-items: center;
        }
        .autosubmit-text {
          padding-left: var(--spacing-m);
        }
        .autosubmit-info {
          display: flex;
          align-items: center;
        }
        .autosubmit-info gr-icon {
          color: var(--info-foreground);
          margin-right: var(--spacing-m);
        }
        md-checkbox {
          --md-checkbox-container-size: 15px;
          --md-checkbox-icon-size: 15px;
        }
        :host {
          padding: var(--spacing-m) 0;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.change = x)
    );
    subscribe(
      this,
      () =>
        combineLatest([
          this.getFlowsModel().isAutosubmitEnabled$,
          this.getFlowsModel().enabled$,
          this.getFlowsModel().flows$,
          this.getChangeModel().isOwner$,
        ]),
      ([isAutosubmitEnabled, isFlowsEnabled, _, isOwner]) => {
        this.isAutosubmitEnabled =
          isAutosubmitEnabled &&
          isFlowsEnabled &&
          !this.getFlowsModel().hasAutosubmitFlowAlready() &&
          isOwner;
        this.showAutosubmitInfoMessage =
          isAutosubmitEnabled &&
          isFlowsEnabled &&
          this.getFlowsModel().hasAutosubmitFlowAlready();
      }
    );
    subscribe(
      this,
      () => this.getFlowsModel().providers$,
      providers => {
        this.flowsDocumentationLink = providers
          .map(p => p.getDocumentation())
          .find(doc => !!doc);
      }
    );
  }

  override render() {
    if (this.showAutosubmitInfoMessage) {
      return html`
        <div class="autosubmit-info">
          <gr-icon icon="info"></gr-icon>
          <span>Autosubmit Enabled.</span>
        </div>
      `;
    }
    if (this.isAutosubmitEnabled) {
      return html`
        <div class="autosubmit">
          <label class="autosubmit-label">
            <md-checkbox
              id="autosubmit"
              @change=${this.handleAutosubmitChanged}
              ?checked=${this.autosubmitChecked}
            ></md-checkbox>
            <span class="autosubmit-text">Enable Autosubmit</span>
            ${this.renderDocumentationLink()}
          </label>
        </div>
      `;
    }
    return nothing;
  }

  private renderDocumentationLink() {
    if (!this.flowsDocumentationLink) return nothing;
    return html` <a
      class="help"
      slot="trailing-icon"
      href=${this.flowsDocumentationLink}
      target="_blank"
      rel="noopener noreferrer"
      tabindex="-1"
    >
      <md-icon-button touch-target="none" type="button">
        <gr-icon icon="help" title="read documentation"></gr-icon>
      </md-icon-button>
    </a>`;
  }

  private handleAutosubmitChanged(e: Event) {
    if (!(e.target instanceof MdCheckbox)) return;
    this.autosubmitChecked = e.target.checked;
    fire(this, 'autosubmit-checked-changed', {checked: this.autosubmitChecked});
  }

  getIsAutosubmitChecked() {
    return this.autosubmitChecked;
  }

  getIsAutosubmitEnabled() {
    return this.isAutosubmitEnabled;
  }
}
