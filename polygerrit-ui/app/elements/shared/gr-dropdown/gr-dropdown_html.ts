/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      display: inline-block;
    }
    .dropdown-trigger {
      text-decoration: none;
      width: 100%;
    }
    .dropdown-content {
      background-color: var(--dropdown-background-color);
      box-shadow: var(--elevation-level-2);
      min-width: 112px;
      max-width: 280px;
    }
    gr-button {
      vertical-align: top;
    }
    gr-avatar {
      height: 2em;
      width: 2em;
      vertical-align: middle;
    }
    gr-button[link]:focus {
      outline: 5px auto -webkit-focus-ring-color;
    }
    ul {
      list-style: none;
    }
    .topContent,
    li {
      border-bottom: 1px solid var(--border-color);
    }
    li:last-of-type {
      border: none;
    }
    li .itemAction {
      cursor: pointer;
      display: block;
      padding: var(--spacing-m) var(--spacing-l);
    }
    li .itemAction {
      color: var(--gr-dropdown-item-color);
      background-color: var(--gr-dropdown-item-background-color);
      border: var(--gr-dropdown-item-border);
      text-transform: var(--gr-dropdown-item-text-transform);
    }
    li .itemAction.disabled {
      color: var(--deemphasized-text-color);
      cursor: default;
    }
    li .itemAction:link,
    li .itemAction:visited {
      text-decoration: none;
    }
    li .itemAction:not(.disabled):hover {
      background-color: var(--hover-background-color);
    }
    li:focus,
    li.selected {
      background-color: var(--selection-background-color);
      outline: none;
    }
    li:focus .itemAction,
    li.selected .itemAction {
      background-color: transparent;
    }
    .topContent {
      display: block;
      padding: var(--spacing-m) var(--spacing-l);
      color: var(--gr-dropdown-item-color);
      background-color: var(--gr-dropdown-item-background-color);
      border: var(--gr-dropdown-item-border);
      text-transform: var(--gr-dropdown-item-text-transform);
    }
    .bold-text {
      font-weight: var(--font-weight-bold);
    }
  </style>
  <gr-button
    link="[[link]]"
    class="dropdown-trigger"
    id="trigger"
    down-arrow="[[downArrow]]"
    on-click="_dropdownTriggerTapHandler"
  >
    <slot></slot>
  </gr-button>
  <iron-dropdown
    id="dropdown"
    vertical-align="top"
    vertical-offset="[[verticalOffset]]"
    allow-outside-scroll="true"
    horizontal-align="[[horizontalAlign]]"
    on-click="_handleDropdownClick"
    on-opened-changed="handleOpenedChanged"
  >
    <div class="dropdown-content" slot="dropdown-content">
      <ul>
        <template is="dom-if" if="[[topContent]]">
          <div class="topContent">
            <template
              is="dom-repeat"
              items="[[topContent]]"
              as="item"
              initial-count="75"
            >
              <div
                class$="[[_getClassIfBold(item.bold)]] top-item"
                tabindex="-1"
              >
                [[item.text]]
              </div>
            </template>
          </div>
        </template>
        <template
          is="dom-repeat"
          items="[[items]]"
          as="link"
          initial-count="75"
        >
          <li tabindex="-1">
            <gr-tooltip-content
              has-tooltip="[[_computeHasTooltip(link.tooltip)]]"
              title$="[[link.tooltip]]"
            >
              <span
                class$="itemAction [[_computeDisabledClass(disabledIds.*, link.id)]]"
                data-id$="[[link.id]]"
                on-click="_handleItemTap"
                hidden$="[[link.url]]"
                tabindex="-1"
                >[[link.name]]</span
              >
              <a
                class="itemAction"
                href$="[[_computeLinkURL(link)]]"
                download$="[[_computeIsDownload(link)]]"
                rel$="[[_computeLinkRel(link)]]"
                target$="[[link.target]]"
                hidden$="[[!link.url]]"
                tabindex="-1"
                >[[link.name]]</a
              >
            </gr-tooltip-content>
          </li>
        </template>
      </ul>
    </div>
  </iron-dropdown>
`;
