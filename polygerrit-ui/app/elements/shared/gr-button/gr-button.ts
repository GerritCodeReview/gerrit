/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-icon/gr-icon';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {votingStyles} from '../../../styles/gr-voting-styles';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {getEventPath, Key} from '../../../utils/dom-util';
import {getAppContext} from '../../../services/app-context';
import {classMap} from 'lit/directives/class-map.js';
import {Interaction} from '../../../constants/reporting';
import {ShortcutController} from '../../lit/shortcut-controller';
import '@material/web/button/elevated-button';
import '@material/web/button/text-button';

declare global {
  interface HTMLElementTagNameMap {
    'gr-button': GrButton;
  }
}
/**
 * @attr {Boolean} position-below
 * @attr {Boolean} primary - set primary button color
 * @attr {Boolean} secondary - set secondary button color
 */
@customElement('gr-button')
export class GrButton extends LitElement {
  /**
   * Should this button be rendered as a vote chip? Then we are applying
   * the .voteChip class (see gr-voting-styles) to the md-text-button/md-elevated-button.
   */
  @property({type: Boolean, reflect: true})
  voteChip = false;

  // Note: don't assign a value to this, since constructor is called
  // after created, the initial value maybe overridden by this
  private initialTabindex?: string;

  @property({type: Boolean, attribute: 'down-arrow'})
  downArrow = false;

  @property({type: Boolean, reflect: true})
  link = false;

  // If flattened then the button will not be shown as raised.
  @property({type: Boolean, reflect: true})
  flatten = false;

  @property({type: Boolean, reflect: true})
  loading = false;

  @property({type: Boolean, reflect: true})
  disabled: boolean | null = null;

  // Private but used in tests.
  readonly reporting = getAppContext().reportingService;

  private readonly shortcuts = new ShortcutController(this);

  static override get styles() {
    return [
      votingStyles,
      spinnerStyles,
      css`
        /* general styles for all buttons */
        :host {
          --background-color: var(
            --button-background-color,
            var(--default-button-background-color)
          );
          --text-color: var(
            --gr-button-text-color,
            var(--default-button-text-color)
          );
          display: inline-block;
          position: relative;
        }
        :host([hidden]) {
          display: none;
        }
        :host md-text-button,
        :host md-elevated-button {
          --md-text-button-label-text-font: var(--header-font-family);
          --md-text-button-label-text-weight: var(--font-weight-medium);
          --md-elevated-button-label-text-font: var(--header-font-family);
          --md-elevated-button-label-text-weight: var(--font-weight-medium);
          text-transform: none;
          /* This is also set in the button-label-(font|weight) css vars above. We keep this incase it is also needed. */
          font-weight: var(--font-weight-medium);
          font-family: var(--header-font-family);
          color: var(--text-color);
          --md-text-button-container-color: var(--text-color);
        }

        md-text-button,
        md-elevated-button {
          --md-text-button-label-text-font: var(--font-family, inherit);
          --md-text-button-label-text-weight: var(
            --font-weight-normal,
            inherit
          );
          --md-elevated-button-label-text-font: var(--font-family, inherit);
          --md-elevated-button-label-text-weight: var(
            --font-weight-normal,
            inherit
          );
          --md-text-button-container-color: var(--background-color);
          --md-text-button-hover-container-color: var(--background-color);
          --md-elevated-button-container-color: var(--background-color);
          --md-elevated-button-hover-container-color: var(--background-color);
          --md-sys-color-primary: var(--text-color);
          /* We set this to 0 which defaults to 12px under text-button.
            This is because for some reason it makes the size of it bigger
            which makes some of the buttons look wrong. E.g. attention set has
            a bigger width and thus a lot of space. This fixes it. */
          --md-text-button-leading-space: 0;
          --md-text-button-trailing-space: 0;
          /* Brings back the round corners we had with paper-button */
          --md-text-button-container-shape: 4px;
          --md-elevated-button-container-shape: 4px;
          /* We have a variable for setting the text colour when it is disabled */
          --md-elevated-button-disabled-label-text-color: var(--text-color);
          --md-text-button-disabled-label-text-color: var(--text-color);
          align-items: center;
          background-color: var(--background-color);
          color: var(--text-color);
          /* paper-button set this but md-(elevated|text)-button does not. So we set it. */
          font: inherit;
          /* This is also set in the button-label-(font|weight) css vars above. We keep this incase it is also needed. */
          font-family: var(--font-family, inherit);
          font-weight: var(--font-weight-normal, inherit);
          display: flex;
          justify-content: center;
          margin: var(--margin, 0);
          min-width: var(--border, 0);
          padding: var(--gr-button-padding, var(--spacing-s) var(--spacing-m));
          /* Needed to resize properly */
          --md-elevated-button-container-height: none;
          --md-text-button-container-height: none;
          cursor: pointer;
        }
        :host md-text-button:hover,
        :host md-elevated-button:hover {
          background: linear-gradient(rgba(0, 0, 0, 0.12), rgba(0, 0, 0, 0.12)),
            var(--background-color);
        }

        :host([primary]) {
          --background-color: var(--primary-button-background-color);
          --text-color: var(--primary-button-text-color);
        }
        :host([link][primary]) {
          --text-color: var(--primary-button-background-color);
        }

        /* Keep below color definition for primary so that this takes precedence
          when disabled. */
        :host([disabled]),
        :host([loading]) {
          --background-color: var(--disabled-button-background-color);
          --text-color: var(--deemphasized-text-color);
          cursor: default;
        }

        :host([disabled][flatten]) {
          --background-color: transparent;
          --text-color: var(--disabled-foreground);
        }

        /* Styles for link buttons specifically */
        :host([link]) {
          --background-color: transparent;
          --margin: 0;
        }
        :host([disabled][link]),
        :host([loading][link]) {
          --background-color: transparent;
          --text-color: var(--disabled-foreground);
          cursor: default;
        }
        gr-icon.downArrow {
          color: inherit;
        }
        .newVoteChip {
          border: 1px solid var(--border-color);
          box-shadow: none;
          box-sizing: border-box;
          min-width: 3em;
          --md-sys-color-primary: var(--vote-text-color);
        }
        .loadingSpin {
          display: inline-block;
        }
        .flex {
          display: flex;
        }
      `,
    ];
  }

  override render() {
    const buttonClass = classMap({
      newVoteChip: this.voteChip,
    });

    if (!this.link && !this.flatten) {
      return html`
        <md-elevated-button
          class=${buttonClass}
          ?disabled=${this.disabled || this.loading}
          part="md-elevated-button"
          touch-target="none"
          role="button"
          tabindex="-1"
        >
          ${this.loading ? html`<span class="loadingSpin"></span>` : ''}
          <slot></slot>
          ${this.renderArrowIcon()}
        </md-elevated-button>
      `;
    }

    return html`
      <md-text-button
        class=${buttonClass}
        ?disabled=${this.disabled || this.loading}
        part="md-text-button"
        touch-target="none"
        role="button"
        tabindex="-1"
      >
        ${this.loading ? html`<span class="loadingSpin"></span>` : ''}
        <div class="flex">
          <slot></slot>
          ${this.renderArrowIcon()}
        </div>
      </md-text-button>
    `;
  }

  renderArrowIcon() {
    if (!this.downArrow) return nothing;
    return html`<gr-icon icon="arrow_drop_down" class="downArrow"></gr-icon>`;
  }

  constructor() {
    super();
    this.initialTabindex = this.getAttribute('tabindex') || '0';
    this.addEventListener('click', e => this.handleAction(e));
    this.shortcuts.addLocal({key: Key.ENTER}, () => this.click());
    this.shortcuts.addLocal({key: Key.SPACE}, () => this.click());
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('disabled')) {
      this.setAttribute(
        'tabindex',
        this.disabled ? '-1' : this.initialTabindex || '0'
      );
    }
    if (changedProperties.has('loading') || changedProperties.has('disabled')) {
      this.setAttribute(
        'aria-disabled',
        this.disabled || this.loading ? 'true' : 'false'
      );
    }
  }

  override connectedCallback() {
    super.connectedCallback();
    if (!this.getAttribute('role')) {
      this.setAttribute('role', 'button');
    }
    if (!this.getAttribute('tabindex')) {
      this.setAttribute('tabindex', '0');
    }
  }

  private handleAction(e: MouseEvent) {
    if (this.disabled || this.loading) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      return;
    }

    this.reporting.reportInteraction(Interaction.BUTTON_CLICK, {
      path: getEventPath(e),
      // Before change 456201 `<gr-button>` used css text-transform:uppercase.
      // We are using `toUpperCase()` here to keep the logs consistent.
      text: this.innerText.toUpperCase(),
    });
  }
}
