/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    .chip {
      border-radius: var(--border-radius);
      background-color: var(--chip-background-color);
      padding: 0 var(--spacing-m);
      white-space: nowrap;
    }
    :host(.merged) .chip {
      background-color: var(--status-merged);
      color: var(--status-merged);
    }
    :host(.abandoned) .chip {
      background-color: var(--status-abandoned);
      color: var(--status-abandoned);
    }
    :host(.wip) .chip {
      background-color: var(--status-wip);
      color: var(--status-wip);
    }
    :host(.private) .chip {
      background-color: var(--status-private);
      color: var(--status-private);
    }
    :host(.merge-conflict) .chip {
      background-color: var(--status-conflict);
      color: var(--status-conflict);
    }
    :host(.active) .chip {
      background-color: var(--status-active);
      color: var(--status-active);
    }
    :host(.ready-to-submit) .chip {
      background-color: var(--status-ready);
      color: var(--status-ready);
    }
    :host(.revert-created) .chip {
      background-color: var(--status-revert-created);
      color: var(--status-revert-created);
    }
    :host(.revert-submitted) .chip {
      background-color: var(--status-revert-created);
      color: var(--status-revert-created);
    }
    .status-link {
      text-decoration: none;
    }
    :host(.custom) .chip {
      background-color: var(--status-custom);
      color: var(--status-custom);
    }
    :host([flat]) .chip {
      background-color: transparent;
      padding: 0;
    }
    :host(:not([flat])) .chip, .icon {
      color: var(--status-text-color);
    }
    .icon {
      --iron-icon-height: 18px;
      --iron-icon-width: 18px;
    }
  </style>
  <gr-tooltip-content
    has-tooltip
    position-below
    title="[[tooltipText]]"
    max-width="40em"
  >
    <template
      is="dom-if"
      if="[[hasStatusLink(revertedChange, resolveWeblinks, status)]]">
      <a class="status-link"
         href="[[getStatusLink(revertedChange, resolveWeblinks, status)]]">
        <div class="chip" aria-label$="Label: [[status]]">
          [[_computeStatusString(status)]]
          <iron-icon
            class="icon"
            icon="gr-icons:edit"
            hidden$="[[!showResolveIcon(resolveWeblinks, status)]]">
          </iron-icon>
        </div>
      </a>
    </template>
    <template is="dom-if" if="[[!hasStatusLink(revertedChange, resolveWeblinks, status)]]">
      <div class="chip" aria-label$="Label: [[status]]"
      >[[_computeStatusString(status)]]</div>
    </template>
  </gr-tooltip-content>
</span>
`;
