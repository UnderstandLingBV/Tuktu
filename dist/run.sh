if type -p java; then
    _java=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then   
    _java="$JAVA_HOME/bin/java"
else
    echo "Please install Java 8 before continuing."
fi

if [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')    
    if [[ "$version" > "1.8" ]]; then
        bin/tuktu
    else         
        echo Please upgrade your Java version to atleast 8
    fi
fi