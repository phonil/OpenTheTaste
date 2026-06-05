package com.ott.api_user.config;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import jakarta.annotation.PostConstruct;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class P6SpyFormatter implements MessageFormattingStrategy {

    @PostConstruct
    public void setLogMessageFormat() {
        P6SpyOptions.getActiveInstance().setLogMessageFormat(P6SpyFormatter.class.getName());
    }

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        sql = formatSql(category, sql);
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        // [CATEGORY] | 5ms | SQL
        return String.format("[%s] | %d ms | %s", category.toUpperCase(), elapsed, sql);
    }

    private String formatSql(String category, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        if (Category.STATEMENT.getName().equals(category)) {
            String tmpsql = sql.trim().toLowerCase(Locale.ROOT);
            if (tmpsql.startsWith("create") || tmpsql.startsWith("alter") || tmpsql.startsWith("comment")) {
                return FormatStyle.DDL.getFormatter().format(sql);
            } else {
                return FormatStyle.BASIC.getFormatter().format(sql);
            }
        }
        return sql;
    }
}
