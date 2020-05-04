
if [ -z "$TOMCAT_HOME" ]; then
    echo -e "\n\nPlease set TOMCAT8_HOME\n\n"
    exit 1
fi

mvn -U -DskipTests clean package

rm -rf $TOMCAT_HOME/webapps/spring-websocket-portfolio*

cp target/spring-websocket-portfolio.war $TOMCAT_HOME/webapps/

$TOMCAT_HOME/bin/startup.sh
