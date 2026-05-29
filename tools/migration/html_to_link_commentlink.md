# Overview

**Raw html substitution will no longer be an option for comment links.**

The raw-html option for commentlink sections is deprecated and removed.
Example:

```
[commentlink "issue b/"]
  match = (^|\\s)b/(\\d+)
  html = $1<a href=\"http://b/issue?id=$2&query=$2\" target=\"_blank\">b/$2</a>
```

Before it allowed to find and replace text matches in commit messages and
comments with arbitrary html. When misconfigured this has in the past enabled
injecting undesired html code and XSS attacks by writing a comment.

Even though the sanitization of the resulting html has improved. This feature is
more powerful than needed. In almost all cases across host configurations html
is only used to either configure text of the link, or limit the link to wrap
only a portion of the matched text.

To fill the gap in functionality from deprecating the option additional optional
parameters (prefix, suffix and text) have been added. They allow to generate
links that look like:
```
  PREFIX<a href="LINK">TEXT</a>SUFFIX
```
With substitution being strictly plaintext and all html escaped.

The comment link section in project configs (in refs/meta/config) never
supported the raw-html option and don't need to be updated.

# Config migration command

```
CONFIG_FILE=<path to gerrit.config file>
perl -0pe 's/([ \t]*)html\s*=\s*\"(.*)<a.* href=(?:\\\"(\S+)\\\"|(\S+)(?=\s|>))(?: .*)?>(.*)<\/a>(.*)(?<!\\)\"/$1link = \"$3$4\"\n$1prefix = \"$2\"\n$1text = \"$5\"\n$1suffix = \"$6\"/g' $CONFIG_FILE |
perl -0pe 's/([ \t]*)html\s*=\s*(\S.*)?<a.* href=(?:\\\"(\S+)\\\"|(\S+)(?=\s|>))(?: .*)?>(.*)<\/a>(.*\S)?/$1link = \"$3$4\"\n$1prefix = \"$2\"\n$1text = \"$5\"\n$1suffix = \"$6\"/g' |
perl -ne 'print if !/\s*(prefix|suffix|text)\s*=\s*\"\"/'
```

The command does 3 simple string replace passes:

1. Replace `html=<value>` with quote-escaped value.
2. Replace `html=<value>` with value without quotes.
3. Remove empty `prefix`, `suffix`, `text` fields.
