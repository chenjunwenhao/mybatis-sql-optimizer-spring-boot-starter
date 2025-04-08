# mybatis-sql-optimizer-spring-boot-starter

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![License](https://img.shields.io/badge/license-apache2.0-blue)

专为 MyBatis 打造的 SQL 智能优化解决方案，无需修改业务代码即可获得专业级 SQL 优化建议，让慢查询无所遁形！
---

## 🌟 核心特性

### 一键式 SQL 优化

- **智能分析引擎**：自动识别`SELECT *`、`执行计划分析`、`索引失效`等 20+ 常见问题
- **执行计划洞察**：集成 PostgreSQL/MySQL`EXPLAIN`可视化分析
- **动态缓存建议**：基于访问模式推荐二级缓存最佳配置

### 开发者友好

- **零侵入接入**：Spring Boot Starter 开箱即用
- **多维度报告**：控制台/邮件/钉钉多维告警（支持自定义阈值）

一个基于 MyBatis 插件和 JSqlParser 解析器的 SQL 分析优化 Starter，提供 SQL 性能分析、优化建议、多数据库兼容支持，并支持同步/异步分析模式，采样率，自定义报告输出形式和自定义分析规则。

- ✅ **SQL 性能分析** - 自动分析执行的 SQL 语句
- ✅ **优化建议** - 提供索引、改写等优化建议
- ✅ **多数据库兼容** - 支持 MySQL、Oracle、PostgreSQL 等主流数据库
- ✅ **灵活的分析模式** - 支持同步和异步分析模式
- ✅ **自定义规则** - 可扩展的分析规则配置
- ✅ **自定义报告输出规则** - 可扩展的报告输出。可以发送MQ，输出到日志、邮件、监控系统等
- ✅ **采样率控制** - 避免分析带来的性能开销
- ✅ **轻量无侵入** - 简单配置即可接入现有项目

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.chenjunwenhao</groupId>
    <artifactId>mybatis-sql-optimizer-spring-boot-starter</artifactId>
    <version>1.2.10</version><!-- 最新版本 -->
</dependency>
```

### 2. 基础配置

```yaml
mybatis:
  optimizer:
    enabled: true # 启用分析器 默认开启
    explainAll: true # 是否分析所有SQL 默认 true
    thresholdMillis: 100 # 当explainAll：false时 执行时间超过阈值的才会分析 默认100ms
    async-analysis: true # 使用异步模式 默认同步
    sample-rate: 1 # 采样率(0-1) 默认1
    async-threads: 2  #异步线程数，默认2
    async-queueSize: 1000 #异步队列大小，默认1000
    analyze-join: true # 允许分析 JOIN 默认true
    analyze-select: true # 允许分析SELECT子句 默认true
    analyze-common: true # 允许分析通用（SQL执行时间超过5秒） 默认true
    analyze-where: true # 允许分析WHERE子句 默认true
    analyze-limit: true # 允许分析LIMIT子句 默认true
    mysql-index: true # 是否分析mysql索引 默认true
    postgre-index: true # 是否分析postgre索引 默认true
    where-function-allowed: # 列函数白名单。默认("ABS", "ROUND", "FLOOR", "CEILING", "COALESCE", "NULLIF")
      - "ROUND"
      - "ABS"
    cache:
      enabled: true  # 是否启用缓存 默认true
      spec: "maximumSize=1000,expireAfterWrite=1h,recordStats" # Caffeine原生配置
      # 或分项配置：
      # max-size: 1000
      # expire-time: 1h
      # record-stats: true
```

### 3. 高级配置
#### 自定义分析规则

实现 `SqlOptimizationAdvice` 接口创建自定义规则：

```java
@Component
public class CustomAdvice implements SqlOptimizationAdvice {

    /**
     * 生成优化建议
     * @param explainResult
     * @return
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        // 自定义分析逻辑
        List<String> adviceList = new ArrayList();
        String sql = explainResult.getSql();
        if (sql.contains("SELECT *")) {
            adviceList.add("避免使用SELECT *");
        }
        return adviceList;
    }

    /**
     * 是否支持该数据库类型
     * @param dbType
     * @return
     */
    @Override
    public boolean supports(DatabaseType dbType) {
        // 只支持PostgreSQL
        // 如果规则不区分数据库，直接return true;
        return dbType.equals(DatabaseType.POSTGRE);
    }
}
```

#### 报告处理

实现 `SqlAnalysisReporter` 接口自定义报告处理：

```java
@Component
public class CustomReporter implements SqlAnalysisReporter {
    
    @Override
    public void report(SqlExplainResult result, DatabaseType dbType, String id) {
        // 发送到日志/邮件/监控系统等
        result.getAdviceList().forEach(result -> {
            log.info("SQL分析结果: {}", result);
        });
    }
}
```

###  4. 输出样例
```java
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -===== SQL分析报告 [MySQL:com.faq.mapper.DictDao.getCity] =====
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -SQL: SELECT
        think_areas.area_id as id,
        think_areas.parent_id as parentId,
        think_areas.area_name as label,
        think_areas.area_type as type
        FROM
        think_areas
        WHERE
        think_areas.parent_id = ?
        and think_areas.area_type like '%1%'
        or upper(think_areas.area_name) like '%2%'
        or Upper(think_areas.area_name) like '%2%'
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -执行时间: 625ms
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -执行计划:
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  filtered: 100.0
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  Extra: Using where
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  select_type: SIMPLE
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  id: 1
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  type: ALL
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  rows: 3408
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  table: think_areas
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -优化建议:
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 检测到全表扫描，建议为表 think_areas 添加索引
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 索引选择性不足，索引 null 过滤了100.0%数据，建议优化索引或查询条件
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - LIKE条件以通配符开头，无法使用索引
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 警告: 对列 `AREA_NAME` 使用函数 `UPPER()`，可能导致索引失效。白名单函数: [ABS, FLOOR, COALESCE, CEILING, ROUND, NULLIF]
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 警告: 对列 `AREA_NAME` 使用函数 `UPPER()`，可能导致索引失效。白名单函数: [ABS, FLOOR, COALESCE, CEILING, ROUND, NULLIF]
2025-04-04 19:53:59 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 全表扫描JOIN操作检测到，考虑添加适当的索
```

## 功能详解

### 分析模式

- **同步模式**：立即分析并返回结果，适合开发环境
- **异步模式**：后台线程池处理，不影响主流程，适合生产环境

### 采样率控制

通过 `sample-rate` 配置采样比例，避免高频 SQL 带来的性能开销：

```yaml
mybatis:
  optimizer:
    sample-rate: 0.3 # 只分析30%的SQL
```


## 最佳实践

1. **开发环境**：使用同步模式，采样率设为1.0，快速发现问题
2. **测试环境**：使用异步模式，采样率0.5-0.8，平衡性能和分析覆盖率
3. **生产环境**：使用异步模式，采样率0.1-0.3，最小化性能影响

## 注意事项

1. 异步模式下，分析结果可能有延迟
2. 高采样率可能影响系统性能
3. 部分复杂SQL可能无法准确分析

## 参与贡献

欢迎提交 Issue 和 PR，贡献你的想法和代码。

## 许可证

MIT License
## 欢迎交流加微信
wx:xiao_6488
或扫描下方二维码
![8db788e702d354185dec9a25037b722](https://github.com/user-attachments/assets/52cfda16-9454-4f98-b3cf-de2941eb6161)
![8db788e702d354185dec9a25037b722](https://gitee.com/cjw1/huashiren/raw/master/8db788e702d354185dec9a25037b722.jpg)