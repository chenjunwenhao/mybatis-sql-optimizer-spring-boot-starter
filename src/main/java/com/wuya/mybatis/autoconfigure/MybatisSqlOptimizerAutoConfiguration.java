package com.wuya.mybatis.autoconfigure;

import com.wuya.mybatis.optimizer.SqlAnalysisInterceptor;
import com.wuya.mybatis.optimizer.SqlAnalysisReporter;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.SqlOptimizerProperties;
import com.wuya.mybatis.optimizer.advice.*;
import com.wuya.mybatis.optimizer.analyzer.ExplainResultAnalyzer;
import com.wuya.mybatis.optimizer.analyzer.MysqlExplainResultAnalyzer;
import com.wuya.mybatis.optimizer.analyzer.OracleExplainResultAnalyzer;
import com.wuya.mybatis.optimizer.analyzer.PostgreExplainResultAnalyzer;
import com.wuya.mybatis.optimizer.report.DefaultAnalysisReporter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
@Configuration
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@AutoConfigureAfter({MybatisAutoConfiguration.class, DataSourceAutoConfiguration.class})
@EnableConfigurationProperties(SqlOptimizerProperties.class)
@ConditionalOnProperty(prefix = "mybatis.optimizer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MybatisSqlOptimizerAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.default-report", matchIfMissing = true)
    public SqlAnalysisReporter sqlAnalysisReporter() {
        return new DefaultAnalysisReporter();
    }

    @Bean
    @ConditionalOnBean(SqlSessionFactory.class)
    public SqlAnalysisInterceptor sqlAnalysisInterceptor(
            SqlOptimizerProperties properties,
            List<ExplainResultAnalyzer> analyzers,
            List<SqlOptimizationAdvice> adviceGenerators,
            DataSource dataSource,
            List<SqlAnalysisReporter> reporters) {
        return new SqlAnalysisInterceptor(properties, analyzers, adviceGenerators, dataSource, reporters);
    }


    @Bean
    @ConditionalOnBean(SqlSessionFactory.class)
    public InitializingBean forceAutoConfiguration(List<SqlSessionFactory> sqlSessionFactories,
                                                   SqlAnalysisInterceptor interceptor) {
        return () -> {
            for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
                org.apache.ibatis.session.Configuration configuration = sqlSessionFactory.getConfiguration();
                boolean alreadyAdded = configuration.getInterceptors().stream()
                        .anyMatch(existing -> existing.getClass().equals(interceptor.getClass()));
                if (!alreadyAdded) {
                    configuration.addInterceptor(interceptor);
                }
            }
        };
    }

    // 注册规则分析器
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.mysql-index", matchIfMissing = true)
    public MySqlAdviceGenerator mySqlAdviceGenerator() {
        return new MySqlAdviceGenerator();
    }
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.postgre-index", matchIfMissing = true)
    public PostgreSQLAdviceGenerator postgreSQLAdviceGenerator() {
        return new PostgreSQLAdviceGenerator();
    }
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-select", matchIfMissing = true)
    public SelectAdviceGenerator selectAdviceGenerator() {
        return new SelectAdviceGenerator();
    }
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-common", matchIfMissing = true)
    public CommonAdviceGenerator commonAdviceGenerator() {
        return new CommonAdviceGenerator();
    }

    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-where", matchIfMissing = true)
    public WhereClauseAdviceGenerator whereClauseAdviceGenerator(SqlOptimizerProperties properties) {
        return new WhereClauseAdviceGenerator(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-join", matchIfMissing = true)
    public JoinAdviceGenerator joinAdviceGenerator() {
        return new JoinAdviceGenerator();
    }

    // 注册数据库分析器
    @Bean
    @ConditionalOnClass(name = "com.mysql.jdbc.Driver")
    public MysqlExplainResultAnalyzer mysqlExplainResultAnalyzer() {
        return new MysqlExplainResultAnalyzer();
    }

    @Bean
    @ConditionalOnClass(name = "org.postgresql.Driver")
    public PostgreExplainResultAnalyzer postgreExplainResultAnalyzer() {
        return new PostgreExplainResultAnalyzer();
    }

    @Bean
    @ConditionalOnClass(name = "oracle.jdbc.OracleDriver")
    public OracleExplainResultAnalyzer oracleExplainResultAnalyzer() {
        return new OracleExplainResultAnalyzer();
    }
}