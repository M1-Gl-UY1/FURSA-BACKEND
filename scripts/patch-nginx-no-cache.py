#!/usr/bin/env python3
"""
V2 HH (09/06/2026) : ajoute les headers no-cache sur index.html dans
les configs nginx des 2 sites front (fursa + admin).

Sans ca, les users en prod gardent l'ancien bundle JS en cache navigateur
apres un deploy, jusqu'a ce qu'ils fassent un hard refresh manuel.

Idempotent : si la marque "V2 HH" est deja presente, on ne touche rien.
"""

import re
import sys
from pathlib import Path

INSERT_BLOCK = '''    # V2 HH (09/06/2026) : no-cache sur index.html pour eviter cache navigateur
    # qui pointe vers anciens bundles assets-XXX.js apres deploy.
    # Les fichiers /assets/ ont des hash dans le nom donc cache long OK.
    location = /index.html {
        expires -1;
        add_header Cache-Control "no-store, no-cache, must-revalidate" always;
    }

'''

SITES = [
    "/etc/nginx/sites-enabled/fursa.seed-innov.com",
    "/etc/nginx/sites-enabled/admin.fursa.seed-innov.com",
]

for site in SITES:
    p = Path(site)
    if not p.exists():
        print(f"{site} : not found, skip")
        continue
    content = p.read_text()
    if "V2 HH" in content:
        print(f"{site} : already patched, skip")
        continue
    # Match le bloc "location / { ... try_files ..." du SPA fallback
    new_content, n = re.subn(
        r"(    # SPA fallback[^\n]*\n)?    location / \{\n        try_files",
        INSERT_BLOCK + "    location / {\n        try_files",
        content,
        count=1,
    )
    if n == 0:
        print(f"{site} : pattern not found, MANUAL CHECK NEEDED")
        sys.exit(1)
    p.write_text(new_content)
    print(f"{site} : patched")

print("DONE. Run: sudo nginx -t && sudo systemctl reload nginx")
