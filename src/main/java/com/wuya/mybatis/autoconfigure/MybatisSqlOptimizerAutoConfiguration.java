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

/**
 * MyBatis SQL优化自动配置类
 * 该配置类负责根据条件注册SQL分析拦截器、报告器、建议生成器和解释结果分析器
 * @author chenjunwen
 * @date 2020-04-09 09:08
 */
@Configuration
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@AutoConfigureAfter({MybatisAutoConfiguration.class, DataSourceAutoConfiguration.class})
@EnableConfigurationProperties(SqlOptimizerProperties.class)
@ConditionalOnProperty(prefix = "mybatis.optimizer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MybatisSqlOptimizerAutoConfiguration {

    /**
     * 注册默认的SQL分析报告器
     * 
     * @return 默认的SQL分析报告器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.default-report", matchIfMissing = true)
    public SqlAnalysisReporter sqlAnalysisReporter() {
        return new DefaultAnalysisReporter();
    }

    /**
     * 注册SQL分析拦截器
     * 
     * @param properties SQL优化属性
     * @param analyzers 解释结果分析器列表
     * @param adviceGenerators SQL优化建议生成器列表
     * @param dataSource 数据源
     * @param reporters SQL分析报告器列表
     * @return SQL分析拦截器实例
     */
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

    /**
     * 强制自动配置，将SQL分析拦截器添加到所有SqlSessionFactory中
     * 
     * @param sqlSessionFactories SqlSessionFactory列表
     * @param interceptor SQL分析拦截器
     * @return 初始化Bean用于添加拦截器
     */
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

    /**
     * 注册MySQL建议生成器
     * 
     * @return MySQL建议生成器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.mysql-index", matchIfMissing = true)
    public MySqlAdviceGenerator mySqlAdviceGenerator() {
        return new MySqlAdviceGenerator();
    }
    
    /**
     * 注册PostgreSQL建议生成器
     * 
     * @return PostgreSQL建议生成器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.postgre-index", matchIfMissing = true)
    public PostgreSQLAdviceGenerator postgreSQLAdviceGenerator() {
        return new PostgreSQLAdviceGenerator();
    }
    
    /**
     * 注册SELECT语句分析建议生成器
     * 
     * @return SELECT语句分析建议生成器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-select", matchIfMissing = true)
    public SelectAdviceGenerator selectAdviceGenerator() {
        return new SelectAdviceGenerator();
    }
    
    /**
     * 注册通用SQL分析建议生成器
     * 
     * @return 通用SQL分析建议生成器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-common", matchIfMissing = true)
    public CommonAdviceGenerator commonAdviceGenerator() {
        return new CommonAdviceGenerator();
    }
    
    /**
     * 注册WHERE子句分析建议生成器
     * 
     * @param properties SQL优化属性
     * @return WHERE子句分析建议生成器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-where", matchIfMissing = true)
    public WhereClauseAdviceGenerator whereClauseAdviceGenerator(SqlOptimizerProperties properties) {
        return new WhereClauseAdviceGenerator(properties);
    }
    
    /**
     * 注册JOIN子句分析建议生成器
     * 
     * @return JOIN子句分析建议生成器实例
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.optimizer.analyze-join", matchIfMissing = true)
    public JoinAdviceGenerator joinAdviceGenerator() {
        return new JoinAdviceGenerator();
    }

    /**
     * 注册MySQL解释结果分析器
     * 
     * @return MySQL解释结果分析器实例
     */
    @Bean
    @ConditionalOnClass(name = "com.mysql.jdbc.Driver")
    public MysqlExplainResultAnalyzer mysqlExplainResultAnalyzer() {
        return new MysqlExplainResultAnalyzer();
    }
    
    /**
     * 注册PostgreSQL解释结果分析器
     * 
     * @return PostgreSQL解释结果分析器实例
     */
    @Bean
    @ConditionalOnClass(name = "org.postgresql.Driver")
    public PostgreExplainResultAnalyzer postgreExplainResultAnalyzer() {
        return new PostgreExplainResultAnalyzer();
    }
    
    /**
     * 注册Oracle解释结果分析器
     * 
     * @return Oracle解释结果分析器实例
     */
    @Bean
    @ConditionalOnClass(name = "oracle.jdbc.OracleDriver")
    public OracleExplainResultAnalyzer oracleExplainResultAnalyzer() {
        return new OracleExplainResultAnalyzer();
    }
}
