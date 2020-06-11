import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrDropdown} from '../../../../elements/shared/gr-dropdown/gr-dropdown';

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

export class GrDropdownCheck extends GrDropdown
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = this.link;
      el.setAttribute('class', `dropdown-trigger`);
      el.setAttribute('id', `trigger`);
      el.downArrow = this.downArrow;
      el.addEventListener('click', this._dropdownTriggerTapHandler.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['slot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-dropdown'] = null!;
      useVars(el);
      el.setAttribute('id', `dropdown`);
      el.verticalOffset = this.verticalOffset;
      el.horizontalAlign = this.horizontalAlign;
      el.addEventListener('click', this._handleDropdownClick.bind(this));
      el.addEventListener('opened-changed', this.handleOpenedChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `dropdown-content`);
    }
    {
      const el: HTMLElementTagNameMap['ul'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this.topContent)
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `topContent`);
      }
      {
        const el: HTMLElementTagNameMap['dom-repeat'] = null!;
        useVars(el);
      }
      {
        const index = 0;
        const itemsIndexAs = 0;
        useVars(index, itemsIndexAs);
        for(const item of this.topContent!)
        {
          {
            const el: HTMLElementTagNameMap['div'] = null!;
            useVars(el);
            el.setAttribute('class', `${this._getClassIfBold(__f(item)!.bold)} top-item`);
          }
          setTextContent(`
                ${__f(item)!.text}
              `);

        }
      }
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const link of this.items!)
      {
        {
          const el: HTMLElementTagNameMap['li'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-tooltip-content'] = null!;
          useVars(el);
          el.hasTooltip = this._computeHasTooltip(__f(link)!.tooltip);
          el.setAttribute('title', `${__f(link)!.tooltip}`);
        }
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
          el.setAttribute('class', `itemAction ${this._computeDisabledClass(pc(this.disabledIds), __f(link)!.id)}`);
          el.setAttribute('dataId', `${__f(link)!.id}`);
          el.addEventListener('click', e => this._handleItemTap.bind(this, wrapInPolymerDomRepeatEvent(e, link))());
          el.setAttribute('hidden', `${__f(link)!.url}`);
        }
        setTextContent(`${__f(link)!.name}`);

        {
          const el: HTMLElementTagNameMap['a'] = null!;
          useVars(el);
          el.setAttribute('class', `itemAction`);
          el.setAttribute('href', `${this._computeLinkURL(link)}`);
          el.setAttribute('download', `${this._computeIsDownload(link)}`);
          el.setAttribute('rel', `${this._computeLinkRel(link)}`);
          el.setAttribute('target', `${__f(link)!.target}`);
          el.setAttribute('hidden', `${!__f(link)!.url}`);
        }
        setTextContent(`${__f(link)!.name}`);

      }
    }
  }
}

