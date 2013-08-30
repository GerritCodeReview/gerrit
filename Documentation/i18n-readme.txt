Gerrit Code Review - i18n
=========================

Aside from actually writing translations, there are some issues with
the way the code produces output.  Most of the UI should support
right-to-left (RTL) languages.

Labels
------

Labels and their values are defined in project.config by the Gerrit
administrator or project owners.  Only a single translation of these
strings is supported.

/Gerrit Gerrit.html
-------------------

* The title of the host page is not translated.

* The <noscript> tag is not translated.

++++
<hr style="
  height: 2px;
  color: silver;
  margin-top: 1.2em;
  margin-bottom: 0.5em;
">
++++
Part of link:index.html[Gerrit Code Review]
