import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrMainHeader} from '../../../../elements/core/gr-main-header/gr-main-header';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrMainHeaderCheck extends GrMainHeader
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['nav'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._computeRelativeURL('/')}`);
      el.setAttribute('class', `bigTitle`);
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.name = `header-title`;
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `titleText`);
    }
    {
      const el: HTMLElementTagNameMap['ul'] = null!;
      useVars(el);
      el.setAttribute('class', `links`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const linkGroup of this._links!)
      {
        {
          const el: HTMLElementTagNameMap['li'] = null!;
          useVars(el);
          el.setAttribute('class', `${this._computeLinkGroupClass(linkGroup)}`);
        }
        {
          const el: HTMLElementTagNameMap['gr-dropdown'] = null!;
          useVars(el);
          el.link = true;
          el.downArrow = true;
          el.items = __f(linkGroup)!.links;
          el.horizontalAlign = `left`;
        }
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
          el.setAttribute('class', `linksTitle`);
          el.setAttribute('id', `${__f(linkGroup)!.title}`);
        }
        setTextContent(`
              ${__f(linkGroup)!.title}
            `);

      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `rightItems`);
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.setAttribute('class', `hideOnMobile`);
      el.name = `header-small-banner`;
    }
    {
      const el: HTMLElementTagNameMap['gr-smart-search'] = null!;
      useVars(el);
      el.setAttribute('id', `search`);
      el.label = `Search for changes`;
      el.searchQuery = this.searchQuery;
      this.searchQuery = el.searchQuery;
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.setAttribute('class', `hideOnMobile`);
      el.name = `header-browse-source`;
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._feedbackURL)
    {
      {
        const el: HTMLElementTagNameMap['a'] = null!;
        useVars(el);
        el.setAttribute('class', `feedbackButton`);
        el.setAttribute('href', `${this._feedbackURL}`);
      }
      {
        const el: HTMLElementTagNameMap['iron-icon'] = null!;
        useVars(el);
      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `accountContainer`);
      el.setAttribute('id', `accountContainer`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('id', `mobileSearch`);
      el.addEventListener('click', this._onMobileSearchTap.bind(this));
      el.ariaLabel = this._computeShowHideAriaLabel(this.mobileSearchHidden);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `registerDiv`);
      el.hidden = this._computeRegisterHidden(this._registerURL);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('class', `registerButton`);
      el.setAttribute('href', `${this._registerURL}`);
    }
    setTextContent(`
            ${this._registerText}
          `);

    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('class', `loginButton`);
      el.setAttribute('href', `${this.loginUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('class', `settingsButton`);
      el.setAttribute('href', `${this._generateSettingsLink()}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._account)
    {
      {
        const el: HTMLElementTagNameMap['gr-account-dropdown'] = null!;
        useVars(el);
        el.account = this._account;
      }
    }
  }
}

