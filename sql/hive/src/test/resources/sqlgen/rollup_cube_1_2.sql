-- This file is automatically generated by LogicalPlanToSQLSuite.
SELECT count(*) as cnt, key%5, grouping_id() FROM parquet_t1 GROUP BY key % 5 WITH CUBE
--------------------------------------------------------------------------------
SELECT `gen_attr_2` AS `cnt`, `gen_attr_3` AS `(key % CAST(5 AS BIGINT))`, `gen_attr_4` AS `grouping_id()` FROM (SELECT count(1) AS `gen_attr_2`, (`gen_attr_5` % CAST(5 AS BIGINT)) AS `gen_attr_3`, grouping_id() AS `gen_attr_4` FROM (SELECT `key` AS `gen_attr_5`, `value` AS `gen_attr_6` FROM `default`.`parquet_t1`) AS gen_subquery_0 GROUP BY (`gen_attr_5` % CAST(5 AS BIGINT)) GROUPING SETS(((`gen_attr_5` % CAST(5 AS BIGINT))), ())) AS gen_subquery_1
