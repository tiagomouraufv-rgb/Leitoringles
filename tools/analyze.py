"""Run spaCy over all books and cache token analyses.

Output: build/analysis/<book>.json with, per chapter/paragraph, a list of tokens:
[start, end, text, lemma, pos, tag, dep, head_index, ent_type, morph]
"""
import json, os, sys
import spacy
sys.path.insert(0, os.path.dirname(__file__))
from mdparse import parse_book_dir

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def analyze(book):
    nlp = spacy.load("en_core_web_sm")
    chapters = parse_book_dir(os.path.join(ROOT, "books-src", book))
    out = []
    for ci, ch in enumerate(chapters):
        paras = []
        texts = [p["t"] for p in ch["paragraphs"]]
        for doc in nlp.pipe(texts, batch_size=64):
            toks = []
            for t in doc:
                toks.append([t.idx, t.idx + len(t.text), t.text, t.lemma_, t.pos_, t.tag_, t.dep_,
                             t.head.i, t.ent_type_, str(t.morph), t.sent.start])
            paras.append(toks)
        out.append(paras)
    os.makedirs(os.path.join(ROOT, "build", "analysis"), exist_ok=True)
    with open(os.path.join(ROOT, "build", "analysis", book + ".json"), "w") as f:
        json.dump({"chapters": chapters, "tokens": out}, f)


if __name__ == "__main__":
    for b in sys.argv[1:]:
        analyze(b)
        print("analyzed", b)
