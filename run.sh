#!/usr/bin/env bash
set -e

echo "========================================================"
echo "  Starting CRMS 2.0 - Criminal Case Management System   "
echo "========================================================"

mkdir -p out

echo "Compiling Java source files..."
javac -encoding UTF-8 -cp "lib/sqlite-jdbc.jar:." -d out *.java model/*.java repository/*.java service/*.java util/*.java menu/*.java

echo ""
echo "Launching SecureWebServer on http://localhost:8081 ..."
echo "Default Login:"
echo "  Username: admin"
echo "  Password: ChangeMe!2026"
echo ""

java -cp "out:lib/sqlite-jdbc.jar" SecureWebServer
