"""Print ingredient values and every dish's numbers so they can be reviewed by hand.

Usage: python3 audit.py ingredients | dishes [category]
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))


def ingredients():
    v = json.load(open(os.path.join(HERE, "ingredient_values.json"), encoding="utf-8"))
    for k, x in v.items():
        note = ("* " + x["note"][:40]) if x["note"] else ""
        print(f'{k:12} {x["kcal"]:5.0f} P{x["p"]:5.1f} C{x["c"]:5.1f} F{x["f"]:5.1f} | {x["usda_name"][:46]:46} {note}')


def dishes(cat=None):
    src = open(os.path.join(HERE, "..", "foods.js"), encoding="utf-8").read()
    rows = [json.loads(l.strip().rstrip(",")) for l in src.splitlines() if l.strip().startswith("[")]
    for r in rows:
        name, _, serving, kcal, p, c, f, category, _recipe = r
        if cat and category != cat:
            continue
        m = re.search(r"(\d+)\s*(g|ml)\b", serving)
        dens = f"{kcal / int(m.group(1)) * 100:4.0f}/100g" if m else "         "
        fat_pct = f * 9 / kcal * 100 if kcal else 0
        print(f"{name[:34]:34} {serving[:24]:24} {kcal:5d} P{p:5.1f} C{c:6.1f} F{f:5.1f} fat{fat_pct:3.0f}% {dens}")


if __name__ == "__main__":
    if sys.argv[1] == "ingredients":
        ingredients()
    else:
        dishes(sys.argv[2] if len(sys.argv) > 2 else None)
