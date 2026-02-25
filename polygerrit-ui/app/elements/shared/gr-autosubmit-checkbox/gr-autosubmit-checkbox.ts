/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-icon/gr-icon';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import {LitElement, html, css, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {subscribe} from '../../lit/subscription-controller';
import {combineLatest} from 'rxjs';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';
import {changeModelToken} from '../../../models/change/change-model';
import {userModelToken} from '../../../models/user/user-model';
import {configModelToken} from '../../../models/config/config-model';
import {getDocUrl} from '../../../utils/url-util';
import {resolve} from '../../../models/dependency';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/form-styles';
import {ChangeInfo, ParsedChangeInfo} from '../../../types/common';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';

declare global {
  interface HTMLElementTagNameMap {
    'gr-autosubmit-checkbox': GrAutosubmitCheckbox;
  }
}

@customElement('gr-autosubmit-checkbox')
export class GrAutosubmitCheckbox extends LitElement {
  @property({type: Object})
  change?: ParsedChangeInfo | ChangeInfo;

  @state()
  isAutosubmitEnabled = false;

  @state()
  showAutosubmitInfoMessage = false;

  @state()
  autosubmitChecked = false;

  @state()
  private docsBaseUrl = '';

  private readonly getFlowsModel = resolve(this, flowsModelToken);

  private flowsDocumentationLink?: string;

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

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
      `,
    ];
  }

  constructor() {
    super();
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
        this.showAutosubmitInfoMessage = isAutosubmitEnabled &&
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
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
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
