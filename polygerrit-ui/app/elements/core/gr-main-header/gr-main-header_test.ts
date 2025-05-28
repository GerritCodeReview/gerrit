/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  isHidden,
  query,
  stubElement,
  stubRestApi,
} from '../../../test/test-utils';
import './gr-main-header';
import {getDocLinks, GrMainHeader} from './gr-main-header';
import {
  createAccountDetailWithId,
  createGerritInfo,
  createServerInfo,
} from '../../../test/test-data-generators';
import {NavLink} from '../../../models/views/admin';
import {ServerInfo, TopMenuItemInfo} from '../../../types/common';
import {AuthType} from '../../../constants/constants';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-main-header tests', () => {
  let element: GrMainHeader;

  setup(async () => {
    stubRestApi('probePath').returns(Promise.resolve(false));
    stubElement('gr-main-header', 'loadAccount').callsFake(() =>
      Promise.resolve()
    );
    element = await fixture(html`<gr-main-header></gr-main-header>`);
    element.loginUrl = '/login';
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <nav class="hideOnMobile">
          <a class="bigTitle" href="//localhost:9876/">
            <gr-endpoint-decorator name="header-title">
              <div class="titleText"></div>
            </gr-endpoint-decorator>
          </a>
          <ul class="links">
            <li>
              <gr-dropdown down-arrow="" horizontal-align="left" link="">
                <span class="linksTitle" id="Changes"> Changes </span>
              </gr-dropdown>
            </li>
            <li>
              <gr-dropdown down-arrow="" horizontal-align="left" link="">
                <span class="linksTitle" id="Documentation">Documentation</span>
              </gr-dropdown>
            </li>
            <li>
              <gr-dropdown down-arrow="" horizontal-align="left" link="">
                <span class="linksTitle" id="Browse"> Browse </span>
              </gr-dropdown>
            </li>
          </ul>
          <div class="rightItems">
            <gr-endpoint-decorator
              class="hideOnMobile"
              name="header-small-banner"
            >
            </gr-endpoint-decorator>
            <gr-smart-search id="search"> </gr-smart-search>
            <gr-endpoint-decorator class="hideOnMobile" name="header-top-right">
            </gr-endpoint-decorator>
            <gr-endpoint-decorator
              class="feedbackButton"
              name="header-feedback"
            >
              <a
                aria-label="File a bug"
                href=""
                rel="noopener noreferrer"
                role="button"
                hidden=""
                target="_blank"
                title="File a bug"
              >
                <gr-icon filled="" icon="bug_report"> </gr-icon>
              </a>
            </gr-endpoint-decorator>
          </div>
          <div class="accountContainer" id="accountContainer">
            <div>
              <gr-icon
                aria-label="Hide Searchbar"
                icon="search"
                id="mobileSearch"
                role="button"
              >
              </gr-icon>
            </div>
            <gr-endpoint-decorator name="auth-link">
              <a class="loginButton" href="/login"> Sign in </a>
            </gr-endpoint-decorator>
            <a
              aria-label="Settings"
              class="settingsButton"
              href="/settings/"
              role="button"
              title="Settings"
            >
              <gr-icon icon="settings" filled></gr-icon>
            </a>
          </div>
        </nav>
        <nav class="hideOnDesktop">
          <div class="nav-sidebar">
            <ul class="menu">
              <li class="has-collapsible">
                <a class="main" data-title="Changes" href="">
                  Changes
                  <gr-icon class="arrow-down" icon="arrow_drop_down"> </gr-icon>
                </a>
                <ul class="dropdown">
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Open </span>
                    <a
                      class="itemAction"
                      href="//localhost:9876/q/status:open+-is:wip"
                      tabindex="-1"
                    >
                      Open
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Merged </span>
                    <a
                      class="itemAction"
                      href="//localhost:9876/q/status:merged"
                      tabindex="-1"
                    >
                      Merged
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Abandoned </span>
                    <a
                      class="itemAction"
                      href="//localhost:9876/q/status:abandoned"
                      tabindex="-1"
                    >
                      Abandoned
                    </a>
                  </li>
                </ul>
              </li>
              <li class="has-collapsible">
                <a class="main" data-title="Documentation" href="">
                  Documentation
                  <gr-icon class="arrow-down" icon="arrow_drop_down"> </gr-icon>
                </a>
                <ul class="dropdown">
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Table of Contents </span>
                    <a
                      class="itemAction"
                      href="https://gerrit-review.googlesource.com/Documentation/index.html"
                      rel="noopener"
                      tabindex="-1"
                      target="_blank"
                    >
                      Table of Contents
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Searching </span>
                    <a
                      class="itemAction"
                      href="https://gerrit-review.googlesource.com/Documentation/user-search.html"
                      rel="noopener"
                      tabindex="-1"
                      target="_blank"
                    >
                      Searching
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Uploading </span>
                    <a
                      class="itemAction"
                      href="https://gerrit-review.googlesource.com/Documentation/user-upload.html"
                      rel="noopener"
                      tabindex="-1"
                      target="_blank"
                    >
                      Uploading
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Access Control </span>
                    <a
                      class="itemAction"
                      href="https://gerrit-review.googlesource.com/Documentation/access-control.html"
                      rel="noopener"
                      tabindex="-1"
                      target="_blank"
                    >
                      Access Control
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> REST API </span>
                    <a
                      class="itemAction"
                      href="https://gerrit-review.googlesource.com/Documentation/rest-api.html"
                      rel="noopener"
                      tabindex="-1"
                      target="_blank"
                    >
                      REST API
                    </a>
                  </li>
                  <li tabindex="-1">
                    <span hidden="" tabindex="-1"> Project Owner Guide </span>
                    <a
                      class="itemAction"
                      href="https://gerrit-review.googlesource.com/Documentation/intro-project-owner.html"
                      rel="noopener"
                      tabindex="-1"
                      target="_blank"
                    >
                      Project Owner Guide
                    </a>
                  </li>
                </ul>
              </li>
              <li class="has-collapsible">
                <a class="main" data-title="Browse" href="">
                  Browse
                  <gr-icon class="arrow-down" icon="arrow_drop_down"> </gr-icon>
                </a>
                <ul class="dropdown"></ul>
              </li>
            </ul>
          </div>
          <div class="nav-header">
            <a
              aria-label="Open hamburger"
              class="hamburger"
              href=""
              role="button"
              title="Hamburger"
            >
              <gr-icon filled="" icon="menu"> </gr-icon>
            </a>
            <a class="bigTitle mobileTitle" href="//localhost:9876/">
              <gr-endpoint-decorator name="header-mobile-title">
                <div class="mobileTitleText"></div>
              </gr-endpoint-decorator>
            </a>
            <div class="mobileRightItems">
              <a
                aria-label="Hide Searchbar"
                class="searchButton"
                role="button"
                title="Search"
              >
                <gr-icon filled="" icon="search"> </gr-icon>
              </a>
              <gr-dropdown class="moreMenu" horizontal-align="center" link="">
                <span class="linksTitle">
                  <gr-icon filled="" icon="more_horiz"> </gr-icon>
                </span>
              </gr-dropdown>
            </div>
          </div>
        </nav>
        <div class="modelBackground"></div>
      `
    );
  });

  test('link visibility', async () => {
    element.loading = true;
    await element.updateComplete;
    assert.isTrue(isHidden(query(element, '.accountContainer')));

    element.loading = false;
    element.loggedIn = false;
    await element.updateComplete;
    assert.isFalse(isHidden(query(element, '.accountContainer')));
    assert.isFalse(isHidden(query(element, '.loginButton')));
    assert.isNotOk(query(element, '.registerDiv'));
    assert.isNotOk(query(element, '.registerButton'));

    element.account = createAccountDetailWithId(1);
    await element.updateComplete;
    assert.isTrue(isHidden(query(element, 'gr-account-dropdown')));
    assert.isTrue(isHidden(query(element, '.settingsButton')));

    element.loggedIn = true;
    await element.updateComplete;
    assert.isTrue(isHidden(query(element, '.loginButton')));
    assert.isTrue(isHidden(query(element, '.registerButton')));
    assert.isFalse(isHidden(query(element, 'gr-account-dropdown')));
    assert.isFalse(isHidden(query(element, '.settingsButton')));
  });

  test('fix my menu item', () => {
    assert.deepEqual(
      [
        {url: 'https://awesometown.com/#hashyhash', name: '', target: ''},
        {url: 'url', name: '', target: '_blank'},
      ].map(element.createHeaderLink),
      [
        {url: 'https://awesometown.com/#hashyhash', name: ''},
        {url: 'url', name: ''},
      ]
    );
  });

  test('user links', () => {
    const defaultLinks = [
      {
        title: 'Faves',
        links: [
          {
            name: 'Pinterest',
            url: 'https://pinterest.com',
          },
        ],
      },
    ];
    const userLinks: TopMenuItemInfo[] = [
      {
        name: 'Facebook',
        url: 'https://facebook.com',
        target: '',
      },
    ];
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        view: undefined,
      },
    ];

    // When no admin links are passed, it should use the default.
    assert.deepEqual(
      element
        .computeLinks(
          /* userLinks= */ [],
          adminLinks,
          /* topMenus= */ [],
          defaultLinks
        )
        .find(i => i.title === 'Faves'),
      defaultLinks[0]
    );
    assert.deepEqual(
      element
        .computeLinks(userLinks, adminLinks, /* topMenus= */ [], defaultLinks)
        .find(i => i.title === 'Your'),
      {
        title: 'Your',
        links: userLinks,
      }
    );
  });

  test('documentation links', () => {
    const docLinks = [
      {
        name: 'Table of Contents',
        url: '/index.html',
      },
    ];

    assert.deepEqual(getDocLinks('', docLinks), []);
    assert.deepEqual(getDocLinks('base', []), []);

    assert.deepEqual(getDocLinks('base', docLinks), [
      {
        name: 'Table of Contents',
        target: '_blank',
        url: 'base/index.html',
      },
    ]);

    assert.deepEqual(getDocLinks('base/', docLinks), [
      {
        name: 'Table of Contents',
        target: '_blank',
        url: 'base/index.html',
      },
    ]);
  });

  test('top menus', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Plugins',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* defaultLinks= */ []
      )[2],
      {
        title: 'Plugins',
        links: [
          {
            name: 'Manage',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      }
    );
  });

  test('ignore top project menus', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Projects',
        items: [
          {
            name: 'Project Settings',
            target: '_blank',
            url: '/plugins/myplugin/${projectName}',
          },
          {
            name: 'Project List',
            target: '_blank',
            url: '/plugins/myplugin/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* defaultLinks= */ []
      )[2],
      {
        title: 'Projects',
        links: [
          {
            name: 'Project List',
            url: '/plugins/myplugin/index.html',
          },
        ],
      }
    );
  });

  test('merge top menus', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Plugins',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
      {
        name: 'Plugins',
        items: [
          {
            name: 'Create',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/create.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* defaultLinks= */ []
      )[2],
      {
        title: 'Plugins',
        links: [
          {
            name: 'Manage',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
          {
            name: 'Create',
            url: 'https://gerrit/plugins/plugin-manager/static/create.html',
          },
        ],
      }
    );
  });

  test('merge top menus in default links', () => {
    const defaultLinks = [
      {
        title: 'Faves',
        links: [
          {
            name: 'Pinterest',
            url: 'https://pinterest.com',
          },
        ],
      },
    ];
    const topMenus = [
      {
        name: 'Faves',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        /* adminLinks= */ [],
        topMenus,
        defaultLinks
      )[0],
      {
        title: 'Faves',
        links: defaultLinks[0].links.concat([
          {
            name: 'Manage',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ]),
      }
    );
  });

  test('merge top menus in user links', () => {
    const userLinks = [
      {
        name: 'Facebook',
        url: 'https://facebook.com',
        target: '',
      },
    ];
    const topMenus = [
      {
        name: 'Your',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        userLinks,
        /* adminLinks= */ [],
        topMenus,
        /* defaultLinks= */ []
      )[0],
      {
        title: 'Your',
        links: [
          {
            name: 'Facebook',
            url: 'https://facebook.com',
            target: '',
          },
          {
            name: 'Manage',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      }
    );
  });

  test('merge top menus in admin links', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Browse',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* defaultLinks= */ []
      )[1],
      {
        title: 'Browse',
        links: [
          adminLinks[0],
          {
            name: 'Manage',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      }
    );
  });

  test('shows feedback icon when URL provided', async () => {
    assert.isEmpty(element.feedbackURL);
    const feedbackButton = query<HTMLAnchorElement>(
      element,
      '.feedbackButton > a'
    );
    assert.ok(feedbackButton);
    assert.isTrue(isHidden(feedbackButton));

    const url = 'report_bug_url';
    const config: ServerInfo = {
      ...createServerInfo(),
      gerrit: {
        ...createGerritInfo(),
        report_bug_url: url,
      },
    };
    element.retrieveFeedbackURL(config);
    await element.updateComplete;

    assert.equal(element.feedbackURL, url);
    const updatedFeedbackButton = query<HTMLAnchorElement>(
      element,
      '.feedbackButton > a'
    );
    assert.ok(updatedFeedbackButton);
    assert.equal(updatedFeedbackButton.style.display, '');
  });

  test('register URL', async () => {
    assert.isTrue(isHidden(query(element, '.registerDiv')));
    const config: ServerInfo = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.LDAP,
        register_url: 'https//gerrit.example.com/register',
        editable_account_fields: [],
      },
    };
    element.retrieveRegisterURL(config);
    await element.updateComplete;
    assert.equal(element.registerURL, config.auth.register_url);
    assert.equal(element.registerText, 'Sign up');
    assert.isFalse(isHidden(query(element, '.registerDiv')));

    config.auth.register_text = 'Create account';
    element.retrieveRegisterURL(config);
    await element.updateComplete;
    assert.equal(element.registerURL, config.auth.register_url);
    assert.equal(element.registerText, config.auth.register_text);
    assert.isFalse(isHidden(query(element, '.registerDiv')));
  });

  test('register URL ignored for wrong auth type', async () => {
    const config: ServerInfo = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.OPENID,
        register_url: 'https//gerrit.example.com/register',
        editable_account_fields: [],
      },
    };
    element.retrieveRegisterURL(config);
    await element.updateComplete;
    assert.equal(element.registerURL, '');
    assert.equal(element.registerText, 'Sign up');
    assert.isTrue(isHidden(query(element, '.registerDiv')));
  });
});
