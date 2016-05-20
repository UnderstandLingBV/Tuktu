@echo off
PATH %PATH%;%JAVA_HOME%\bin\
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j%%k%%l%%m"

if %jver% LSS 18000 goto nojava
bin\tuktu
exit


:nojava
echo Please update Java to atleast Java 8
exit
