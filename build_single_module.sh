build/mvn package -DskipTests -pl graphx
cp graphx/target/spark-graphx_2.11-2.1.2.jar ~/.m2/repository/org/apache/spark/spark-graphx_2.11/2.1.2/spark-graphx_2.11-2.1.2.jar
build/mvn package -DskipTests -pl examples
