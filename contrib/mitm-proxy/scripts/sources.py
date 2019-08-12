import os
import zipfile
import datetime
import math

from mitmproxy import ctx
from pathlib import Path

class EmptyContentSource:
  def get_content_if_modified(self, relative_path: str, if_modified_since = None):
    return ("", None)

class FileSystemSource:
  def __init__(self, base_path):
    if isinstance(base_path, str):
      base_path = Path(base_path)
    self._base_path = base_path

  def get_content_if_modified(self, relative_path: str, if_modified_since = None):
    if relative_path.startswith('/'):
      relative_path = relative_path[1:]
    full_path = self._base_path.joinpath(relative_path).expanduser()
    ctx.log.info(str(full_path))
    last_modified = datetime.datetime.fromtimestamp(math.ceil(os.path.getmtime(full_path)))
    if if_modified_since and last_modified <= if_modified_since:
      return (None, last_modified)
    with open(full_path, mode='rb') as f:
      return (f.read(), last_modified)

class ZipFileSource:
  def __init__(self, zip_file_path, base_path=None):
    if isinstance(zip_file_path, str):
      zip_file_path = Path(zip_file_path)
    if base_path and isinstance(base_path, str):
      base_path = Path(base_path)

    str_path = str(zip_file_path.resolve())
    self._zip_file = zipfile.ZipFile(str_path, 'r')
    self._base_path = base_path

  def get_content_if_modified(self, relative_path: str, if_modified_since = None):
    if not self._zip_file:
      return ("", None)
    full_path = self._base_path.joinpath(relative_path) if self._base_path else relative_path
    ctx.log.info(str(full_path))
    return (self._zip_file.read(str(full_path)), None)
