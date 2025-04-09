package com.wuya.mybatis.exception;
/**
 * SqlOptimizerException
 * @author wuya
 * Created by chenjunwen on 2018/1/25.
 */
public class SqlOptimizerException extends RuntimeException{
    public SqlOptimizerException(String message) {
        super(message);
    }

    public SqlOptimizerException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqlOptimizerException(Exception e) {
        super(e);
    }
}
