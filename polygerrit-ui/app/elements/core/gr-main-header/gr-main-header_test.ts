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
                <span class="linksTitle" id="Documentation">
                  Documentation
                </span>
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
              <md-icon-button
                data-aria-label="File a bug"
                hidden=""
                href=""
                target="_blank"
                title="File a bug"
                touch-target="none"
                value=""
              >
                <md-icon aria-hidden="true" filled=""> bug_report </md-icon>
              </md-icon-button>
            </gr-endpoint-decorator>
          </div>
          <div class="accountContainer" id="accountContainer">
            <gr-endpoint-decorator name="auth-link">
              <a class="loginButton" href="/login"> Sign in </a>
            </gr-endpoint-decorator>
            <md-icon-button
              class="settingsButton"
              data-aria-label="Settings"
              href="/settings/"
              title="Settings"
              touch-target="none"
              value=""
            >
              <md-icon aria-hidden="true" filled=""> settings </md-icon>
            </md-icon-button>
          </div>
        </nav>
        <nav class="hideOnDesktop">
          <div class="nav-header">
            <md-icon-button
              data-aria-label="Open hamburger"
              touch-target="none"
              value=""
            >
              <md-icon aria-hidden="true" filled=""> menu </md-icon>
            </md-icon-button>
            <div class="mobileTitleWrapper">
              <a class="bigTitle" href="//localhost:9876/">
                <gr-endpoint-decorator name="header-mobile-title">
                  <div class="titleText"></div>
                </gr-endpoint-decorator>
              </a>
            </div>
            <div class="mobileRightItems">
              <md-icon-button
                data-aria-label="Hide Searchbar"
                title="Search"
                touch-target="none"
                value=""
              >
                <md-icon aria-hidden="true" filled=""> search </md-icon>
              </md-icon-button>
              <gr-dropdown class="moreMenu" link="">
                <span class="linksTitle">
                  <md-icon aria-hidden="true" filled=""> more_horiz </md-icon>
                </span>
              </gr-dropdown>
            </div>
          </div>
          <div class="nav-sidebar">
            <md-list aria-label="menu links">
              <md-item>
                <div slot="headline">Changes</div>
              </md-item>
              <md-list-item
                data-index="0-0"
                href="//localhost:9876/q/status:open+-is:wip"
                md-list-item=""
                tabindex="0"
                type="link"
              >
                Open
              </md-list-item>
              <md-list-item
                data-index="0-1"
                href="//localhost:9876/q/status:merged"
                md-list-item=""
                tabindex="-1"
                type="link"
              >
                Merged
              </md-list-item>
              <md-list-item
                data-index="0-2"
                href="//localhost:9876/q/status:abandoned"
                md-list-item=""
                tabindex="-1"
                type="link"
              >
                Abandoned
              </md-list-item>
              <md-divider role="separator" tabindex="-1"> </md-divider>
              <md-item>
                <div slot="headline">Documentation</div>
              </md-item>
              <md-list-item
                data-index="1-0"
                href="https://gerrit-review.googlesource.com/Documentation/index.html"
                md-list-item=""
                target="_blank"
                type="link"
              >
                Table of Contents
              </md-list-item>
              <md-list-item
                data-index="1-1"
                href="https://gerrit-review.googlesource.com/Documentation/user-search.html"
                md-list-item=""
                target="_blank"
                type="link"
              >
                Searching
              </md-list-item>
              <md-list-item
                data-index="1-2"
                href="https://gerrit-review.googlesource.com/Documentation/user-upload.html"
                md-list-item=""
                target="_blank"
                type="link"
              >
                Uploading
              </md-list-item>
              <md-list-item
                data-index="1-3"
                href="https://gerrit-review.googlesource.com/Documentation/access-control.html"
                md-list-item=""
                target="_blank"
                type="link"
              >
                Access Control
              </md-list-item>
              <md-list-item
                data-index="1-4"
                href="https://gerrit-review.googlesource.com/Documentation/rest-api.html"
                md-list-item=""
                target="_blank"
                type="link"
              >
                REST API
              </md-list-item>
              <md-list-item
                data-index="1-5"
                href="https://gerrit-review.googlesource.com/Documentation/intro-project-owner.html"
                md-list-item=""
                target="_blank"
                type="link"
              >
                Project Owner Guide
              </md-list-item>
              <md-divider role="separator" tabindex="-1"> </md-divider>
              <md-item>
                <div slot="headline">Browse</div>
              </md-item>
            </md-list>
          </div>
        </nav>
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
        {url: '#/q/is:nice', name: '', target: '_blank'},
      ].map(element.createHeaderLink),
      [
        {url: 'https://awesometown.com/#hashyhash', name: '', target: ''},
        {url: '/q/is:nice', name: '', target: '_blank'},
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
            target: '_blank',
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
            url: '/plugins/myplugin/${projectName}',
          },
          {
            name: 'Project List',
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
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
      {
        name: 'Plugins',
        items: [
          {
            name: 'Create',
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
            target: '_blank',
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
      '.feedbackButton > md-icon-button'
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
      '.feedbackButton > md-icon-button'
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
