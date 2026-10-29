"""List lemmas and proper nouns of a book that still have no translation.

Usage: python3 tools/missing_words.py <book-id>
Run tools/analyze.py first. Add the missing words to tools/lexicon/en-pt-*.txt
(lemma|Portuguese|simple English definition) and names to tools/lexicon/names.txt
(Name|Portuguese description|Wikipedia search term).
"""
import json, os, sys, glob, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEX = os.path.join(ROOT, "tools", "lexicon")


def keys(pattern):
    s = set()
    for f in glob.glob(os.path.join(LEX, pattern)):
        for line in open(f, encoding="utf-8"):
            if line.strip():
                s.add(line.split("|")[0])
    return s


def main(book):
    words = keys("en-pt-*.txt")
    names = keys("names.txt")
    d = json.load(open(os.path.join(ROOT, "build", "analysis", book + ".json")))
    miss_w, miss_n = collections.Counter(), collections.Counter()
    for ch in d["tokens"]:
        for p in ch:
            for t in p:
                text, lemma, pos = t[2], t[3], t[4]
                if pos in ("PUNCT", "SPACE", "NUM", "SYM", "X") or not any(c.isalpha() for c in text):
                    continue
                if pos == "PROPN" and text[:1].isupper():
                    if text not in names and lemma.lower() not in words:
                        miss_n[text] += 1
                elif lemma.lower() not in words and text.lower() not in words:
                    miss_w[lemma.lower()] += 1
    print("# Missing words (%d):" % len(miss_w))
    print(" ".join(sorted(miss_w)))
    print("# Missing names (%d):" % len(miss_n))
    print(" ".join(sorted(miss_n, key=lambda k: -miss_n[k])))


if __name__ == "__main__":
    main(sys.argv[1])
