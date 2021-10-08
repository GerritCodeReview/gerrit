/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-checks-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-hovercard-styles">
    #container {
      min-width: 356px;
      max-width: 356px;
      padding: var(--spacing-xl) 0 var(--spacing-m) 0;
    }
    .row {
      display: flex;
      margin-top: var(--spacing-s);
    }
    .attempts.row {
      flex-wrap: wrap;
    }
    .chipRow {
      display: flex;
      margin-top: var(--spacing-s);
    }
    .chip {
      background: var(--gray-background);
      color: var(--gray-foreground);
      border-radius: 20px;
      padding: var(--spacing-xs) var(--spacing-m) var(--spacing-xs)
        var(--spacing-s);
    }
    .title {
      color: var(--deemphasized-text-color);
      margin-right: var(--spacing-m);
    }
    div.section {
      margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
      display: flex;
    }
    div.sectionIcon {
      flex: 0 0 30px;
    }
    div.chip iron-icon {
      width: 16px;
      height: 16px;
      /* Positioning of a 16px icon in the middle of a 20px line. */
      position: relative;
      top: 2px;
    }
    div.sectionIcon iron-icon {
      position: relative;
      top: 2px;
      width: 20px;
      height: 20px;
    }
    div.sectionIcon iron-icon.small {
      position: relative;
      top: 6px;
      width: 16px;
      height: 16px;
    }
    div.sectionContent iron-icon.link {
      color: var(--link-color);
    }
    div.sectionContent .attemptIcon iron-icon,
    div.sectionContent iron-icon.small {
      width: 16px;
      height: 16px;
      margin-right: var(--spacing-s);
      /* Positioning of a 16px icon in the middle of a 20px line. */
      position: relative;
      top: 2px;
    }
    div.sectionContent .attemptIcon iron-icon {
      margin-right: 0;
    }
    .attemptIcon,
    .attemptNumber {
      margin-right: var(--spacing-s);
      color: var(--deemphasized-text-color);
      text-align: center;
      width: 24px;
      font-size: var(--font-size-small);
    }
    div.action {
      border-top: 1px solid var(--border-color);
      margin-top: var(--spacing-m);
      padding: var(--spacing-m) var(--spacing-xl) 0;
    }
  </style>
  <div id="container" role="tooltip" tabindex="-1">
    <div class="section">
      <div hidden$="[[hideChip(run)]]" class="chipRow">
        <div class="chip">
          <iron-icon icon="gr-icons:[[computeChipIcon(run)]]"></iron-icon>
          <span>[[run.status]]</span>
        </div>
      </div>
    </div>
    <div class="section">
      <div class="sectionIcon" hidden$="[[hideHeaderSectionIcon(run)]]">
        <iron-icon
          class$="[[computeIcon(run)]]"
          icon="gr-icons:[[computeIcon(run)]]"
        ></iron-icon>
      </div>
      <div class="sectionContent">
        <h3 class="name heading-3">
          <span>[[run.checkName]]</span>
        </h3>
      </div>
    </div>
    <div class="section" hidden$="[[hideStatusSection(run)]]">
      <div class="sectionIcon">
        <iron-icon class="small" icon="gr-icons:info-outline"></iron-icon>
      </div>
      <div class="sectionContent">
        <div hidden$="[[!run.statusLink]]" class="row">
          <div class="title">Status</div>
          <div>
            <a href="[[_convertUndefined(run.statusLink)]]" target="_blank"
              ><iron-icon
                aria-label="external link to check status"
                class="small link"
                icon="gr-icons:launch"
              ></iron-icon
              >[[computeHostName(run.statusLink)]]
            </a>
          </div>
        </div>
        <div hidden$="[[!run.statusDescription]]" class="row">
          <div class="title">Message</div>
          <div>[[run.statusDescription]]</div>
        </div>
      </div>
    </div>
    <div class="section" hidden$="[[hideAttempts(run)]]">
      <div class="sectionIcon">
        <iron-icon class="small" icon="gr-icons:arrow-forward"></iron-icon>
      </div>
      <div class="sectionContent">
        <div hidden$="[[hideAttempts(run)]]" class="attempts row">
          <div class="title">Attempt</div>
          <template is="dom-repeat" items="[[computeAttempts(run)]]">
            <div>
              <div class="attemptIcon">
                <iron-icon
                  class$="[[item.icon]]"
                  icon="gr-icons:[[item.icon]]"
                ></iron-icon>
              </div>
              <div class="attemptNumber">[[computeAttempt(item.attempt)]]</div>
            </div>
          </template>
        </div>
      </div>
    </div>
    <div class="section" hidden$="[[hideTimestampSection(run)]]">
      <div class="sectionIcon">
        <iron-icon class="small" icon="gr-icons:schedule"></iron-icon>
      </div>
      <div class="sectionContent">
        <div hidden$="[[hideScheduled(run)]]" class="row">
          <div class="title">Scheduled</div>
          <div>[[computeDuration(run.scheduledTimestamp)]]</div>
        </div>
        <div hidden$="[[!run.startedTimestamp]]" class="row">
          <div class="title">Started</div>
          <div>[[computeDuration(run.startedTimestamp)]]</div>
        </div>
        <div hidden$="[[!run.finishedTimestamp]]" class="row">
          <div class="title">Ended</div>
          <div>[[computeDuration(run.finishedTimestamp)]]</div>
        </div>
        <div hidden$="[[hideCompletion(run)]]" class="row">
          <div class="title">Completion</div>
          <div>[[computeCompletionDuration(run)]]</div>
        </div>
      </div>
    </div>
    <div class="section" hidden$="[[hideDescriptionSection(run)]]">
      <div class="sectionIcon">
        <iron-icon class="small" icon="gr-icons:link"></iron-icon>
      </div>
      <div class="sectionContent">
        <div hidden$="[[!run.checkDescription]]" class="row">
          <div class="title">Description</div>
          <div>[[run.checkDescription]]</div>
        </div>
        <div hidden$="[[!run.checkLink]]" class="row">
          <div class="title">Documentation</div>
          <div>
            <a href="[[_convertUndefined(run.checkLink)]]" target="_blank"
              ><iron-icon
                aria-label="external link to check documentation"
                class="small link"
                icon="gr-icons:launch"
              ></iron-icon
              >[[computeHostName(run.checkLink)]]
            </a>
          </div>
        </div>
      </div>
    </div>
    <template is="dom-repeat" items="[[computeActions(run)]]">
      <div class="action">
        <gr-checks-action
          event-target="[[_target]]"
          action="[[item]]"
        ></gr-checks-action>
      </div>
    </template>
  </div>
`;
