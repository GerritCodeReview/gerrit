(() => {
  'use strict';

  class ThemeHeader extends Polymer.Element {
    static get is() {
      return 'gerrit-theme-header';
    }

    static get template() {
      return Polymer.html`
      <style include="shared-styles"></style>
      <style>
        .google-header {
          display: flex;
          align-items: center;
          flex-wrap: wrap;
          line-height: .9em;
          font-size: .8em;
          color: var(--header-text-color);
        }
        .title {
          font-size: .7em;
        }
        img {
          margin-right: 5px;
          height: auto;
          max-height: 2em;
          max-width: 100%;
        }
      </style>

      <div class="google-header">
        <img src="/static/android-partner.png"
           alt="Android Partner Logo"/>
        <span class="google-name">
          <div class="title">Android</div>
          Partner Code Review
        </span>
      </div>`;
    }

    getSiteName() {
      return document.location.host.split('-review')[0];
    }
  }

  customElements.define(ThemeHeader.is, ThemeHeader);


  class ThemeHeaderSource extends Polymer.Element {
    static get is() {
      return 'gerrit-theme-header-source';
    }

    static get template() {
      return Polymer.html`
        <style include="shared-styles">
        a {
          color: var(--header-text-color);
        }
        </style>
        <a href="[[getGitilesUrl()]]">Repositories</a>`;
    }

    getGitilesUrl() {
      return '//' + document.location.host.replace('-review', '');
    }
  }

  customElements.define(ThemeHeaderSource.is, ThemeHeaderSource);

  Gerrit.install(plugin => {
    plugin.registerCustomComponent(
        'header-title', ThemeHeader.is, {replace: true});
    plugin.registerCustomComponent(
        'header-browse-source', ThemeHeaderSource.is, {replace: true});

    plugin.styleApi().insertCSSRule(`
      html.lightTheme {
        --header-background-color: #ffb3b3;
        --header-text-color: #000;
      }
    `);
    plugin.styleApi().insertCSSRule(`
      html.darkTheme {
        --header-background-color: #ffb3b390;
        --header-text-color: #000;
      }
    `);
  });
})();
