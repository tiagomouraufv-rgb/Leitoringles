"""Sentence splitting and sentence-translation files.

Translation files live in books-src/<book>/pt/NN.txt (NN = chapter index, 00 = first
chapter of the parsed book). Format: one block per paragraph, blocks separated by a
blank line; inside a block, one line per sentence, in the same order as the English.

Usage:
  python3 tools/sentences.py export <book>        -> build/sent/<book>/NN.txt (English, same layout)
  python3 tools/sentences.py check <book>         -> reports chapters whose translation does not align
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(__file__))

ABBR = {"Mr", "Mrs", "Dr", "St", "Prof", "Mt", "No", "Capt", "Mme", "Mlle", "M", "Messrs", "Jr", "Sr", "vs", "etc"}
SPLIT = re.compile(r"[.!?…]+[\"”’')\]]*(?=\s+[\"“‘'(\[—–]*[A-Z0-9])")


def split(text):
    """Returns a list of (start, end) sentence ranges covering the paragraph."""
    out = []
    start = 0
    for m in SPLIT.finditer(text):
        end = m.end()
        # skip abbreviations like "Mr." and single initials like "J."
        word = re.search(r"([A-Za-z]+)\.$", text[start:m.start() + 1])
        if text[m.start()] == "." and word and (word.group(1) in ABBR or (len(word.group(1)) == 1 and word.group(1).isupper())):
            continue
        if text[start:end].strip():
            out.append((start, end))
        start = end
        while start < len(text) and text[start].isspace():
            start += 1
    if text[start:].strip():
        out.append((start, len(text.rstrip())))
    return out


def chapters_of(book):
    from mdparse import parse_book_dir
    return parse_book_dir(os.path.join(ROOT, "books-src", book))


def pt_path(book, ci):
    return os.path.join(ROOT, "books-src", book, "pt", "%02d.txt" % ci)


def read_pt(book, ci):
    p = pt_path(book, ci)
    if not os.path.exists(p):
        return None
    blocks = [b for b in re.split(r"\n\s*\n", open(p, encoding="utf-8").read().strip()) if b.strip()]
    return [[l.strip() for l in b.split("\n") if l.strip()] for b in blocks]


def align(book, ci, ch):
    """Returns per-paragraph lists of [start, end, pt], or None; raises ValueError on mismatch."""
    pt = read_pt(book, ci)
    if pt is None:
        return None
    paras = ch["paragraphs"]
    if len(pt) != len(paras):
        raise ValueError("%s ch%02d: %d paragraphs in English, %d in translation" % (book, ci, len(paras), len(pt)))
    out = []
    for pi, (p, tr) in enumerate(zip(paras, pt)):
        ss = split(p["t"])
        if len(ss) != len(tr):
            raise ValueError("%s ch%02d p%d: %d sentences in English, %d in translation\n  EN: %s\n  PT: %s" % (
                book, ci, pi, len(ss), len(tr), " | ".join(p["t"][a:b] for a, b in ss), " | ".join(tr)))
        out.append([[a, b, t] for (a, b), t in zip(ss, tr)])
    return out


def export(book):
    d = os.path.join(ROOT, "build", "sent", book)
    os.makedirs(d, exist_ok=True)
    for ci, ch in enumerate(chapters_of(book)):
        blocks = []
        for p in ch["paragraphs"]:
            blocks.append("\n".join(p["t"][a:b] for a, b in split(p["t"])))
        with open(os.path.join(d, "%02d.txt" % ci), "w", encoding="utf-8") as f:
            f.write("\n\n".join(blocks) + "\n")
    print("exported", book, len(chapters_of(book)), "chapters to", d)


def check(book):
    ok = missing = 0
    for ci, ch in enumerate(chapters_of(book)):
        try:
            r = align(book, ci, ch)
            if r is None:
                missing += 1
            else:
                ok += 1
        except ValueError as e:
            print(e)
    print(book, "aligned", ok, "missing", missing)


if __name__ == "__main__":
    cmd, book = sys.argv[1], sys.argv[2]
    {"export": export, "check": check}[cmd](book)
