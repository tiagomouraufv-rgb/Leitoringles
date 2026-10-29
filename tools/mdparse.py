"""Parse the simple markdown used in books-src into chapters and paragraphs.

Rules:
- "# Title" first line: book title (ignored here).
- "## Heading": starts a new chapter.
- A paragraph that is entirely **bold** becomes a section heading (style "h").
- "> text": quote (style "q").
- "- text": list item (style "li").
- Everything else: normal paragraph (style "p").
- Inline **bold** and *italic* markers are removed.
"""
import re
import glob
import os


def clean_inline(t):
    t = re.sub(r"\*\*(.+?)\*\*", r"\1", t)
    t = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"\1", t)
    t = t.replace("*", "")
    return t.strip()


def parse_book_dir(path):
    files = sorted(glob.glob(os.path.join(path, "*.md")))
    text = "\n\n".join(open(f, encoding="utf-8").read() for f in files)
    return parse_text(text)


def parse_text(text):
    chapters = []
    cur = None
    blocks = re.split(r"\n\s*\n", text)
    for b in blocks:
        b = b.strip()
        if not b:
            continue
        if b.startswith("# ") and not b.startswith("## "):
            continue
        lines = b.split("\n")
        # a block may start with a heading followed by text lines
        while lines and lines[0].startswith("## "):
            cur = {"title": clean_inline(lines[0][3:]), "paragraphs": []}
            chapters.append(cur)
            lines = lines[1:]
        if not lines:
            continue
        if cur is None:
            cur = {"title": "", "paragraphs": []}
            chapters.append(cur)
        if all(l.startswith("- ") for l in lines):
            for l in lines:
                cur["paragraphs"].append({"t": clean_inline(l[2:]), "s": "li"})
            continue
        if all(l.startswith(">") for l in lines):
            t = " ".join(l.lstrip(">").strip() for l in lines)
            cur["paragraphs"].append({"t": clean_inline(t), "s": "q"})
            continue
        raw = " ".join(l.strip() for l in lines)
        if re.fullmatch(r"\*\*[^*]+\*\*", raw):
            cur["paragraphs"].append({"t": clean_inline(raw), "s": "h"})
            continue
        cur["paragraphs"].append({"t": clean_inline(raw), "s": "p"})
    return chapters


if __name__ == "__main__":
    import sys
    ch = parse_book_dir(sys.argv[1])
    for c in ch:
        print(c["title"], len(c["paragraphs"]))
