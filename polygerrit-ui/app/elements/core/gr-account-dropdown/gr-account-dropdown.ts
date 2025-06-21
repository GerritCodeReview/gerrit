/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-avatar/gr-avatar';
import {getUserName} from '../../../utils/display-name-util';
import {AccountInfo, DropdownLink, ServerInfo} from '../../../types/common';
import {fire} from '../../../utils/event-util';
import {DropdownContent} from '../../shared/gr-dropdown/gr-dropdown';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {subscribe} from '../../lit/subscription-controller';
import '@material/web/icon/icon';

const INTERPOLATE_URL_PATTERN = /\${([\w]+)}/g;

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-dropdown': GrAccountDropdown;
  }
  interface HTMLElementEventMap {
    'show-keyboard-shortcuts': CustomEvent<{}>;
  }
}

@customElement('gr-account-dropdown')
export class GrAccountDropdown extends LitElement {
  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  showMobile?: boolean;

  // Private but used in test
  @state()
  config?: ServerInfo;

  @state()
  private path = '/';

  @state()
  private hasAvatars = false;

  @state()
  private switchAccountUrl = '';

  // private but used in test
  @state() feedbackURL = '';

  // Private but used in test
  readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      cfg => {
        this.config = cfg;

        if (cfg?.gerrit?.report_bug_url) {
          this.feedbackURL = cfg?.gerrit.report_bug_url;
        }

        if (cfg && cfg.auth && cfg.auth.switch_account_url) {
          this.switchAccountUrl = cfg.auth.switch_account_url;
        } else {
          this.switchAccountUrl = '';
        }
        this.hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.handleLocationChange();
    document.addEventListener('location-change', this.handleLocationChange);
  }

  override disconnectedCallback() {
    document.removeEventListener('location-change', this.handleLocationChange);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        gr-dropdown {
          padding: 0 var(--spacing-m);
          --gr-button-text-color: var(--header-text-color);
          --gr-dropdown-item-color: var(--primary-text-color);
        }
        gr-avatar {
          height: 2em;
          width: 2em;
          vertical-align: middle;
        }
        md-icon[filled] {
          font-variation-settings: 'FILL' 1;
        }
      `,
    ];
  }

  override render() {
    return html`<gr-dropdown
      link=""
      .items=${this.links}
      .topContent=${this.topContent}
      @tap-item-shortcuts=${this.handleShortcutsTap}
      .horizontalAlign=${'right'}
    >
      ${this.showMobile && !this.hasAvatars
        ? html`<md-icon filled>account_circle</md-icon>`
        : html`<span ?hidden=${this.hasAvatars}
            >${this.accountName(this.account)}</span
          >`}
      <gr-avatar
        .account=${this.account}
        ?hidden=${!this.hasAvatars}
        .imageSize=${56}
        aria-label="Account avatar"
      ></gr-avatar>
    </gr-dropdown>`;
  }

  get links(): DropdownLink[] | undefined {
    return this.getLinks(this.switchAccountUrl, this.path);
  }

  get topContent(): DropdownContent[] | undefined {
    return this.getTopContent(this.account);
  }

  // Private but used in test
  getLinks(switchAccountUrl?: string, path?: string) {
    if (switchAccountUrl === undefined || path === undefined) {
      return undefined;
    }

    const links: DropdownLink[] = [];
    links.push({name: 'Settings', id: 'settings', url: '/settings/'});
    links.push({name: 'Keyboard Shortcuts', id: 'shortcuts'});
    if (switchAccountUrl) {
      const replacements = {path};
      const url = this.interpolateUrl(switchAccountUrl, replacements);
      links.push({name: 'Switch account', url, external: true});
    }
    if (this.showMobile && this.feedbackURL) {
      links.push({
        name: 'Feedback',
        id: 'feedback',
        url: this.feedbackURL,
        external: true,
        target: '_blank',
      });
    }
    links.push({name: 'Sign out', id: 'signout', url: '/logout'});
    return links;
  }

  // Private but used in test
  getTopContent(account?: AccountInfo) {
    return [
      {text: this.accountName(account), bold: true},
      {text: account?.email ? account.email : ''},
    ] as DropdownContent[];
  }

  private handleShortcutsTap() {
    fire(this, 'show-keyboard-shortcuts', {});
  }

  private readonly handleLocationChange = () => {
    this.path =
      window.location.pathname + window.location.search + window.location.hash;
  };

  // Private but used in test
  interpolateUrl(url: string, replacements: {[key: string]: string}) {
    return url.replace(
      INTERPOLATE_URL_PATTERN,
      (_, p1) => replacements[p1] || ''
    );
  }

  private accountName(account?: AccountInfo) {
    return getUserName(this.config, account);
  }
}
