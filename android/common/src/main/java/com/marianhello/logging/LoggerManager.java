package com.marianhello.logging;

import org.slf4j.Marker;

import java.io.File;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.android.SQLiteAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.StatusPrinter;

public class LoggerManager {

    public static final String SQLITE_APPENDER_NAME = "sqlite";

    static {
        // reset the default context (which may already have been initialized)
        // since we want to reconfigure it
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.reset();
        // Disable class packaging data
        // @see https://github.com/tony19/logback-android/issues/171
        context.setPackagingDataEnabled(false);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%msg");
        encoder.start();

        LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(context);
        logcatAppender.setEncoder(encoder);
        logcatAppender.start();

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.TRACE);
        root.addAppender(logcatAppender);
    }

    public static void enableDBLogging() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(SQLITE_APPENDER_NAME) == null) {
            LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
            SQLiteAppender appender = new SQLiteAppender();
            appender.setName(SQLITE_APPENDER_NAME);
            appender.setMaxHistory("7 days"); //keep 7 days' worth of history
            appender.setContext(context);
            appender.start();
            root.addAppender(appender);
        }
    }

    public static void disableDBLogging() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> appender = root.getAppender(SQLITE_APPENDER_NAME);
        if (appender != null) {
            appender.stop();
            root.detachAppender(appender);
        }
    }

    public static org.slf4j.Logger getLogger(Class forClass) {
        //return org.slf4j.LoggerFactory.getLogger(forClass);

        try {
            return org.slf4j.LoggerFactory.getLogger(forClass);
        } catch (Exception e) {
            // Возвращаем заглушку вместо null
            return new org.slf4j.Logger() {
                @Override
                public String getName() {
                    return "";
                }

                @Override
                public boolean isTraceEnabled() {
                    return false;
                }

                @Override
                public void trace(String msg) {

                }

                @Override
                public void trace(String format, Object arg) {

                }

                @Override
                public void trace(String format, Object arg1, Object arg2) {

                }

                @Override
                public void trace(String format, Object... arguments) {

                }

                @Override
                public void trace(String msg, Throwable t) {

                }

                @Override
                public boolean isTraceEnabled(Marker marker) {
                    return false;
                }

                @Override
                public void trace(Marker marker, String msg) {

                }

                @Override
                public void trace(Marker marker, String format, Object arg) {

                }

                @Override
                public void trace(Marker marker, String format, Object arg1, Object arg2) {

                }

                @Override
                public void trace(Marker marker, String format, Object... argArray) {

                }

                @Override
                public void trace(Marker marker, String msg, Throwable t) {

                }

                @Override
                public boolean isDebugEnabled() {
                    return false;
                }

                @Override
                public void debug(String msg) {

                }

                @Override
                public void debug(String format, Object arg) {

                }

                @Override
                public void debug(String format, Object arg1, Object arg2) {

                }

                @Override
                public void debug(String format, Object... arguments) {

                }

                @Override
                public void debug(String msg, Throwable t) {

                }

                @Override
                public boolean isDebugEnabled(Marker marker) {
                    return false;
                }

                @Override
                public void debug(Marker marker, String msg) {

                }

                @Override
                public void debug(Marker marker, String format, Object arg) {

                }

                @Override
                public void debug(Marker marker, String format, Object arg1, Object arg2) {

                }

                @Override
                public void debug(Marker marker, String format, Object... arguments) {

                }

                @Override
                public void debug(Marker marker, String msg, Throwable t) {

                }

                @Override
                public boolean isInfoEnabled() {
                    return false;
                }

                @Override
                public void info(String msg) {

                }

                @Override
                public void info(String format, Object arg) {

                }

                @Override
                public void info(String format, Object arg1, Object arg2) {

                }

                @Override
                public void info(String format, Object... arguments) {

                }

                @Override
                public void info(String msg, Throwable t) {

                }

                @Override
                public boolean isInfoEnabled(Marker marker) {
                    return false;
                }

                @Override
                public void info(Marker marker, String msg) {

                }

                @Override
                public void info(Marker marker, String format, Object arg) {

                }

                @Override
                public void info(Marker marker, String format, Object arg1, Object arg2) {

                }

                @Override
                public void info(Marker marker, String format, Object... arguments) {

                }

                @Override
                public void info(Marker marker, String msg, Throwable t) {

                }

                @Override
                public boolean isWarnEnabled() {
                    return false;
                }

                @Override
                public void warn(String msg) {

                }

                @Override
                public void warn(String format, Object arg) {

                }

                @Override
                public void warn(String format, Object... arguments) {

                }

                @Override
                public void warn(String format, Object arg1, Object arg2) {

                }

                @Override
                public void warn(String msg, Throwable t) {

                }

                @Override
                public boolean isWarnEnabled(Marker marker) {
                    return false;
                }

                @Override
                public void warn(Marker marker, String msg) {

                }

                @Override
                public void warn(Marker marker, String format, Object arg) {

                }

                @Override
                public void warn(Marker marker, String format, Object arg1, Object arg2) {

                }

                @Override
                public void warn(Marker marker, String format, Object... arguments) {

                }

                @Override
                public void warn(Marker marker, String msg, Throwable t) {

                }

                @Override
                public boolean isErrorEnabled() {
                    return false;
                }

                @Override
                public void error(String msg) {

                }

                @Override
                public void error(String format, Object arg) {

                }

                @Override
                public void error(String format, Object arg1, Object arg2) {

                }

                @Override
                public void error(String format, Object... arguments) {

                }

                @Override
                public void error(String msg, Throwable t) {

                }

                @Override
                public boolean isErrorEnabled(Marker marker) {
                    return false;
                }

                @Override
                public void error(Marker marker, String msg) {

                }

                @Override
                public void error(Marker marker, String format, Object arg) {

                }

                @Override
                public void error(Marker marker, String format, Object arg1, Object arg2) {

                }

                @Override
                public void error(Marker marker, String format, Object... arguments) {

                }

                @Override
                public void error(Marker marker, String msg, Throwable t) {

                }
            };
        }
    }
}
