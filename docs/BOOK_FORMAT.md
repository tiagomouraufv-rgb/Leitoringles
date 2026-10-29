# Formato de livro do Leitor Inglês (versão 1)

Cada livro é um único arquivo JSON em UTF-8, com extensão `.lib.json` (pode ser comprimido com gzip).

```json
{
  "format": "leitoringles-book",
  "version": 1,
  "id": "moby-dick",
  "title": "Moby Dick",
  "author": "Herman Melville",
  "year": 1851,
  "level": "B1",
  "language": "en",
  "glossLanguage": "pt-BR",
  "description": "One or two sentences in English.",
  "chapters": [ { "title": "Part 1 — ...", "paragraphs": [ { "t": "Text.", "s": "p" } ] } ],
  "lexicon": { "forms": { "wolves": "W:wolf" }, "entries": { "W:wolf": { "w": "wolf", "pt": "lobo", "def": "...", "pos": "noun", "cat": "word" } } },
  "expressions": [ { "id": "E:at once", "text": "at once", "pt": "imediatamente", "def": "immediately", "type": "expression", "occ": [[0, 3, 15, 22]] } ],
  "grammar": [ { "id": "past_perfect", "order": 20, "level": "B1", "title": "Past perfect", "titlePt": "...", "form": "...", "explanation": "...", "occ": [[0, 2, 10, 17, 0, 45]] } ]
}
```

## Campos

- `level`: A1, A2, B1, B2, C1 ou C2 — define a prateleira.
- `chapters[].paragraphs[].s`: `p` normal, `h` subtítulo, `q` citação/carta, `li` item de lista.
- `chapters[].paragraphs[].tr` (opcional, recomendado): `[[início, fim, "tradução"], ...]`, uma entrada por frase, posições dentro de `t`. Usado ao selecionar um trecho e no pop-up da palavra. No pipeline deste repositório, as traduções ficam em `books-src/<livro>/pt/NN.txt` (um bloco por parágrafo separado por linha em branco, uma linha por frase) e são alinhadas por `tools/sentences.py`.
- `lexicon.forms`: cada palavra do texto como aparece (sensível a maiúsculas) → chave de entrada.
- `lexicon.entries`: chaves `W:<lema>` para palavras e `N:<Nome>` para nomes próprios.
  `pt` tradução, `def` definição simples em inglês (opcional), `pos` classe gramatical,
  `cat` = `verb` ou `word` (separa Verbos de Palavras na aba Vocabulário), `img` termo de busca da imagem (opcional).
- `expressions[].occ` e `grammar[].occ`: `[capítulo, parágrafo, início, fim]` (+ `início da frase, fim da frase` na gramática).
  Posições em caracteres (UTF-16) dentro do texto do parágrafo; o fim não é incluído.
- `grammar[].id`: um dos ids do programa (veja `SYLLABUS` em `app/src/main/java/.../data/Prompt.kt`).
  O app mostra os tópicos nessa ordem de aprendizagem, independentemente da ordem em que aparecem no livro.

## Como gerar com as ferramentas do repositório

```
pip install spacy
pip install https://github.com/explosion/spacy-models/releases/download/en_core_web_sm-3.8.0/en_core_web_sm-3.8.0-py3-none-any.whl
python3 tools/analyze.py <id>
python3 tools/missing_words.py <id>      # completar tools/lexicon/*.txt
python3 tools/build_book.py <id> --out build/books
```

Os textos-fonte ficam em `books-src/<id>/*.md`; as explicações de gramática em `tools/grammar_topics.json`.
