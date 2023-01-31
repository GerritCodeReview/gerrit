/**
 * @license
 * Copyright 2023 Google LLC
 * Copyright 2012 TJ Holowaychuk <tj@vision-media.ca>
 * SPDX-License-Identifier: MIT
 */
export type PageNextCallback = () => void;

export type PageCallback = (
  context: PageContext,
  next: PageNextCallback
) => void;

export interface PageOptions {
  popstate?: boolean;
  dispatch?: boolean;
}

interface PageState {
  path?: string;
}

function decodeURIComponentString(val: string | undefined | null) {
  if (!val) return '';
  return decodeURIComponent(val.replace(/\+/g, ' '));
}

const clickEvent = document.ontouchstart ? 'touchstart' : 'click';

export class Page {
  private callbacks: PageCallback[] = [];

  private exits: PageCallback[] = [];

  private current = '';

  private base = '';

  private running = false;

  private prevPageContext?: PageContext;

  private documentLoaded = false;

  len = 0;

  setBase(base: string) {
    this.base = base;
  }

  getBase() {
    return this.base;
  }

  start(options: PageOptions) {
    if (this.running) return;
    this.running = true;

    window.document.addEventListener(clickEvent, this.clickHandler);
    window.addEventListener('load', this.loadHandler);
    if (options.popstate !== false) {
      window.addEventListener('popstate', this.popStateHandler);
    }
    if (document.readyState === 'complete') this.documentLoaded = true;

    if (options.dispatch === false) return;

    const url =
      window.location.pathname + window.location.search + window.location.hash;
    this.replace(url);
  }

  stop() {
    if (!this.running) return;
    this.current = '';
    this.len = 0;
    this.running = false;

    window.document.removeEventListener(clickEvent, this.clickHandler, false);
    window.removeEventListener('popstate', this.popStateHandler, false);
    window.removeEventListener('load', this.loadHandler, false);
  }

  show(path: string, push?: boolean) {
    const ctx = new PageContext(path, {}, this);
    const prev = this.prevPageContext;
    this.prevPageContext = ctx;
    this.current = ctx.path;
    this.dispatch(ctx, prev);
    if (false !== ctx.handled && false !== push) ctx.pushState();
  }

  // Unused as of Jan 2023, but looks simple and useful.
  back() {
    if (this.len > 0) {
      window.history.back();
      this.len--;
    } else {
      setTimeout(() => this.show(this.base));
    }
  }

  redirect(to: string) {
    setTimeout(() => this.replace(to), 0);
  }

  replace(path: string, state: PageState = {}, dispatch = true) {
    const ctx = new PageContext(path, state, this);
    const prev = this.prevPageContext;
    this.prevPageContext = ctx;
    this.current = ctx.path;
    ctx.save(); // save before dispatching, which may redirect
    if (dispatch) this.dispatch(ctx, prev);
  }

  dispatch(ctx: PageContext, prev?: PageContext) {
    let i = 0;
    let j = 0;

    const nextExit = () => {
      const fn = this.exits[j++];
      if (!fn) {
        nextEnter();
        return;
      }
      fn(prev!, nextExit);
    };

    const nextEnter = () => {
      const fn = this.callbacks[i++];

      if (ctx.path !== this.current) {
        ctx.handled = false;
        return;
      }
      if (!fn) {
        this.unhandled(ctx);
        return;
      }
      fn(ctx, nextEnter);
    };

    if (prev) {
      nextExit();
    } else {
      nextEnter();
    }
  }

  registerRoute(re: RegExp, fn: PageCallback) {
    this.callbacks.push(middleware(re, fn));
  }

  registerExit(re: RegExp, fn: PageCallback) {
    this.exits.push(middleware(re, fn));
  }

  loadHandler = () => {
    setTimeout(() => (this.documentLoaded = true), 0);
  };

  clickHandler = (e: MouseEvent | TouchEvent) => {
    if ((e as MouseEvent).button !== 0) return;
    if (e.metaKey || e.ctrlKey || e.shiftKey) return;
    if (e.defaultPrevented) return;

    let el = e.target as HTMLAnchorElement;
    const eventPath = e.composedPath();
    if (eventPath) {
      for (let i = 0; i < eventPath.length; i++) {
        const pathEl = eventPath[i] as HTMLAnchorElement;
        if (!pathEl.nodeName) continue;
        if (pathEl.nodeName.toUpperCase() !== 'A') continue;
        if (!pathEl.href) continue;

        el = pathEl;
        break;
      }
    }

    while (el && 'A' !== el.nodeName.toUpperCase())
      el = el.parentNode as HTMLAnchorElement;
    if (!el || 'A' !== el.nodeName.toUpperCase()) return;

    if (el.hasAttribute('download') || el.getAttribute('rel') === 'external')
      return;
    const link = el.getAttribute('href');
    if (samePath(el) && (el.hash || '#' === link)) return;
    if (link && link.indexOf('mailto:') > -1) return;
    if (el.target) return;
    if (!sameOrigin(el.href)) return;

    let path = el.pathname + el.search + (el.hash ?? '');
    path = path[0] !== '/' ? '/' + path : path;

    const orig = path;
    const pageBase = this.getBase();
    if (path.indexOf(pageBase) === 0) {
      path = path.substr(pageBase.length);
    }
    if (pageBase && orig === path && window.location.protocol !== 'file:') {
      return;
    }
    e.preventDefault();
    this.show(orig);
  };

  popStateHandler = () => (e: PopStateEvent) => {
    if (!this.documentLoaded) return;
    if (e.state) {
      const path = e.state.path;
      this.replace(path, e.state);
    } else {
      const loc = window.location;
      this.show(loc.pathname + loc.search + loc.hash, /* push */ false);
    }
  };

  // Unused as of Jan 2023, but looks simple and useful.
  private unhandled(ctx: PageContext) {
    if (ctx.handled) return;
    const current = window.location.pathname + window.location.search;
    if (current === ctx.canonicalPath) return;
    this.stop();
    ctx.handled = false;
    window.location.href = ctx.canonicalPath;
  }
}

function sameOrigin(href: string) {
  if (!href) return false;
  const url = new URL(href, window.location.toString());
  const loc = window.location;
  return (
    loc.protocol === url.protocol &&
    loc.hostname === url.hostname &&
    loc.port === url.port
  );
}

function samePath(url: HTMLAnchorElement) {
  const loc = window.location;
  return url.pathname === loc.pathname && url.search === loc.search;
}

function escapeRegExp(s: string) {
  return s.replace(/([.+*?=^!:${}()[\]|/\\])/g, '\\$1');
}

export class PageContext {
  /**
   * Includes everything: base, path, query and hash.
   */
  canonicalPath = '';

  /**
   * Does not include base path.
   * Does not include hash.
   * Includes query string.
   */
  path = '';

  /** Does not include hash. */
  querystring = '';

  params: Record<string, string> = {};

  hash = '';

  handled = false;

  private title = '';

  private state: PageState = {};

  constructor(path: string, state: PageState, private readonly page: Page) {
    const pageBase = page.getBase();
    if ('/' === path[0] && 0 !== path.indexOf(pageBase)) path = pageBase + path;
    const i = path.indexOf('?');

    this.canonicalPath = path;
    const re = new RegExp('^' + escapeRegExp(pageBase));
    this.path = path.replace(re, '') || '/';
    this.title = window.document.title;
    this.state = state;
    this.state.path = path;
    this.querystring =
      i !== -1 ? decodeURIComponentString(path.slice(i + 1)) : '';
    this.params = {};

    if (this.path.indexOf('#') === -1) return;
    const parts = this.path.split('#');
    this.path = parts[0];
    this.hash = decodeURIComponentString(parts[1]) || '';
    this.querystring = this.querystring.split('#')[0];
  }

  pushState() {
    this.page.len++;
    window.history.pushState(this.state, this.title, this.canonicalPath);
  }

  save() {
    window.history.replaceState(this.state, this.title, this.canonicalPath);
  }
}

function middleware(re: RegExp, fn: Function) {
  return (ctx: PageContext, next: Function) => {
    const qsIndex = ctx.path.indexOf('?');
    const pathname = qsIndex !== -1 ? ctx.path.slice(0, qsIndex) : ctx.path;
    const m = re.exec(decodeURIComponent(pathname));
    if (m) {
      delete ctx.params[0];
      for (let i = 1; i < m.length; ++i) {
        ctx.params[`${i - 1}`] = decodeURIComponentString(m[i]);
      }
      fn(ctx, next);
    } else {
      next();
    }
  };
}
