
# Change the line below to the location of Tomcat built from trunk
TOMCAT=~/dev/sources/apache/tomcat/trunk/output/build

echo "Shutting down Tomcat"
$TOMCAT/bin/shutdown.sh

mvn -DskipTests clean package

echo "Copying war to $TOMCAT/webapps/"
rm -rf $TOMCAT/webapps/spring-websocket-portfolio*
cp target/spring-websocket-portfolio.war $TOMCAT/webapps/

echo "Starting Tomcat"
$TOMCAT/bin/startup.sh
