@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
@rem Check for Java 25 first
if exist "%LOCALAPPDATA%\jdk-25\jdk-25.0.4+7\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\jdk-25\jdk-25.0.4+7"
    set "JAVA_EXE=%LOCALAPPDATA%\jdk-25\jdk-25.0.4+7\bin\java.exe"
    goto execute
)

@rem Resolve Java
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo FEHLER: JAVA_HOME ist nicht gesetzt und 'java' wurde nicht im PATH gefunden.
echo Bitte installiere Java 25 und setze die Umgebungsvariable JAVA_HOME.
echo.
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo FEHLER: JAVA_HOME ist ungueltig: %JAVA_HOME%
echo Bitte pruefe den Pfad zu deiner Java 25 Installation.
echo.
exit /b 1

:execute
@rem Auto-download wrapper jar if missing
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" goto runGradle

echo Lade gradle-wrapper.jar herunter...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar', '%WRAPPER_JAR%')"

:runGradle
@rem Setup classpath and execute Gradle
set CLASSPATH=%WRAPPER_JAR%

"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
exit /b %ERRORLEVEL%

:mainEnd
if "%OS%"=="Windows_NT" endlocal
