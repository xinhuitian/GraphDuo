# GraphDuo

GraphDuo is a new graph processing framework on Spark based on a dual-model abstraction, which provides two efficient computing models for unidirectional and bidirectional value propagation algorithms respectively to ensure small memory footprint, computation and communication efficiency. Both computing models rely on a cheap degree-based graph partitioning scheme and a locality-aware graph layout for communication balance and fast data lookup

The source codes of GraphDuo are put in the path: graphx/src/main/scala/org/apache/spark/graphv/enhanced/
