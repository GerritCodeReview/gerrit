import re

class RegexTextReplacer:
  def __init__(self, pattern, replacement):
    self._pattern = re.compile(pattern)
    self._replacement = replacement

  def update_text(self, text):
    return self._pattern.sub(self._replacement, text)

