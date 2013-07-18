
if [ -z "$GLASSFISH4_HOME" ]; then
    echo -e "\n\nPlease set GLASSFISH4_HOME\n"
    echo -e "Also make sure you've called \`\$GLASSFISH4_HOME/bin/asadmin stop-domain\`\n\n"
    exit 1
fi

mvn -DskipTests clean package

$GLASSFISH4_HOME/bin/asadmin deploy --force=true target/spring-websocket-portfolio.war
