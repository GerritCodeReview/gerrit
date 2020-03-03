import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      .loading {
        color: var(--deemphasized-text-color);
        padding: var(--spacing-l);
      }
      gr-change-list {
        width: 100%;
      }
      gr-user-header,
      gr-repo-header {
        border-bottom: 1px solid var(--border-color);
      }
      nav {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: flex-end;
        margin-right: 20px;
      }
      nav,
      iron-icon {
        color: var(--deemphasized-text-color);
      }
      iron-icon {
        height: 1.85rem;
        margin-left: 16px;
        width: 1.85rem;
      }
      .hide {
        display: none;
      }
      @media only screen and (max-width: 50em) {
        .loading,
        .error {
          padding: 0 var(--spacing-l);
        }
      }
    </style>
    <div class="loading" hidden\$="[[!_loading]]" hidden="">Loading...</div>
    <div hidden\$="[[_loading]]" hidden="">
      <gr-repo-header repo="[[_repo]]" class\$="[[_computeHeaderClass(_repo)]]"></gr-repo-header>
      <gr-user-header user-id="[[_userId]]" show-dashboard-link="" logged-in="[[_loggedIn]]" class\$="[[_computeHeaderClass(_userId)]]"></gr-user-header>
      <gr-change-list account="[[account]]" changes="{{_changes}}" preferences="[[preferences]]" selected-index="{{viewState.selectedChangeIndex}}" show-star="[[_loggedIn]]" on-toggle-star="_handleToggleStar" on-toggle-reviewed="_handleToggleReviewed"></gr-change-list>
      <nav class\$="[[_computeNavClass(_loading)]]">
          Page [[_computePage(_offset, _changesPerPage)]]
          <a id="prevArrow" href\$="[[_computeNavLink(_query, _offset, -1, _changesPerPage)]]" class\$="[[_computePrevArrowClass(_offset)]]">
            <iron-icon icon="gr-icons:chevron-left"></iron-icon>
          </a>
          <a id="nextArrow" href\$="[[_computeNavLink(_query, _offset, 1, _changesPerPage)]]" class\$="[[_computeNextArrowClass(_changes)]]">
            <iron-icon icon="gr-icons:chevron-right"></iron-icon>
          </a>
      </nav>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
