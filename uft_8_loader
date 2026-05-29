"""
utf8_loader.py
Loads the two documents you provided, enforcing UTF-8.
"""

import xml.etree.ElementTree as ET
from pathlib import Path
import yaml

def load_xml_utf8(path):
    """Parse XML assuming UTF-8. Raises UnicodeDecodeError if file isn't UTF-8."""
    # open explicitly as utf-8 to enforce the standard
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    # ET.fromstring works on unicode string
    root = ET.fromstring(text.encode("utf-8"))
    return {
        "title": root.findtext(".//TITLE", "").strip(),
        "h1": root.findtext(".//H1", "").strip(),
        "sections": [ (h2.text or "").strip() for h2 in root.findall(".//H2") ]
    }

def load_markdown_utf8(path):
    """Read markdown frontmatter as UTF-8 and return metadata."""
    text = Path(path).read_text(encoding="utf-8")
    # simple frontmatter extraction (between first two ---)
    parts = text.split("---")
    if len(parts) >= 3:
        front = parts[1]
        meta = yaml.safe_load(front)
    else:
        meta = {}
    return {
        "title": meta.get("title"),
        "shortTitle": meta.get("shortTitle"),
        "intro": meta.get("intro", "").strip()
    }

def convert_latin1_to_utf8(src, dst):
    """Utility used in migration: read latin-1, write utf-8 with updated declaration."""
    data = Path(src).read_bytes()
    text = data.decode("iso-8859-1")
    text = text.replace('encoding="iso-8859-1"', 'encoding="UTF-8"')
    Path(dst).write_text(text, encoding="utf-8")
