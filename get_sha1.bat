@echo off
chcp 65001 >nul
cd /d "%~dp0"
"C:\Users\30742\.jdks\openjdk-25.0.1\bin\keytool.exe" -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
pause
