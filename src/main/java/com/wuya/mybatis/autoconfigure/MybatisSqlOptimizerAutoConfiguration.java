package com.wuya.mybatis.autoconfigure;

import com.wuya.mybatis.optimizer.SqlAnalysisInterceptor;
import com.wuya.mybatis.optimizer.SqlOptimizationAdvice;
import com.wuya.mybatis.optimizer.SqlOptimizerProperties;
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

import java.util.List;
@Configuration
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@AutoConfigureAfter({MybatisAutoConfiguration.class, DataSourceAutoConfiguration.class})
@EnableConfigurationProperties(SqlOptimizerProperties.class)
@ConditionalOnProperty(prefix = "mybatis.optimizer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MybatisSqlOptimizerAutoConfiguration {

    @Bean
    @ConditionalOnBean(SqlSessionFactory.class)
    public SqlAnalysisInterceptor sqlAnalysisInterceptor(SqlOptimizerProperties properties,
                                                         List<SqlOptimizationAdvice> adviceGenerators) {
        return new SqlAnalysisInterceptor(properties, adviceGenerators);
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
}