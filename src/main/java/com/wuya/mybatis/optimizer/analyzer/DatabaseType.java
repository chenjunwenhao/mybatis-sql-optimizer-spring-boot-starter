package com.wuya.mybatis.optimizer.analyzer;

/**
 * 数据库类型枚举
 * @author chenjunwen
 * @date 2020-07-01
 */
public enum DatabaseType {
    MYSQL("MySQL"),
    POSTGRE("PostgreSQL"),
    ORACLE("Oracle"),
    UNKNOWN("Unknown");

    private final String name;

    DatabaseType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static DatabaseType fromUrl(String url) {
        if (url == null) {
            return UNKNOWN;
        }
        if (url.contains(":mysql:")) {
            return MYSQL;
        } else if (url.contains(":postgresql:")) {
            return POSTGRE;
        } else if (url.contains(":oracle:")) {
            return ORACLE;
        }
        return UNKNOWN;
    }
}