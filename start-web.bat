@echo off
setlocal

echo ======================================================================
echo   Minecraft Bauplaner 3D - Web-Planer lokaler Test-Server
echo ======================================================================
echo.

cd /d "%~dp0"

where node >NUL 2>&1
if %ERRORLEVEL% equ 0 (
    echo [INFO] Starte lokalen Web-Server via Node.js auf Port 8080...
    echo [INFO] Oeffne http://localhost:8080 im Standard-Browser...
    node -e "const http=require('http'),fs=require('fs'),path=require('path');const port=8080;http.createServer((req,res)=>{let f=path.join(__dirname,'web',req.url.split('?')[0]);if(fs.existsSync(f)&&fs.statSync(f).isDirectory())f=path.join(f,'index.html');fs.readFile(f,(err,data)=>{if(err){res.writeHead(404);res.end('Not Found');}else{const ext=path.extname(f).slice(1);const ct={'html':'text/html; charset=utf-8','js':'application/javascript; charset=utf-8','css':'text/css; charset=utf-8','json':'application/json','schem':'application/octet-stream'}[ext]||'application/octet-stream';res.writeHead(200,{'Content-Type':ct,'Access-Control-Allow-Origin':'*'});res.end(data);}});}).listen(port,()=>{console.log('Web-Planer aktiv: http://localhost:'+port);try{require('child_process').exec('start http://localhost:'+port);}catch(e){}});"
    goto end
)

where python >NUL 2>&1
if %ERRORLEVEL% equ 0 (
    echo [INFO] Starte lokalen Web-Server via Python auf Port 8080...
    start http://localhost:8080
    python -m http.server 8080 --directory web
    goto end
)

echo [FEHLER] Weder Node.js noch Python wurden im PATH gefunden.
echo Du kannst einen der folgenden Befehle manuell ausfuehren:
echo   - npx serve web
echo   - python -m http.server 8080 --directory web
pause

:end
