#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================================================"
echo "  Minecraft Bauplaner 3D - Web-Planer lokaler Test-Server"
echo "======================================================================"
echo ""

if command -v node &>/dev/null; then
    echo "[INFO] Starte lokalen Web-Server via Node.js auf Port 8080..."
    echo "Web-Planer aktiv unter: http://localhost:8080"
    node -e "const http=require('http'),fs=require('fs'),path=require('path');const port=8080;http.createServer((req,res)=>{let f=path.join('$SCRIPT_DIR','web',req.url.split('?')[0]);if(fs.existsSync(f)&&fs.statSync(f).isDirectory())f=path.join(f,'index.html');fs.readFile(f,(err,data)=>{if(err){res.writeHead(404);res.end('Not Found');}else{const ext=path.extname(f).slice(1);const ct={'html':'text/html; charset=utf-8','js':'application/javascript; charset=utf-8','css':'text/css; charset=utf-8','json':'application/json','schem':'application/octet-stream'}[ext]||'application/octet-stream';res.writeHead(200,{'Content-Type':ct,'Access-Control-Allow-Origin':'*'});res.end(data);}});}).listen(port,()=>{console.log('Server laeuft auf http://localhost:'+port);});"
elif command -v python3 &>/dev/null; then
    echo "[INFO] Starte lokalen Web-Server via Python auf Port 8080..."
    python3 -m http.server 8080 --directory web
elif command -v python &>/dev/null; then
    echo "[INFO] Starte lokalen Web-Server via Python auf Port 8080..."
    python -m http.server 8080 --directory web
else
    echo "ERROR: Weder Node.js noch Python gefunden. Fuehre 'npx serve web' oder 'python -m http.server 8080 --directory web' aus."
    exit 1
fi
