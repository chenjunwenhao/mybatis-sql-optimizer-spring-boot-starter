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
    <version>1.2.11</version><!-- 最新版本 -->
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
### 5. 对接大模型进行AI分析
由于提供了自定义规则扩展点，目前`mybatis-sql-optimizer-spring-boot-starter`就不对大模型进行集成了，如果有大模型的条件可以自己自定义拓展，可以参考 `DeepSeekAdvice` 实现。
#### 大模型拓展点效果
通过自定义规则拓展点，实现 `SqlOptimizationAdvice` 接口创建自定义规则，可以对接大模型，如：ChatGPT、LLM、DeepSeek等,调用大模型进行SQL优化分析, 从而实现对SQL的优化。我自己使用了DeepSeek的API进行了测试，效果不错。
先看效果
```java
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -===== SQL分析报告 [MySQL:com.faq.mapper.DictDao.getCity] =====
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -SQL: SELECT
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
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -执行时间: 310ms
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -执行计划:
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  filtered: 100.0
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  Extra: Using where
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  select_type: SIMPLE
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  id: 1
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  type: ALL
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  rows: 3408
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  table: think_areas
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -优化建议:
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 检测到全表扫描，建议为表 think_areas 添加索引
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 索引选择性不足，索引 null 过滤了100.0%数据，建议优化索引或查询条件
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - LIKE条件以通配符开头，无法使用索引
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 警告: 对列 `AREA_NAME` 使用函数 `UPPER()`，可能导致索引失效。白名单函数: [ABS, FLOOR, COALESCE, CEILING, ROUND, NULLIF]
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 警告: 对列 `AREA_NAME` 使用函数 `UPPER()`，可能导致索引失效。白名单函数: [ABS, FLOOR, COALESCE, CEILING, ROUND, NULLIF]
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - 全表扫描JOIN操作检测到，考虑添加适当的索引
2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - [ai] 原SQL分析结果: # SQL 优化分析

## 当前SQL存在的问题

1. **索引失效问题**：
   - `like '%1%'` 和 `like '%2%'` 使用了前导通配符，导致无法使用索引
   - `upper()` 函数的使用也会导致索引失效

2. **逻辑错误**：
   - WHERE条件中的逻辑运算符优先级问题，当前写法等同于：
     ```sql
     (think_areas.parent_id = ? and think_areas.area_type like '%1%')
     or upper(think_areas.area_name) like '%2%'
     or Upper(think_areas.area_name) like '%2%'
     ```
   - 最后一个条件与倒数第二个条件重复

3. **性能问题**：
   - 全表扫描不可避免
   - 重复条件计算

## 优化建议

### 1. 修正逻辑错误
        
SELECT
    think_areas.area_id as id,
    think_areas.parent_id as parentId,
    think_areas.area_name as label,
    think_areas.area_type as type
FROM
    think_areas
WHERE
    think_areas.parent_id = ?
    AND (think_areas.area_type like '%1%'
        OR upper(think_areas.area_name) like '%2%')

### 2. 更好的优化方案

如果业务允许，尽量避免使用前导通配符：

SELECT
    think_areas.area_id as id,
    think_areas.parent_id as parentId,
    think_areas.area_name as label,
    think_areas.area_type as type
FROM
    think_areas
WHERE
    think_areas.parent_id = ?
    AND (think_areas.area_type like '1%'  -- 去掉前导通配符
        OR think_areas.area_name like '2%')  -- 去掉UPPER函数和前导通配符


### 3. 索引建议

如果这是高频查询，建议添加以下索引：

CREATE INDEX idx_parent_id ON think_areas(parent_id);
CREATE INDEX idx_area_type ON think_areas(area_type);
CREATE INDEX idx_area_name ON think_areas(area_name);


### 4. 其他建议

1. 如果数据量大且查询频繁，考虑使用全文索引
2. 考虑将大小写敏感的需求移到应用层处理
3. 如果`area_type`有固定值，使用`=`代替`like`

## 最终优化SQL

SELECT
    area_id as id,
    parent_id as parentId,
    area_name as label,
    area_type as type
FROM
    think_areas
WHERE
    parent_id = ?
    AND (area_type like '1%'
        OR area_name like '2%')


这个优化版本：
1. 移除了重复条件
2. 修正了逻辑运算符优先级
3. 简化了表名前缀
4. 尽可能避免前导通配符
5. 移除了不必要的UPPER函数
   2025-04-12 20:44:09 [pool-2-thread-1] INFO  com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter -  - [ai] 执行计划分析结果: # SQL 分析报告

## 当前SQL执行情况分析

从提供的执行计划信息来看，这个SQL查询存在明显的性能问题：

1. **访问类型(type)**: `ALL` - 表示进行了全表扫描，这是最差的一种访问方式
2. **扫描行数(rows)**: 3408 - 需要扫描整个表的3408行数据
3. **过滤条件(filtered)**: 100% - 没有有效利用索引进行过滤
4. **额外信息(Extra)**: `Using where` - 表示在存储引擎检索行后进行了额外的过滤

## 优化建议

### 1. 添加适当的索引

这是最关键的优化点。根据查询条件，为`think_areas`表添加合适的索引：
        
-- 假设查询中有WHERE条件字段为area_name
ALTER TABLE think_areas ADD INDEX idx_area_name(area_name);

-- 如果是多条件查询，考虑复合索引
ALTER TABLE think_areas ADD INDEX idx_multiple(column1, column2);


### 2. 检查查询条件

确保WHERE条件使用了索引列，避免在索引列上使用函数或计算：

-- 不好的写法(无法使用索引)
SELECT * FROM think_areas WHERE YEAR(create_time) = 2023;

-- 好的写法
SELECT * FROM think_areas WHERE create_time BETWEEN '2023-01-01' AND '2023-12-31';


### 3. 限制返回的列

避免使用`SELECT *`，只查询需要的列：

-- 替代
SELECT id, area_name FROM think_areas WHERE ...;

### 4. 考虑表分区

如果表数据量很大(远大于3408行)，可以考虑按某些条件进行分区。

### 5. 检查表结构

确保表有合适的主键，字段类型选择合理，避免使用过大的字段类型。

## 实施建议

1. 首先分析实际查询语句(当前只提供了执行计划，缺少SQL文本)
2. 根据实际查询条件创建针对性索引
3. 使用EXPLAIN验证优化效果
4. 考虑在测试环境验证后再应用到生产环境

需要更具体的优化建议，请提供完整的SQL查询语句和表结构信息。
```
#### 伪代码示例
下面是一个AIAdvice的伪代码示例，DeepSeekClient是通过HTTP调用的DeepSeek的API，它实现了SqlOptimizationAdvice接口，用于生成SQL优化建议。
```java
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DeepSeekAdvice implements SqlOptimizationAdvice {

    @Autowired
    private DeepSeekClient deepseekClient;
    /**
     * 生成优化建议
     * @param explainResult
     * @return
     */
    @Override
    public List<String> generateAdvice(SqlExplainResult explainResult) {
        // 自定义分析逻辑
        List<String> adviceList = new ArrayList();
        // 原sql
        String sql = explainResult.getSql();
        // 执行计划结果
        List<Map<String, Object>> explainResults = explainResult.getExplainResults();
        // 模型分析原sql; 伪代码DeepSeek API
        String sqlAIAdvice = deepseekClient.analysis(sql);
        // 模型分析执行计划
        String sqlExplainAIAdvice = deepseekClient.analysis(explainResults);
        adviceList.add("[ai] 原SQL分析结果:" + sqlAIAdvice);
        adviceList.add("[ai] 执行计划分析结果:" + sqlExplainAIAdvice);
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
        return true;
    }
}
```
#### DeepSeek的模型选择对比和输出markdown解决方案

---
**模型选择对比**

|模型名称|适用场景|输出特点|
|-|-|-|
|`deepseek-chat`|通用对话场景|倾向于自然语言+Markdown|
|`deepseek-coder`|代码生成/分析/优化|结构化代码+技术术语|
|`deepseek-math`|数学/逻辑分析|公式/符号化表达|


---

 **性能对比测试数据**

| 模型            | 响应时间(avg) | 技术术语准确率 | 格式合规性 |
|----------------|--------------|---------------|-----------|
| deepseek-chat   | 1.2s         | 78%           | 需后处理   |
| deepseek-coder  | 0.9s         | 95%           | 直接可用   |

---
 **输出markdown解决方案**

 如果模型的输出分析过于冗余,可以通过提示词控制格式；或者自定义提示词控制指定半结构化格式，例如：csv、json等，下面示例代码：
```java
List<Map<String, String>> messages = new ArrayList<>();
messages.add(Map.of(
    "role", "system",
    "content": "你是一个SQL优化专家，请按以下格式响应："
              + "1. 问题描述（纯文本）"
              + "2. 优化建议（无Markdown）"
        // +4.要求内容精简，避免输出过多无用信息
        // +4.要求输出Markdown格式，便于前端展示
        // +4.要求输出json格式，便于输入到ES进行分析查询，具体json格式为..."
              + "3. 示例代码（如果适用）"
));
messages.add(Map.of(
    "role", "user",
    "content", "分析SQL: " + sql
));
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