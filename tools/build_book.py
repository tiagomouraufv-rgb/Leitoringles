"""Build a Leitor Inglês book file (format version 1) from books-src/<id>.

Usage: python3 tools/build_book.py <book-id> [--out DIR]

Requires: build/analysis/<id>.json (run tools/analyze.py first).
The output file is <id>.lib.json, the format described in docs/BOOK_FORMAT.md.
"""
import json, os, re, sys, glob, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEX = os.path.join(ROOT, "tools", "lexicon")

BOOKS = {
    "dracula": {"title": "Dracula", "author": "Bram Stoker", "year": 1897, "level": "B1",
                "description": "A young English lawyer travels to Transylvania to meet a mysterious count. Soon a terrible evil comes to England."},
    "moby-dick": {"title": "Moby Dick", "author": "Herman Melville", "year": 1851, "level": "B1",
                  "description": "Ishmael joins the whaling ship Pequod, whose captain, Ahab, wants only one thing: to kill the white whale that took his leg."},
    "red-and-black": {"title": "The Red and the Black", "author": "Stendhal", "year": 1830, "level": "B1",
                      "description": "Julien Sorel, a poor carpenter's son, uses his intelligence and ambition to rise in French society, between love and danger."},
}

WORD_RE = re.compile(r"[A-Za-zÀ-ÖØ-öø-ÿ]+(?:['’][A-Za-z]+)?")

POS_LABEL = {"NOUN": "noun", "VERB": "verb", "AUX": "verb", "ADJ": "adj", "ADV": "adv", "PROPN": "name",
             "PRON": "pron", "ADP": "prep", "SCONJ": "conj", "CCONJ": "conj", "DET": "det", "INTJ": "interj", "PART": "part"}


def load_tsv(name):
    d = {}
    for f in sorted(glob.glob(os.path.join(LEX, name))):
        for line in open(f, encoding="utf-8"):
            line = line.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split("|")
            while len(parts) < 3:
                parts.append("")
            d[parts[0]] = parts
    return d


def in_quotes(text, pos):
    return text[:pos].count('"') % 2 == 1


class Para:
    def __init__(self, text, toks):
        self.text = text
        self.toks = toks
        self.children = collections.defaultdict(list)
        for i, t in enumerate(toks):
            if t[7] != i:
                self.children[t[7]].append(i)

    def ch(self, i, dep=None):
        return [c for c in self.children[i] if dep is None or self.toks[c][6] == dep]

    def sent_bounds(self, i):
        s = self.toks[i][10]
        e = s
        while e + 1 < len(self.toks) and self.toks[e + 1][10] == s:
            e += 1
        return self.toks[s][0], self.toks[e][1]


def detect_grammar(p):
    """Return list of (topic, tok_start_index, tok_end_index)."""
    T = p.toks
    found = []
    low = lambda i: T[i][2].lower()

    def auxes(i):
        return [c for c in p.ch(i) if T[c][6] in ("aux", "auxpass")]

    for i, t in enumerate(T):
        start, end, text, lemma, pos, tag, dep, head = t[:8]
        lw = text.lower()
        ax = auxes(i)
        ax_lem = [T[a][3].lower() for a in ax]
        ax_tag = [T[a][5] for a in ax]
        ax_low = [low(a) for a in ax]
        span = lambda: (min([i] + ax), max([i] + ax))

        # verb to be (main verb / copula)
        if lemma == "be" and dep not in ("aux", "auxpass"):
            if tag in ("VBZ", "VBP"):
                found.append(("be_present", i, i))
            elif tag == "VBD":
                found.append(("be_past", i, i))
            if tag in ("VBZ", "VBP", "VBD"):
                ex = [c for c in p.ch(i, "expl") if low(c) == "there"]
                if ex:
                    found.append(("there_is", min(ex[0], i), max(ex[0], i)))
        # present simple
        if pos == "VERB" and tag in ("VBZ", "VBP") and dep not in ("aux", "auxpass"):
            found.append(("present_simple", i, i))
        if pos == "VERB" and tag == "VB" and any(T[a][3] == "do" and T[a][5] in ("VBZ", "VBP") for a in ax):
            found.append(("present_simple", *span()))
        # continuous forms
        if tag == "VBG" and ax:
            if "been" in ax_low and "have" in ax_lem:
                if any(T[a][5] == "VBD" and T[a][3] == "have" for a in ax):
                    found.append(("past_perfect_continuous", *span()))
                else:
                    found.append(("present_perfect_continuous", *span()))
            elif any(T[a][3] == "be" and T[a][5] in ("VBZ", "VBP") for a in ax):
                if lw == "going" and any(T[c][5] == "VB" for c in p.ch(i, "xcomp")):
                    x = [c for c in p.ch(i, "xcomp") if T[c][5] == "VB"][0]
                    found.append(("going_to", min(ax + [i]), x))
                else:
                    found.append(("present_continuous", *span()))
            elif any(T[a][3] == "be" and T[a][5] == "VBD" for a in ax):
                if lw == "going" and any(T[c][5] == "VB" for c in p.ch(i, "xcomp")):
                    x = [c for c in p.ch(i, "xcomp") if T[c][5] == "VB"][0]
                    found.append(("going_to", min(ax + [i]), x))
                else:
                    found.append(("past_continuous", *span()))
        # perfect forms
        if tag == "VBN" and "have" in ax_lem:
            has_md = any(T[a][5] == "MD" for a in ax)
            if has_md:
                md = [a for a in ax if T[a][5] == "MD"][0]
                if low(md) in ("should", "could", "might", "must", "may", "ought"):
                    found.append(("modal_perfect", *span()))
            elif any(T[a][3] == "have" and T[a][5] == "VBD" for a in ax):
                found.append(("past_perfect", *span()))
            elif any(T[a][3] == "have" and T[a][5] in ("VBZ", "VBP") for a in ax):
                found.append(("present_perfect", *span()))
        # passive
        if dep == "auxpass":
            h = head
            found.append(("passive", min(i, h), max(i, h)))
        # past simple
        if pos == "VERB" and tag == "VBD" and dep not in ("aux", "auxpass") and lemma not in ("be", "have"):
            irregular_ed = {"led", "fled", "bled", "sped", "shed", "bred", "fed", "wed"}
            if lw.endswith("ed") and lw not in irregular_ed and not lemma.endswith("eed"):
                found.append(("past_simple_regular", i, i))
            else:
                found.append(("past_simple_irregular", i, i))
        elif pos == "VERB" and tag == "VBD" and lemma == "have" and dep not in ("aux", "auxpass"):
            found.append(("past_simple_irregular", i, i))
        if lw == "used" and tag == "VBD" and i + 1 < len(T) and low(i + 1) == "to" and any(T[c][5] == "VB" for c in p.ch(i, "xcomp")):
            x = [c for c in p.ch(i, "xcomp") if T[c][5] == "VB"][0]
            found.append(("used_to", i, x))
        # modals
        if tag == "MD" and head != i:
            h = head
            hl = T[h][3]
            nxt_have = any(low(c) == "have" for c in p.children[h] if T[c][6] == "aux")
            if lw in ("can", "could", "ca") and not nxt_have:
                if hl == "be" and lw in ("ca", "could") and (low(i + 1) == "n't" if i + 1 < len(T) else False):
                    found.append(("modals_deduction", i, h))
                else:
                    found.append(("can_could", min(i, h), max(i, h)))
            elif lw in ("will", "'ll", "shall", "wo"):
                found.append(("will_future", min(i, h), max(i, h)))
            elif lw in ("should", "ought") and not nxt_have:
                found.append(("should", min(i, h), max(i, h)))
            elif lw == "must" and not nxt_have:
                subj = [c for c in p.children[h] if T[c][6] in ("nsubj", "nsubjpass")]
                if hl == "be" and not any(low(s) in ("you", "we", "i") for s in subj) and not any(T[c][6] == "auxpass" for c in p.children[h]):
                    found.append(("modals_deduction", i, h))
                else:
                    found.append(("must_have_to", min(i, h), max(i, h)))
            elif lw in ("might", "may") and hl == "be" and not nxt_have:
                found.append(("modals_deduction", i, h))
        # have to
        if lemma == "have" and dep not in ("aux", "auxpass"):
            xs = [c for c in p.ch(i, "xcomp") if T[c][5] == "VB" and any(low(a) == "to" for a in p.ch(c, "aux"))]
            if xs and xs[0] > i:
                found.append(("must_have_to", i, xs[0]))
        # comparatives / superlatives
        if tag in ("JJR", "RBR", "JJS", "RBS"):
            if lw in ("more", "most", "less", "least") and i + 1 < len(T) and T[i + 1][5] in ("JJ", "RB"):
                found.append(("comparatives", i, i + 1))
            elif lw not in ("more", "most", "less", "least", "other", "further", "later", "earlier", "rather"):
                found.append(("comparatives", i, i))
        # conditionals
        if lw == "if" and dep == "mark":
            v = head
            main = T[v][7]
            vax = auxes(v)
            mainax = [low(a) for a in auxes(main)]
            vtag = T[v][5]
            if vtag == "VBN" and any(T[a][3] == "have" and T[a][5] == "VBD" for a in vax) and "have" in mainax and any(m in mainax for m in ("would", "'d", "could", "might")):
                found.append(("third_conditional", i, v))
            elif (vtag == "VBD" or low(v) == "were") and any(m in mainax for m in ("would", "'d", "could", "might")) and "have" not in mainax:
                found.append(("second_conditional", i, v))
            elif (vtag in ("VBZ", "VBP") or any(T[a][5] in ("VBZ", "VBP") for a in vax)) and any(m in mainax for m in ("will", "'ll", "shall", "wo")):
                found.append(("first_conditional", i, v))
        # verb patterns
        if pos == "VERB" and lemma in VERB_PATTERN:
            for c in p.ch(i, "xcomp"):
                if c > i and (T[c][5] == "VBG" or (T[c][5] == "VB" and any(low(a) == "to" for a in p.ch(c, "aux")))):
                    found.append(("verb_patterns", i, c))
                    break
        # relative clauses
        if dep == "relcl":
            rel = [c for c in range(max(0, i - 6), i + 1) if T[c][5] in ("WP", "WDT", "WP$", "WRB") and T[c][2].lower() in ("who", "which", "that", "whose", "whom", "where") and (T[c][7] == i or T[T[c][7]][7] == i)]
            if rel:
                found.append(("relative_clauses", rel[0], i))
        # reported speech
        if lemma in ("say", "tell") and pos == "VERB" and not in_quotes(p.text, start):
            for c in p.ch(i, "ccomp"):
                if c > i and not in_quotes(p.text, T[c][0]):
                    marks = [m for m in p.ch(c, "mark") if low(m) == "that"]
                    if marks or T[c][5] == "VBD" or any(T[a][5] == "MD" for a in auxes(c)):
                        found.append(("reported_speech", i, c))
                        break
        # wish
        if lemma == "wish" and pos == "VERB" and p.ch(i, "ccomp"):
            c = p.ch(i, "ccomp")[0]
            found.append(("wish", i, max(i, c)))
        # imperative
        if pos == "VERB" and tag == "VB" and dep == "ROOT" and not p.ch(i, "nsubj") and not ax:
            s = T[i][10]
            first = s
            while first < len(T) and not any(ch.isalpha() for ch in T[first][2]):
                first += 1
            if first == i or (first < len(T) and low(first) in ("please", "now", "oh", "come") and first + 1 <= i <= first + 2):
                sent_end = p.sent_bounds(i)[1]
                if "?" not in p.text[T[i][0]:sent_end]:
                    found.append(("imperative", i, i))
        if lw == "do" and i + 1 < len(T) and low(i + 1) == "n't" and T[i][10] == i:
            h = head
            if T[h][5] == "VB" and not p.ch(h, "nsubj"):
                found.append(("imperative", i, h))
        # phrasal verbs
        if dep == "prt" and T[head][4] in ("VERB", "AUX"):
            key = T[head][3].lower() + " " + lw
            if key in PHRASAL:
                found.append(("phrasal_verbs", min(head, i), max(head, i)))
    return found


VERB_PATTERN = {"want", "decide", "try", "begin", "start", "hope", "need", "learn", "promise", "refuse", "agree", "plan",
                "seem", "expect", "manage", "forget", "remember", "like", "love", "hate", "prefer", "stop", "enjoy",
                "finish", "keep", "continue", "mind", "avoid", "intend", "offer", "pretend", "dare", "fail", "wish"}
PHRASAL = {}


def build(book_id, out_dir):
    global PHRASAL
    meta = BOOKS[book_id]
    data = json.load(open(os.path.join(ROOT, "build", "analysis", book_id + ".json")))
    chapters_src = data["chapters"]
    tokens = data["tokens"]

    words = load_tsv("en-pt-*.txt")
    names = load_tsv("names.txt")
    PHRASAL = load_tsv("phrasal.txt")
    exprs_src = load_tsv("expressions.txt")
    topics = json.load(open(os.path.join(ROOT, "tools", "grammar_topics.json")))

    forms = {}
    entries = {}
    form_votes = collections.defaultdict(collections.Counter)
    used_entries = collections.Counter()

    def entry_for(tok):
        text, lemma, pos = tok[2], tok[3], tok[4]
        if text.isupper() and len(text) > 1 and text.title() in names:
            text = text.title()
        if text[:1].isupper() and (text in names or (pos == "PROPN" and lemma in names)):
            nm = text if text in names else lemma
            if nm in names:
                key = "N:" + nm
                if key not in entries:
                    n = names[nm]
                    e = {"w": nm, "pt": n[1], "pos": "name"}
                    if len(n) > 2 and n[2]:
                        e["img"] = n[2]
                    entries[key] = e
                return key
        lk = lemma.lower()
        if lk in words:
            key = "W:" + lk
            if key not in entries:
                wv = words[lk]
                e = {"w": lk, "pt": wv[1], "pos": POS_LABEL.get(pos, "other")}
                if len(wv) > 2 and wv[2]:
                    e["def"] = wv[2]
                entries[key] = e
            return key
        tl = text.lower()
        for cand in (tl, tl[:-1] if tl.endswith("s") else None, tl[:-2] if tl.endswith("es") else None,
                     tl[:-3] + "y" if tl.endswith("ies") else None):
            if cand and cand in words:
                return entry_for([0, 0, cand, cand, "NOUN" if pos == "PROPN" else pos])
        if text[:1].isupper():
            key = "N:" + text
            if key not in entries:
                entries[key] = {"w": text, "pt": "nome próprio", "pos": "name"}
            return key
        return None

    chapters = []
    grammar_occ = collections.defaultdict(list)
    expr_occ = collections.defaultdict(list)
    pos_votes = collections.defaultdict(collections.Counter)

    expr_patterns = []
    for k, v in exprs_src.items():
        pat = r"(?<![A-Za-z])" + re.escape(k).replace(r"\ ", r"\s+").replace("'", "['’]") + r"(?![A-Za-z])"
        expr_patterns.append((k, re.compile(pat, re.IGNORECASE)))

    for ci, ch in enumerate(chapters_src):
        paras_out = []
        per_topic_count = collections.Counter()
        for pi, para in enumerate(ch["paragraphs"]):
            text = para["t"]
            toks = tokens[ci][pi]
            P = Para(text, toks)
            # words -> entries
            starts = {}
            for ti, t in enumerate(toks):
                for k in range(t[0], t[1]):
                    starts.setdefault(k, ti)
            for m in WORD_RE.finditer(text):
                ti = starts.get(m.start())
                if ti is None:
                    continue
                key = entry_for(toks[ti])
                if key:
                    form_votes[m.group(0)][key] += 1
                    used_entries[key] += 1
                    if toks[ti][4] in ("VERB", "AUX"):
                        pos_votes[key]["verb"] += 1
                    else:
                        pos_votes[key]["other"] += 1
            # grammar
            for topic, a, b in detect_grammar(P):
                if per_topic_count[topic] >= 4:
                    continue
                s, e = toks[a][0], toks[b][1]
                ss, se = P.sent_bounds(a)
                if any(o[0] == ci and o[1] == pi and o[2] == s for o in grammar_occ[topic][-3:]):
                    continue
                grammar_occ[topic].append([ci, pi, s, e, ss, se])
                per_topic_count[topic] += 1
            # expressions
            for k, rx in expr_patterns:
                for m in rx.finditer(text):
                    expr_occ["E:" + k].append([ci, pi, m.start(), m.end()])
            for ti, t in enumerate(toks):
                if t[6] == "prt" and toks[t[7]][4] in ("VERB", "AUX"):
                    h = t[7]
                    key = toks[h][3].lower() + " " + t[2].lower()
                    if key in PHRASAL:
                        a, b = min(h, ti), max(h, ti)
                        if b - a <= 4:
                            expr_occ["P:" + key].append([ci, pi, toks[a][0], toks[b][1]])
            paras_out.append({"t": text, "s": para["s"]})
        chapters.append({"title": ch["title"], "paragraphs": paras_out})

    # forms map: surface -> entry key (most frequent)
    for form, c in form_votes.items():
        forms[form] = c.most_common(1)[0][0]
    # vocabulary category for each entry
    for key, e in entries.items():
        if e["pos"] == "name":
            e["cat"] = "word"
        else:
            v = pos_votes[key]
            e["cat"] = "verb" if v["verb"] > v["other"] else "word"
            if e["cat"] == "verb":
                e["pos"] = "verb"
    used = {k: v for k, v in entries.items() if used_entries[k] > 0}

    expressions = []
    for key, occ in expr_occ.items():
        if key.startswith("E:"):
            src = exprs_src[key[2:]]
            typ = "expression"
        else:
            src = PHRASAL[key[2:]]
            typ = "phrasal"
        e = {"id": key, "text": src[0], "pt": src[1], "type": typ, "occ": occ[:200]}
        if src[2]:
            e["def"] = src[2]
        expressions.append(e)
    expressions.sort(key=lambda e: (e["occ"][0][0], e["occ"][0][1], e["occ"][0][2]))

    grammar = []
    tmap = {t["id"]: t for t in topics}
    for t in topics:
        occ = grammar_occ.get(t["id"], [])
        if not occ:
            continue
        g = dict(t)
        g["occ"] = occ
        grammar.append(g)

    book = {
        "format": "leitoringles-book",
        "version": 1,
        "id": book_id,
        "title": meta["title"],
        "author": meta["author"],
        "year": meta["year"],
        "level": meta["level"],
        "language": "en",
        "glossLanguage": "pt-BR",
        "description": meta["description"],
        "chapters": chapters,
        "lexicon": {"forms": forms, "entries": used},
        "expressions": expressions,
        "grammar": grammar,
    }
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, book_id + ".lib.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(book, f, ensure_ascii=False, separators=(",", ":"))
    no_pt = [e["w"] for e in used.values() if not e.get("pt")]
    print(book_id, "chapters", len(chapters), "forms", len(forms), "entries", len(used),
          "expressions", len(expressions), "grammar", {g["id"]: len(g["occ"]) for g in grammar},
          "size", os.path.getsize(path), "no_pt", no_pt[:20])
    return path


if __name__ == "__main__":
    args = sys.argv[1:]
    out = os.path.join(ROOT, "app", "src", "main", "assets", "books")
    if "--out" in args:
        i = args.index("--out")
        out = args[i + 1]
        args = args[:i] + args[i + 2:]
    for b in args:
        build(b, out)
