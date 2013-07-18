
if [ -z "$TOMCAT8_HOME" ]; then
    echo -e "\n\nPlease set TOMCAT8_HOME\n\n"
    exit 1
fi

mvn -DskipTests clean package

rm -rf $TOMCAT8_HOME/webapps/spring-websocket-portfolio*

cp target/spring-websocket-portfolio.war $TOMCAT8_HOME/webapps/

$TOMCAT8_HOME/bin/startup.sh
