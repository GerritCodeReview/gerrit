import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrGroupAuditLog} from '../../../../elements/admin/gr-group-audit-log/gr-group-audit-log';

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

export class GrGroupAuditLogCheck extends GrGroupAuditLog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
      el.setAttribute('id', `list`);
      el.setAttribute('class', `genericList`);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
      el.setAttribute('class', `headerRow`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `date topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `type topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `member topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `by-user topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
      el.setAttribute('id', `loading`);
      el.setAttribute('class', `loadingMsg ${this.computeLoadingClass(this._loading)}`);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
      el.setAttribute('class', `${this.computeLoadingClass(this._loading)}`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._auditLog!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
          el.setAttribute('class', `table`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `date`);
        }
        {
          const el: HTMLElementTagNameMap['gr-date-formatter'] = null!;
          useVars(el);
          el.hasTooltip = true;
          el.dateStr = __f(item)!.date;
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `type`);
        }
        setTextContent(`${this.itemType(__f(item)!.type)}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `member`);
        }
        {
          const el: HTMLElementTagNameMap['dom-if'] = null!;
          useVars(el);
        }
        if (this._isGroupEvent(item))
        {
          {
            const el: HTMLElementTagNameMap['a'] = null!;
            useVars(el);
            el.setAttribute('href', `${this._computeGroupUrl(__f(item)!.member)}`);
          }
          setTextContent(`
                ${this._getNameForGroup(__f(item)!.member)}
              `);

        }
        {
          const el: HTMLElementTagNameMap['dom-if'] = null!;
          useVars(el);
        }
        if (!this._isGroupEvent(item))
        {
          {
            const el: HTMLElementTagNameMap['gr-account-link'] = null!;
            useVars(el);
            el.account = __f(item)!.member;
          }
          setTextContent(`
              ${this._getIdForUser(__f(item)!.member)}
            `);

        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `by-user`);
        }
        {
          const el: HTMLElementTagNameMap['gr-account-link'] = null!;
          useVars(el);
          el.account = __f(item)!.user;
        }
        setTextContent(`
            ${this._getIdForUser(__f(item)!.user)}
          `);

      }
    }
  }
}

