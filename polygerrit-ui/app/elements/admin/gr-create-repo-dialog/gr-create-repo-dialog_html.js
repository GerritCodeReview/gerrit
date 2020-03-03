import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
      gr-autocomplete {
        width: 20em;
      }
    </style>

    <div class="gr-form-styles">
      <div id="form">
        <section>
          <span class="title">Repository name</span>
          <iron-input autocomplete="on" bind-value="{{_repoConfig.name}}">
            <input is="iron-input" id="repoNameInput" autocomplete="on" bind-value="{{_repoConfig.name}}">
          </iron-input>
        </section>
        <section>
          <span class="title">Rights inherit from</span>
          <span class="value">
            <gr-autocomplete id="rightsInheritFromInput" text="{{_repoConfig.parent}}" query="[[_query]]" placeholder="Optional, defaults to 'All-Projects'">
            </gr-autocomplete>
          </span>
        </section>
        <section>
          <span class="title">Owner</span>
          <span class="value">
            <gr-autocomplete id="ownerInput" text="{{_repoOwner}}" value="{{_repoOwnerId}}" query="[[_queryGroups]]">
            </gr-autocomplete>
          </span>
        </section>
        <section>
          <span class="title">Create initial empty commit</span>
          <span class="value">
            <gr-select id="initialCommit" bind-value="{{_repoConfig.create_empty_commit}}">
              <select>
                <option value="false">False</option>
                <option value="true">True</option>
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Only serve as parent for other repositories</span>
          <span class="value">
            <gr-select id="parentRepo" bind-value="{{_repoConfig.permissions_only}}">
              <select>
                <option value="false">False</option>
                <option value="true">True</option>
              </select>
            </gr-select>
          </span>
        </section>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
