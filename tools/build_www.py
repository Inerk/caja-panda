"""Genera app/src/main/assets/www/index.html a partir de web/caja.html (la misma Caja, conectada a Firebase)."""
import json, pathlib, shutil
root = pathlib.Path(__file__).resolve().parent.parent
src = (root / "web" / "caja.html").read_text(encoding="utf-8")
cfg = json.loads((root / "web" / "firebase-config.json").read_text(encoding="utf-8"))
cut = src.index("</style>") + len("</style>")
head_part, body_part = src[:cut], src[cut:]
head = f"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#0b2f7a">
<style>:root{{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}}body{{margin:0;font:14px system-ui,sans-serif}}img{{max-width:100%}}[hidden]{{display:none!important}}</style>
<script src="lib/qrcode.js"></script>
<script src="lib/firebase-app-compat.js"></script>
<script src="lib/firebase-auth-compat.js"></script>
<script src="lib/firebase-firestore-compat.js"></script>
<script>window.FIREBASE_CONFIG={json.dumps(cfg)};</script>
{head_part}
</head>
<body>
"""
out = head + body_part.strip() + "\n</body>\n</html>\n"
dst = root / "app" / "src" / "main" / "assets" / "www" / "index.html"
dst.write_text(out, encoding="utf-8")
img = dst.parent / "img"
shutil.rmtree(img, ignore_errors=True)
shutil.copytree(root / "web" / "img", img)
print("Generado", dst.relative_to(root), len(out), "bytes")
