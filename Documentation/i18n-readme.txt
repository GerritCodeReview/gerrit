Gerrit Code Review - i18n
=========================

Aside from actually writing translations, there's some issues with
the way the code produces output.  Most of the UI should support
right-to-left (RTL) languages.


ApprovalCategory
----------------

The getName() function produces only a single translation of the
description string.  This name is set by the Gerrit administrator,
which may cause problems if the site is translated into multiple
languages and different users want different translations.

ApprovalCategoryValue
---------------------

The getName() function produces only a single translation of the
description string.  This name is set by the Gerrit administrator,
which may cause problems if the site is translated into multiple
languages and different users want different translations.

/Gerrit Gerrit.html
-------------------

* The title of the host page is not translated.

* The <noscript> tag is not translated.

GERRIT
------
Part of link:index.html[Gerrit Code Review]
