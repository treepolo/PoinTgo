@ECHO OFF
SETLOCAL

SET APP_HOME=%~dp0
IF "%APP_HOME%"=="" SET APP_HOME=.
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

IF EXIST "%JAVA_HOME%\bin\java.exe" (
    SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
    SET JAVA_EXE=java.exe
)

"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL
