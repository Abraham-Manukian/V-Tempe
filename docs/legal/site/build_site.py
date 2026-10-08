"""Builds the static legal site from the Markdown sources in docs/legal/.

The Markdown files are the single source of truth; the HTML pages next to this script are
generated. Re-run after every edit:

    python docs/legal/site/build_site.py

Standard library only. Supports the Markdown subset the legal documents use: headings,
paragraphs, bullet and numbered lists, tables, block quotes, horizontal rules, **bold** and
`code`. Output pages have no external scripts, fonts or trackers.
"""
from __future__ import annotations

import html
import re
from pathlib import Path

LEGAL_DIR = Path(__file__).resolve().parent.parent
SITE_DIR = Path(__file__).resolve().parent

# (source markdown, output page, language, page title)
PAGES = [
    ("privacy-policy.md", "index.html", "ru", "Политика обработки персональных данных — V-Tempe"),
    ("terms-of-use.md", "terms.html", "ru", "Пользовательское соглашение — V-Tempe"),
    ("health-data-consent.md", "health-data-consent.html", "ru", "Согласие на обработку данных о здоровье — V-Tempe"),
    ("delete-account.md", "delete-account.html", "ru", "Удаление аккаунта — V-Tempe"),
    ("privacy-policy.en.md", "en/index.html", "en", "Privacy Policy — V-Tempe"),
    ("terms-of-use.en.md", "en/terms.html", "en", "Terms of Use — V-Tempe"),
    ("health-data-consent.en.md", "en/health-data-consent.html", "en", "Health Data Consent — V-Tempe"),
]

NAV = {
    "ru": [("index.html", "Политика"), ("terms.html", "Соглашение"),
           ("health-data-consent.html", "Согласие"), ("delete-account.html", "Удаление аккаунта"),
           ("en/index.html", "English")],
    "en": [("index.html", "Privacy"), ("terms.html", "Terms"),
           ("health-data-consent.html", "Consent"), ("../delete-account.html", "Delete account"),
           ("../index.html", "Русский")],
}

STYLE = """
:root{color-scheme:light dark;--bg:#fff;--fg:#1a1a1a;--muted:#5c5c66;--line:#e2e2ea;--accent:#5b3fd1;--note:#f4f1ff}
@media (prefers-color-scheme:dark){:root{--bg:#121216;--fg:#ececf1;--muted:#a0a0ad;--line:#2c2c36;--accent:#b4a4ff;--note:#1e1a2e}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif}
main{max-width:760px;margin:0 auto;padding:16px 16px 48px}
nav{display:flex;flex-wrap:wrap;gap:8px 16px;padding:12px 0;border-bottom:1px solid var(--line);margin-bottom:8px}
nav a{color:var(--accent);text-decoration:none;font-weight:600}
h1{font-size:1.6rem;line-height:1.3}h2{font-size:1.25rem;margin-top:2rem}
a{color:var(--accent);overflow-wrap:anywhere}
blockquote{margin:16px 0;padding:8px 16px;background:var(--note);border-left:4px solid var(--accent);color:var(--muted)}
.table{overflow-x:auto;margin:16px 0}
table{border-collapse:collapse;width:100%;font-size:.95rem}
th,td{border:1px solid var(--line);padding:8px;text-align:left;vertical-align:top}
code{font-size:.9em}
hr{border:0;border-top:1px solid var(--line);margin:32px 0}
"""


def inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    return re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)


def table(rows: list[str]) -> str:
    cells = [[c.strip() for c in r.strip().strip("|").split("|")] for r in rows]
    head, body = cells[0], [r for r in cells[2:]]
    out = ["<div class=\"table\"><table><thead><tr>"]
    out += [f"<th>{inline(c)}</th>" for c in head]
    out.append("</tr></thead><tbody>")
    for r in body:
        out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def convert(markdown: str) -> str:
    out: list[str] = []
    lines = markdown.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
        elif line.startswith("#"):
            level = len(line) - len(line.lstrip("#"))
            out.append(f"<h{level}>{inline(line[level:].strip())}</h{level}>")
            i += 1
        elif line.strip() == "---":
            out.append("<hr>")
            i += 1
        elif line.startswith("|"):
            block = []
            while i < len(lines) and lines[i].startswith("|"):
                block.append(lines[i]); i += 1
            out.append(table(block))
        elif line.startswith(">"):
            block = []
            while i < len(lines) and lines[i].startswith(">"):
                block.append(lines[i][1:].strip()); i += 1
            out.append(f"<blockquote><p>{inline(' '.join(block))}</p></blockquote>")
        elif re.match(r"^(- |\d+\. )", line):
            ordered = not line.startswith("- ")
            items: list[str] = []
            while i < len(lines) and (re.match(r"^(- |\d+\. )", lines[i]) or lines[i].startswith("  ")):
                if lines[i].startswith("  ") and items:
                    items[-1] += " " + lines[i].strip()
                else:
                    items.append(re.sub(r"^(- |\d+\. )", "", lines[i]))
                i += 1
            tag = "ol" if ordered else "ul"
            out.append(f"<{tag}>" + "".join(f"<li>{inline(t)}</li>" for t in items) + f"</{tag}>")
        else:
            block = []
            while i < len(lines) and lines[i].strip() and not re.match(r"^(#|\||>|- |\d+\. |---$)", lines[i]):
                block.append(lines[i].strip()); i += 1
            out.append(f"<p>{inline(' '.join(block))}</p>")
    return "\n".join(out)


def page(lang: str, title: str, body: str) -> str:
    nav = "".join(f"<a href=\"{href}\">{html.escape(label)}</a>" for href, label in NAV[lang])
    return (
        f"<!doctype html>\n<html lang=\"{lang}\">\n<head>\n<meta charset=\"utf-8\">\n"
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
        f"<title>{html.escape(title)}</title>\n<style>{STYLE}</style>\n</head>\n"
        f"<body>\n<main>\n<nav>{nav}</nav>\n{body}\n</main>\n</body>\n</html>\n"
    )


def main() -> None:
    for source, target, lang, title in PAGES:
        markdown = (LEGAL_DIR / source).read_text(encoding="utf-8")
        output = SITE_DIR / target
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(page(lang, title, convert(markdown)), encoding="utf-8", newline="\n")
        print(f"{source} -> {output.relative_to(SITE_DIR)}")


if __name__ == "__main__":
    main()
