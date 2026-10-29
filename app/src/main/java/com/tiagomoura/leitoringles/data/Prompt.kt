package com.tiagomoura.leitoringles.data

/** Order in which grammar topics are shown, from the easiest to the hardest. */
val SYLLABUS = listOf(
    "be_present", "present_simple", "there_is", "present_continuous", "imperative", "can_could",
    "be_past", "past_simple_regular", "past_simple_irregular", "past_continuous", "going_to", "will_future",
    "comparatives", "present_perfect", "must_have_to", "should", "first_conditional", "verb_patterns",
    "passive", "past_perfect", "relative_clauses", "reported_speech", "second_conditional", "used_to",
    "present_perfect_continuous", "past_perfect_continuous", "modals_deduction", "third_conditional",
    "wish", "modal_perfect", "phrasal_verbs",
)

object Prompt {
    fun build(title: String, author: String, level: String): String {
        val who = if (author.isBlank()) "" else " de $author"
        return """
Crie uma versão graded reader, em inglês nível $level, do livro "$title"$who, para o meu app Leitor Inglês (repositório GitHub tiagomouraufv-rgb/Leitoringles).

REGRAS DO TEXTO
- Se o livro ainda estiver protegido por direitos autorais, me avise e não escreva a adaptação.
- Conte a história completa, com todos os acontecimentos importantes, em inglês nível $level (frases curtas, vocabulário do nível, sem copiar o texto original).
- Divida em partes de 900 a 1.400 palavras, cada uma com um título. A primeira parte é "About this edition", com uma apresentação curta e a lista dos personagens principais.

SE VOCÊ TIVER ACESSO AO REPOSITÓRIO (Claude Code ou conversa com o GitHub conectado)
1. Escreva o texto em books-src/<id>/NN.md (00-intro.md, 01.md, 02.md...). Use "# Título" no primeiro arquivo, "## Título da parte" para cada parte, "**Texto**" sozinho num parágrafo para subtítulos, "> " para citações e "- " para listas.
2. Acrescente o livro ao dicionário BOOKS em tools/build_book.py (título, autor, ano, nível "$level", descrição curta em inglês).
3. Rode: python3 tools/analyze.py <id>  (precisa de spaCy e en_core_web_sm).
4. Rode o script que lista as palavras sem tradução e acrescente as traduções em tools/lexicon/en-pt-*.txt (formato: lema|tradução em português|definição simples em inglês) e os nomes próprios em tools/lexicon/names.txt (Nome|descrição em português|termo de busca de imagem na Wikipédia).
5. Rode: python3 tools/build_book.py <id> --out build/books
6. Me entregue o arquivo build/books/<id>.lib.json para eu importar no app (não coloque em app/src/main/assets, a menos que eu peça).

SE VOCÊ NÃO TIVER ACESSO AO REPOSITÓRIO
Gere um único arquivo JSON chamado <id>.lib.json, em UTF-8, exatamente neste formato (versão 1):

{
  "format": "leitoringles-book",
  "version": 1,
  "id": "identificador-curto-sem-espacos",
  "title": "$title",
  "author": "${author.ifBlank { "Autor" }}",
  "year": 1900,
  "level": "$level",
  "language": "en",
  "glossLanguage": "pt-BR",
  "description": "Uma ou duas frases em inglês sobre o livro.",
  "chapters": [
    {"title": "Part 1 — ...", "paragraphs": [{"t": "First sentence. Second one.", "s": "p", "tr": [[0, 15, "Primeira frase."], [16, 27, "Segunda."]]}]}
  ],
  "lexicon": {
    "forms": {"wolves": "W:wolf", "Ahab": "N:Ahab"},
    "entries": {
      "W:wolf": {"w": "wolf", "pt": "lobo", "def": "a wild animal like a large dog", "pos": "noun", "cat": "word"},
      "W:give": {"w": "give", "pt": "dar", "pos": "verb", "cat": "verb"},
      "N:Ahab": {"w": "Ahab", "pt": "Ahab — capitão do navio", "pos": "name", "cat": "word", "img": "termo de busca na Wikipédia"}
    }
  },
  "expressions": [
    {"id": "E:at once", "text": "at once", "pt": "imediatamente", "def": "immediately", "type": "expression",
     "occ": [[0, 3, 15, 22]]}
  ],
  "grammar": [
    {"id": "past_simple_regular", "order": 8, "level": "A2", "title": "Past simple (regular verbs)",
     "titlePt": "Passado simples (verbos regulares)", "form": "verbo + -ed ...",
     "explanation": "Explicação em português, clara e curta.",
     "occ": [[0, 2, 10, 17, 0, 45]]}
  ]
}

Detalhes obrigatórios:
- "s" do parágrafo: "p" (normal), "h" (subtítulo), "q" (citação/carta) ou "li" (item de lista).
- "tr" do parágrafo (obrigatório): a tradução para português do Brasil de CADA frase do parágrafo, na ordem, como [início, fim, "tradução"]; início/fim são posições de caracteres da frase dentro de "t" (fim exclusivo). As frases devem cobrir o parágrafo inteiro, sem sobreposição. Títulos e itens de lista também levam "tr" (uma frase). A tradução é natural e fiel, não palavra por palavra.
- "forms": TODAS as palavras que aparecem no texto, como estão escritas (incluindo plurais, verbos conjugados e palavras com maiúscula no início de frase), apontando para uma entrada de "entries". Chaves das entradas: "W:" + lema em minúsculas, ou "N:" + nome próprio.
- "entries": uma por lema, com "pt" (tradução para o português do Brasil, com os sentidos usados no livro), "def" (definição simples em inglês, opcional para palavras muito básicas), "pos" (noun, verb, adj, adv, name, pron, prep, conj, det, interj) e "cat" ("verb" para verbos, "word" para o resto). Use "img" em nomes de pessoas reais, lugares, navios e objetos concretos, com o termo exato de busca na Wikipédia em inglês.
- "occ" das expressões: [capítulo, parágrafo, início, fim], com posições em caracteres dentro do texto do parágrafo (contando a partir de 0; o fim não é incluído). Inclua phrasal verbs ("type": "phrasal") e expressões fixas ("type": "expression").
- "grammar": use estes ids, nesta ordem de aprendizagem: ${SYLLABUS.joinToString(", ")}. "occ" = [capítulo, parágrafo, início, fim, início da frase, fim da frase]. Marque até 4 exemplos por tópico em cada capítulo, apenas exemplos corretos. Inclua somente os tópicos que aparecem no livro.
- Confira as posições: o trecho texto[início:fim] precisa ser exatamente a palavra ou expressão marcada.
- Se o arquivo ficar grande demais para uma resposta, crie-o com as suas ferramentas de arquivo e me entregue o arquivo para download.
""".trimIndent()
    }
}
