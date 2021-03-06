/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.carbondata.spark.testsuite.standardpartition

import org.apache.spark.sql.execution.BatchedDataSourceScanExec
import org.apache.spark.sql.test.util.QueryTest
import org.apache.spark.sql.{DataFrame, Row}
import org.scalatest.BeforeAndAfterAll

import org.apache.carbondata.core.constants.CarbonCommonConstants
import org.apache.carbondata.core.util.CarbonProperties
import org.apache.carbondata.spark.rdd.CarbonScanRDD

class StandardPartitionTableQueryTestCase extends QueryTest with BeforeAndAfterAll {

  override def beforeAll {
    dropTable

    CarbonProperties.getInstance()
      .addProperty(CarbonCommonConstants.CARBON_TIMESTAMP_FORMAT, "dd-MM-yyyy")
    CarbonProperties.getInstance()
      .addProperty(CarbonCommonConstants.CARBON_DATE_FORMAT, "dd-MM-yyyy")
    sql(
      """
        | CREATE TABLE originTable (empno int, empname String, designation String, doj Timestamp,
        |  workgroupcategory int, workgroupcategoryname String, deptno int, deptname String,
        |  projectcode int, projectjoindate Timestamp, projectenddate Date,attendance int,
        |  utilization int,salary int)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)

    sql(s"""LOAD DATA local inpath '$resourcesPath/data.csv' INTO TABLE originTable OPTIONS('DELIMITER'= ',', 'QUOTECHAR'= '"')""")

  }

  test("querying on partition table for int partition column") {
    sql(
      """
        | CREATE TABLE partitionone (empname String, designation String, doj Timestamp,
        |  workgroupcategory int, workgroupcategoryname String, deptno int, deptname String,
        |  projectcode int, projectjoindate Timestamp, projectenddate Date,attendance int,
        |  utilization int,salary int)
        | PARTITIONED BY (empno int)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)
    sql(s"""LOAD DATA local inpath '$resourcesPath/data.csv' INTO TABLE partitionone OPTIONS('DELIMITER'= ',', 'QUOTECHAR'= '"')""")

    val frame = sql(
      "select empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno," +
      " deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary " +
      "from partitionone where empno=11 order by empno")
    verifyPartitionInfo(frame, Seq("empno=11"))

    checkAnswer(frame,
      sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from originTable where empno=11 order by empno"))

  }

  test("querying on partition table for string partition column") {
    sql(
      """
        | CREATE TABLE partitiontwo (empno int, empname String, designation String, doj Timestamp,
        |  workgroupcategory int, workgroupcategoryname String, deptno int,
        |  projectcode int, projectjoindate Timestamp, projectenddate Date,attendance int,
        |  utilization int,salary int)
        | PARTITIONED BY (deptname String)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)
    sql(s"""LOAD DATA local inpath '$resourcesPath/data.csv' INTO TABLE partitiontwo OPTIONS('DELIMITER'= ',', 'QUOTECHAR'= '"')""")

    val frame = sql(
      "select empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno," +
      " deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary " +
      "from partitiontwo where deptname='network' and projectcode=928478")
    verifyPartitionInfo(frame, Seq("deptname=network"))

    val frame1 = sql(
      "select empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno," +
      " deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary " +
      "from partitiontwo where projectcode=928478")
    checkAnswer(frame1,
      sql( "select empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno," +
           " deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary " +
           "from originTable where projectcode=928478"))
    verifyPartitionInfo(frame1, Seq("deptname=network","deptname=security","deptname=protocol","deptname=Learning","deptname=configManagement"))

    val frame2 = sql("select distinct deptname from partitiontwo")

    verifyPartitionInfo(frame2, Seq("deptname=network","deptname=security","deptname=protocol","deptname=Learning","deptname=configManagement"))

    checkAnswer(frame,
      sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from originTable where where deptname='network' and projectcode=928478 order by empno"))

  }

  test("querying on partition table for more partition columns") {
    sql(
      """
        | CREATE TABLE partitionmany (empno int, empname String, designation String,
        |  workgroupcategory int, workgroupcategoryname String, deptno int,
        |  projectjoindate Timestamp, projectenddate Date,attendance int,
        |  utilization int,salary int)
        | PARTITIONED BY (deptname String,doj Timestamp,projectcode int)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)
    sql(s"""LOAD DATA local inpath '$resourcesPath/data.csv' INTO TABLE partitionmany OPTIONS('DELIMITER'= ',', 'QUOTECHAR'= '"')""")

    val frame = sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from partitionmany where doj='2007-01-17 00:00:00'")
    verifyPartitionInfo(frame, Seq("deptname=network","doj=2007-01-17 00:00:00","projectcode=928478"))
    checkAnswer(frame,
      sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from originTable where doj='2007-01-17 00:00:00'"))

  }

  test("querying on partition table for date partition column") {
    sql(
      """
        | CREATE TABLE partitiondate (empno int, empname String, designation String,
        |  workgroupcategory int, workgroupcategoryname String, deptno int,
        |  projectjoindate Timestamp,attendance int,
        |  deptname String,doj Timestamp,projectcode int,
        |  utilization int,salary int)
        | PARTITIONED BY (projectenddate Date)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)
    sql(s"""LOAD DATA local inpath '$resourcesPath/data.csv' INTO TABLE partitiondate OPTIONS('DELIMITER'= ',', 'QUOTECHAR'= '"')""")
    val frame = sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from partitiondate where projectenddate = cast('2016-11-30' as date)")
    verifyPartitionInfo(frame, Seq("projectenddate=2016-11-30"))
    checkAnswer(frame,
      sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from originTable where projectenddate = cast('2016-11-30' as date)"))

  }

  test("querying on partition table for date partition column on insert query") {
    sql(
      """
        | CREATE TABLE partitiondateinsert (empno int, empname String, designation String,
        |  workgroupcategory int, workgroupcategoryname String, deptno int,
        |  projectjoindate Timestamp,attendance int,
        |  deptname String,projectcode int,
        |  utilization int,salary int)
        | PARTITIONED BY (projectenddate Date,doj Timestamp)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)
    sql(s"""insert into partitiondateinsert select empno, empname,designation,workgroupcategory,workgroupcategoryname,deptno,projectjoindate,attendance,deptname,projectcode,utilization,salary,projectenddate,doj from originTable""")
    val frame = sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from partitiondateinsert where projectenddate = cast('2016-11-30' as date)")
    verifyPartitionInfo(frame, Seq("projectenddate=2016-11-30","doj=2015-12-01 00:00:00"))
    checkAnswer(frame,
      sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from originTable where projectenddate = cast('2016-11-30' as date)"))

    val frame1 = sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from partitiondateinsert where doj>'2006-01-17 00:00:00'")
    verifyPartitionInfo(frame1,
      Seq("projectenddate=2016-06-29" ,
          "doj=2010-12-29 00:00:00"   ,
          "doj=2015-12-01 00:00:00"   ,
          "projectenddate=2016-11-12" ,
          "projectenddate=2016-12-29" ,
          "doj=2011-11-09 00:00:00"   ,
          "doj=2009-07-07 00:00:00"   ,
          "projectenddate=2016-05-29" ,
          "doj=2012-10-14 00:00:00"   ,
          "projectenddate=2016-11-30" ,
           "projectenddate=2016-11-15",
           "doj=2015-05-12 00:00:00"  ,
           "doj=2013-09-22 00:00:00"  ,
           "doj=2008-05-29 00:00:00"  ,
           "doj=2014-08-15 00:00:00",
           "projectenddate=2016-12-30"))
    checkAnswer(frame1,
      sql("select  empno, empname, designation, doj, workgroupcategory, workgroupcategoryname, deptno, deptname, projectcode, projectjoindate, projectenddate, attendance, utilization, salary from originTable where doj>cast('2006-01-17 00:00:00' as Timestamp)"))

  }

  test("badrecords on partition column") {
    sql("create table badrecordsPartition(intField1 int, stringField1 string) partitioned by (intField2 int) stored by 'carbondata'")
    sql(s"load data local inpath '$resourcesPath/data_partition_badrecords.csv' into table badrecordsPartition options('bad_records_action'='force')")
    sql("select count(*) from badrecordsPartition").show()
    checkAnswer(sql("select count(*) cnt from badrecordsPartition where intfield2 is null"), Seq(Row(9)))
    checkAnswer(sql("select count(*) cnt from badrecordsPartition where intfield2 is not null"), Seq(Row(2)))
  }

  test("badrecords fail on partition column") {
    sql("create table badrecordsPartitionfail(intField1 int, stringField1 string) partitioned by (intField2 int) stored by 'carbondata'")
    intercept[Exception] {
      sql(s"load data local inpath '$resourcesPath/data_partition_badrecords.csv' into table badrecordsPartitionfail options('bad_records_action'='fail')")

    }
  }

  test("badrecords ignore on partition column") {
    sql("create table badrecordsPartitionignore(intField1 int, stringField1 string) partitioned by (intField2 int) stored by 'carbondata'")
    sql("create table badrecordsignore(intField1 int,intField2 int, stringField1 string) stored by 'carbondata'")
    sql(s"load data local inpath '$resourcesPath/data_partition_badrecords.csv' into table badrecordsPartitionignore options('bad_records_action'='ignore')")
    sql(s"load data local inpath '$resourcesPath/data_partition_badrecords.csv' into table badrecordsignore options('bad_records_action'='ignore')")
    checkAnswer(sql("select count(*) cnt from badrecordsPartitionignore where intfield2 is null"), sql("select count(*) cnt from badrecordsignore where intfield2 is null"))
    checkAnswer(sql("select count(*) cnt from badrecordsPartitionignore where intfield2 is not null"), sql("select count(*) cnt from badrecordsignore where intfield2 is not null"))
  }


  test("test partition fails on int null partition") {
    sql("create table badrecordsPartitionintnull(intField1 int, stringField1 string) partitioned by (intField2 int) stored by 'carbondata'")
    sql(s"load data local inpath '$resourcesPath/data_partition_badrecords.csv' into table badrecordsPartitionintnull options('bad_records_action'='force')")
    checkAnswer(sql("select count(*) cnt from badrecordsPartitionintnull where intfield2 = 13"), Seq(Row(1)))
  }

  test("test partition fails on int null partition read alternate") {
    CarbonProperties.getInstance().addProperty(CarbonCommonConstants.CARBON_READ_PARTITION_HIVE_DIRECT, "false")
    sql("create table badrecordsPartitionintnullalt(intField1 int, stringField1 string) partitioned by (intField2 int) stored by 'carbondata'")
    sql(s"load data local inpath '$resourcesPath/data_partition_badrecords.csv' into table badrecordsPartitionintnullalt options('bad_records_action'='force')")
    checkAnswer(sql("select count(*) cnt from badrecordsPartitionintnullalt where intfield2 = 13"), Seq(Row(1)))
    CarbonProperties.getInstance().addProperty(CarbonCommonConstants.CARBON_READ_PARTITION_HIVE_DIRECT, CarbonCommonConstants.CARBON_READ_PARTITION_HIVE_DIRECT_DEFAULT)
  }

  test("static column partition with load command") {
    sql(
      """
        | CREATE TABLE staticpartitionload (empno int, designation String,
        |  workgroupcategory int, workgroupcategoryname String, deptno int,
        |  projectjoindate Timestamp,attendance int,
        |  deptname String,projectcode int,
        |  utilization int,salary int,projectenddate Date,doj Timestamp)
        | PARTITIONED BY (empname String)
        | STORED BY 'org.apache.carbondata.format'
      """.stripMargin)
    sql(s"""LOAD DATA local inpath '$resourcesPath/data.csv' INTO TABLE staticpartitionload partition(empname='ravi') OPTIONS('DELIMITER'= ',', 'QUOTECHAR'= '"')""")
  }


  private def verifyPartitionInfo(frame: DataFrame, partitionNames: Seq[String]) = {
    val plan = frame.queryExecution.sparkPlan
    val scanRDD = plan collect {
      case b: BatchedDataSourceScanExec if b.rdd.isInstanceOf[CarbonScanRDD] => b.rdd
        .asInstanceOf[CarbonScanRDD]
    }
    assert(scanRDD.nonEmpty)
    assert(!partitionNames.map(f => scanRDD.head.partitionNames.exists(_.equals(f))).exists(!_))
  }

  override def afterAll = {
    dropTable
  }

  def dropTable = {
    sql("drop table if exists originTable")
    sql("drop table if exists originMultiLoads")
    sql("drop table if exists partitionone")
    sql("drop table if exists partitiontwo")
    sql("drop table if exists partitionmany")
    sql("drop table if exists partitiondate")
    sql("drop table if exists partitiondateinsert")
    sql("drop table if exists badrecordsPartition")
    sql("drop table if exists staticpartitionload")
    sql("drop table if exists badrecordsPartitionignore")
    sql("drop table if exists badrecordsPartitionfail")
    sql("drop table if exists badrecordsignore")
    sql("drop table if exists badrecordsPartitionintnull")
    sql("drop table if exists badrecordsPartitionintnullalt")
  }

}
