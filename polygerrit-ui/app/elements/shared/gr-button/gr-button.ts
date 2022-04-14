/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/paper-button/paper-button';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {votingStyles} from '../../../styles/gr-voting-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators';
import {addShortcut, getEventPath, Key} from '../../../utils/dom-util';
import {getAppContext} from '../../../services/app-context';
import {classMap} from 'lit/directives/class-map';
import {KnownExperimentId} from '../../../services/flags/flags';

declare global {
  interface HTMLElementTagNameMap {
    'gr-button': GrButton;
  }
}
/**
 * @attr {Boolean} no-uppercase - text in button is not uppercased
 * @attr {Boolean} position-below
 * @attr {Boolean} primary - set primary button color
 * @attr {Boolean} secondary - set secondary button color
 */
@customElement('gr-button')
export class GrButton extends LitElement {
  // Private but used in tests.
  readonly reporting = getAppContext().reportingService;

  /**
   * Should this button be rendered as a vote chip? Then we are applying
   * the .voteChip class (see gr-voting-styles) to the paper-button.
   */
  @property({type: Boolean, reflect: true})
  voteChip = false;

  // Note: don't assign a value to this, since constructor is called
  // after created, the initial value maybe overridden by this
  private initialTabindex?: string;

  @property({type: Boolean, reflect: true, attribute: 'down-arrow'})
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
        :host([no-uppercase]) paper-button {
          text-transform: none;
        }
        paper-button {
          /* paper-button sets this to anti-aliased, which appears different than
            bold font elsewhere on macOS. */
          -webkit-font-smoothing: initial;
          align-items: center;
          background-color: var(--background-color);
          color: var(--text-color);
          display: flex;
          font-family: var(--font-family, inherit);
          /** Without this '.keyboard-focus' buttons will get bolded. */
          font-weight: var(--font-weight-normal, inherit);
          justify-content: center;
          margin: var(--margin, 0);
          min-width: var(--border, 0);
          padding: var(--gr-button-padding, var(--spacing-s) var(--spacing-m));
        }
        paper-button[elevation='1'] {
          box-shadow: var(--elevation-level-1);
        }
        paper-button[elevation='2'] {
          box-shadow: var(--elevation-level-2);
        }
        paper-button[elevation='3'] {
          box-shadow: var(--elevation-level-3);
        }
        paper-button[elevation='4'] {
          box-shadow: var(--elevation-level-4);
        }
        paper-button[elevation='5'] {
          box-shadow: var(--elevation-level-5);
        }
        paper-button:hover {
          background: linear-gradient(rgba(0, 0, 0, 0.12), rgba(0, 0, 0, 0.12)),
            var(--background-color);
        }

        /* Some mobile browsers treat focused element as hovered element.
        As a result, element remains hovered after click (has grey background in default theme).
        Use @media (hover:none) to remove background if
        user's primary input mechanism can't hover over elements.
        See: https://developer.mozilla.org/en-US/docs/Web/CSS/@media/hover

        Note 1: not all browsers support this media query
        (see https://caniuse.com/#feat=css-media-interaction).
        If browser doesn't support it, then the whole content of @media .. is ignored.
        This is why the default behavior is placed outside of @media.
        */
        @media (hover: none) {
          paper-button:hover {
            background: transparent;
          }
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

        /* Styles for link buttons specifically */
        :host([link]) {
          --background-color: transparent;
          --margin: 0;
        }
        :host([link]) paper-button {
          padding: var(--gr-button-padding, var(--spacing-s));
        }
        :host([disabled][link]),
        :host([loading][link]) {
          --background-color: transparent;
          --text-color: var(--deemphasized-text-color);
          cursor: default;
        }

        /* Styles for the optional down arrow */
        :host(:not([down-arrow])) .downArrow {
          display: none;
        }
        :host([down-arrow]) .downArrow {
          border-top: 0.36em solid #ccc;
          border-left: 0.36em solid transparent;
          border-right: 0.36em solid transparent;
          margin-bottom: var(--spacing-xxs);
          margin-left: var(--spacing-m);
          transition: border-top-color 200ms;
        }
        :host([down-arrow]) paper-button:hover .downArrow {
          border-top-color: var(--deemphasized-text-color);
        }
        .newVoteChip {
          border: 1px solid var(--border-color);
          box-shadow: none;
          box-sizing: border-box;
          min-width: 3em;
          color: var(--vote-text-color);
        }
      `,
    ];
  }

  private readonly flagsService = getAppContext().flagsService;

  private readonly isSubmitRequirementsUiEnabled = this.flagsService.isEnabled(
    KnownExperimentId.SUBMIT_REQUIREMENTS_UI
  );

  override render() {
    return html`<paper-button
      ?raised=${!this.link && !this.flatten}
      ?disabled=${this.disabled || this.loading}
      role="button"
      tabindex="-1"
      part="paper-button"
      class=${classMap({
        voteChip: this.voteChip && !this.isSubmitRequirementsUiEnabled,
        newVoteChip: this.voteChip && this.isSubmitRequirementsUiEnabled,
      })}
    >
      ${this.loading ? html`<span class="loadingSpin"></span>` : ''}
      <slot></slot>
      <i class="downArrow"></i>
    </paper-button>`;
  }

  constructor() {
    super();
    this.initialTabindex = this.getAttribute('tabindex') || '0';
    this.addEventListener('click', e => this._handleAction(e));
    addShortcut(this, {key: Key.ENTER}, () => this.click());
    addShortcut(this, {key: Key.SPACE}, () => this.click());
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

  _handleAction(e: MouseEvent) {
    if (this.disabled || this.loading) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      return;
    }

    this.reporting.reportInteraction('button-click', {path: getEventPath(e)});
  }
}
