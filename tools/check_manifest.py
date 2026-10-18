import json, sys
m = json.load(open(r"Y:\Work\Natyz Embbed Gallery\GalleryNatyz\app\src\main\assets\gallery\manifest.json", encoding="utf-8"))
for g in m["galleries"]:
    types = [(i["id"], i["type"]) for i in g["items"]]
    print(g["id"], "|", g["title"], "|", g["titleZh"], "|", g["cover"], "|", types)
