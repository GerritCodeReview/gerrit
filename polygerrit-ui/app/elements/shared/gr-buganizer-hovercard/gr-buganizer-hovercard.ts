/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

@customElement('gr-buganizer-hovercard')
export class GrBuganizerHovercard extends LitElement {
  @property({type: String})
  issueNumber = '';

  @property({type: String})
  match = '';

  static override get styles() {
    return [
      css`
        a {
            color: var(--link-color);
        }
        .hovercard {
          display: none; /* Hidden by default */
          position: absolute;
          top: 50%; /* Position vertically centered */
          left: 100%; /* Position to the right of the link */
          transform: translateY(-50%); /* Adjust vertical position for centering */
          background-color: white;
          border: 1px solid #ccc;
          padding: 20px;
          z-index: 10; /* Ensure hovercard is above other content */
          width: 300px;
        }
        .hovercard-content {
          display: flex;
          flex-direction: column;
          flex-wrap: wrap;
          justify-content: space-between;
        }

        .hovercard-item {
          display: flex;
          align-items: baseline; /* Align text to the bottom */
          margin-bottom: 5px; /* Add spacing between items */
          flex-shrink: 0;
        }

        .hovercard-item strong {
          margin-right: 5px; /* Add spacing after the label */
        }
        .link-wrapper {
          position: relative; /* Allow positioning hovercard relative to this */
          display: inline-block; /* So hovercard wraps correctly */
          white-space: nowrap;
        }
        .link-wrapper:hover .hovercard {
          display: block; /* Show on hover */
        }

      `,
    ];
  }

  override render() {
    return this.renderHovercardContent();
  }

  private renderHovercardContent() {
    const title = 'This is title';
    const component = 'x>y';
    const status = 'assigned';
    const assignee = 'nihardamar';
    const type = 'bug';
    console.log("entered buganizer hovercard");
    console.log(this.match);
    console.log(this.issueNumber);


    return html`<span class="link-wrapper"><a href="https://issuetracker.google.com/${this.issueNumber}" rel="noopener noreferrer" target="_blank">b/${this.issueNumber}</a>
      <div class="hovercard">
        <div class="hovercard-content">
          <div class="hovercard-item"><strong>This is title:</strong> ${title}</div>
          <div class="hovercard-item"><strong>component:</strong> ${component}</div>
          <div class="hovercard-item"><strong>status:</strong> ${status}</div>
          <div class="hovercard-item"><strong>assignee:</strong> ${assignee}</div>
          <div class="hovercard-item"><strong>type:</strong> ${type}</div>
        </div>
      </div>
      </span>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-buganizer-hovercard': GrBuganizerHovercard;
  }
}
