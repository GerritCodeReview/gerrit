import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrIncludedInDialog} from '../../../../elements/change/gr-included-in-dialog/gr-included-in-dialog';

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

export class GrIncludedInDialogCheck extends GrIncludedInDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['header'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['h1'] = null!;
      useVars(el);
      el.setAttribute('id', `title`);
      el.setAttribute('class', `heading-1`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `closeButtonContainer`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `closeButton`);
      el.link = true;
      el.addEventListener('click', this._handleCloseTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.setAttribute('id', `filterInput`);
      el.bindValue = this._filterText;
      this._filterText = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.bindValue = this._filterText;
      this._filterText = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `${this._computeLoadingClass(this._loaded)}`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const group of this._computeGroups(this._includedIn, this._filterText)!)
      {
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
        }
        setTextContent(`${__f(group)!.title}:`);

        {
          const el: HTMLElementTagNameMap['ul'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['dom-repeat'] = null!;
          useVars(el);
        }
        {
          const index = 0;
          const itemsIndexAs = 0;
          useVars(index, itemsIndexAs);
          for(const item of __f(group)!.items!)
          {
            {
              const el: HTMLElementTagNameMap['li'] = null!;
              useVars(el);
            }
            setTextContent(`${item}`);

          }
        }
      }
    }
  }
}

